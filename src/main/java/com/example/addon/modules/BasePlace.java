package com.example.addon.modules;

import com.example.addon.Dune;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class BasePlace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum range to look for enemy players.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Maximum range to place support blocks.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Delay in ticks between placements.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> minTargetDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-target-damage")
        .description("Minimum target damage the support setup should enable.")
        .defaultValue(4)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximum self damage allowed for the enabled crystal position.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("Prevents placements that could kill you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate toward support blocks while placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Render a hand swing on placement.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Pauses while using an item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnMine = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-mine")
        .description("Pauses while mining blocks.")
        .defaultValue(false)
        .build()
    );

    private final BlockPos.Mutable bestPos = new BlockPos.Mutable();
    private int placeTimer;

    public BasePlace() {
        super(Dune.Main, "Base Place", "Places obsidian support blocks for crystal spots.");
    }

    @Override
    public void onActivate() {
        placeTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (placeTimer > 0) {
            placeTimer--;
            return;
        }

        if (shouldPause()) return;

        FindItemResult obsidian = meteordevelopment.meteorclient.utils.player.InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        PlayerEntity target = findNearestTarget();
        if (target == null) return;

        if (!findBestSupportPos(target)) return;

        boolean placed = BlockUtils.place(bestPos, obsidian, rotate.get(), 50, swing.get(), true, false);
        if (placed) placeTimer = placeDelay.get();
    }

    private boolean shouldPause() {
        if (mc.player == null) return true;
        if (pauseOnUse.get() && (mc.player.isUsingItem() || mc.options.useKey.isPressed())) return true;
        return pauseOnMine.get() && mc.interactionManager != null && mc.interactionManager.isBreakingBlock();
    }

    private PlayerEntity findNearestTarget() {
        if (mc.player == null || mc.world == null) return null;

        PlayerEntity nearest = null;
        double bestDistance = Double.MAX_VALUE;
        double maxSq = targetRange.get() * targetRange.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!player.isAlive() || player.isSpectator()) continue;
            if (!Friends.get().shouldAttack(player)) continue;

            double distance = player.squaredDistanceTo(mc.player);
            if (distance > maxSq || distance >= bestDistance) continue;

            nearest = player;
            bestDistance = distance;
        }

        return nearest;
    }

    private boolean findBestSupportPos(PlayerEntity target) {
        if (mc.player == null || mc.world == null) return false;

        double bestScore = 0;
        boolean found = false;

        int radius = (int) Math.ceil(placeRange.get());
        BlockPos playerPos = mc.player.getBlockPos();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    pos.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);

                    if (!isValidSupportPos(pos, target)) continue;

                    Vec3d crystalPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                    float targetDamage = DamageUtils.crystalDamage(target, crystalPos, false, pos);
                    float selfDamage = DamageUtils.crystalDamage(mc.player, crystalPos, false, pos);

                    if (targetDamage < minTargetDamage.get()) continue;
                    if (selfDamage > maxSelfDamage.get()) continue;
                    if (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player)) continue;

                    double score = targetDamage - selfDamage * 0.35;
                    if (!found || score > bestScore) {
                        bestScore = score;
                        bestPos.set(pos);
                        found = true;
                    }
                }
            }
        }

        return found;
    }

    private boolean isValidSupportPos(BlockPos pos, PlayerEntity target) {
        if (mc.player == null || mc.world == null) return false;
        if (!mc.world.isInBuildLimit(pos) || !mc.world.isInBuildLimit(pos.up(2))) return false;

        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!mc.world.getBlockState(pos.up()).isAir()) return false;
        if (!mc.world.getBlockState(pos.up(2)).isAir()) return false;

        Vec3d placePos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double rangeSq = placeRange.get() * placeRange.get();
        if (mc.player.squaredDistanceTo(placePos) > rangeSq) return false;

        // Keep support close to the chosen enemy so the enabled crystal spot is meaningful.
        if (target.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5) > targetRange.get() * targetRange.get()) return false;

        Box crystalBox = new Box(
            pos.getX(), pos.getY() + 1, pos.getZ(),
            pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1
        );

        return !EntityUtils.intersectsWithEntity(crystalBox, entity -> !entity.isSpectator());
    }
}

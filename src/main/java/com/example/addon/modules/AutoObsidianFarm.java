package com.example.addon.modules;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.example.addon.Dune;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.Comparator;

public class AutoObsidianFarm extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> itemSearchRange = sgGeneral.add(new IntSetting.Builder()
        .name("item-search-range")
        .description("Range (in blocks) used to search for dropped items in the Nether.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 16)
        .max(64)
        .build()
    );

    private final Setting<BlockPos> deathPosition = sgGeneral.add(new BlockPosSetting.Builder()
        .name("death-position")
        .description("Position to walk to before using the damage command.")
        .defaultValue(new BlockPos(0, 64, 0))
        .build()
    );

    private final Setting<Integer> portalSearchRange = sgGeneral.add(new IntSetting.Builder()
        .name("portal-search-range")
        .description("Range (in blocks) used to find a Nether portal in the Overworld.")
        .defaultValue(6)
        .min(1)
        .sliderRange(1, 16)
        .max(64)
        .build()
    );

    private final Setting<Integer> repathDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("repath-delay-ticks")
        .description("Ticks to wait before reissuing the same Baritone goal.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 40)
        .max(200)
        .build()
    );

    private final Setting<Integer> damageDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("damage-delay-ticks")
        .description("Ticks to wait between repeated damage command attempts.")
        .defaultValue(40)
        .min(5)
        .sliderRange(5, 100)
        .max(400)
        .build()
    );

    private State state;
    private BlockPos activeGoal;
    private int repathTimer;
    private int damageTimer;
    private int portalWarnTimer;

    public AutoObsidianFarm() {
        super(Dune.Main, "auto-obsidian-farm", "Collects Nether drops, suicides at a set spot, then re-enters the Nether through a nearby portal.");
    }

    @Override
    public void onActivate() {
        if (!BaritoneUtils.IS_AVAILABLE) {
            error("Baritone is not available. Install/enable Baritone integration first.");
            toggle();
            return;
        }

        activeGoal = null;
        repathTimer = 0;
        damageTimer = 0;
        portalWarnTimer = 0;
        state = State.IDLE;
    }

    @Override
    public void onDeactivate() {
        PathManagers.get().stop();
        activeGoal = null;
        state = State.IDLE;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (damageTimer > 0) damageTimer--;
        if (portalWarnTimer > 0) portalWarnTimer--;

        if (mc.world.getRegistryKey() == World.NETHER) {
            handleNetherLoop();
            return;
        }

        if (mc.world.getRegistryKey() == World.OVERWORLD) {
            handleOverworldLoop();
            return;
        }

        clearGoal(true);
        state = State.IDLE;
    }

    private void handleNetherLoop() {
        if (mc.player == null || mc.world == null) return;

        state = State.NETHER_COLLECTING;

        ItemEntity targetItem = findClosestItem(itemSearchRange.get());
        if (targetItem != null) {
            requestMove(targetItem.getBlockPos());
            return;
        }

        state = State.NETHER_RETURN_AND_DIE;
        BlockPos targetDeathPos = deathPosition.get();
        if (!isNear(targetDeathPos, 2.25)) {
            requestMove(targetDeathPos);
            return;
        }

        clearGoal(true);

        if (damageTimer > 0) return;

        // Meteor commands are dispatched without the prefix character.
        try {
            Commands.dispatch("damage 7");
            damageTimer = damageDelayTicks.get();
            state = State.WAITING_RESPAWN;
        } catch (CommandSyntaxException ignored) {
            error("Failed to run damage command.");
            damageTimer = damageDelayTicks.get();
        }
    }

    private void handleOverworldLoop() {
        if (mc.player == null || mc.world == null) return;

        if (!mc.player.isAlive()) {
            state = State.WAITING_RESPAWN;
            clearGoal(true);
            return;
        }

        state = State.OVERWORLD_RETURN_TO_PORTAL;

        BlockPos portal = findClosestPortal(portalSearchRange.get());
        if (portal == null) {
            clearGoal(true);
            if (portalWarnTimer <= 0) {
                warning("No Nether portal found in range " + portalSearchRange.get() + ".");
                portalWarnTimer = 60;
            }
            return;
        }

        requestMove(portal);

        if (isNear(portal, 2.25)) state = State.WAITING_PORTAL_TRANSFER;
    }

    private ItemEntity findClosestItem(int range) {
        if (mc.player == null || mc.world == null) return null;

        Box searchBox = new Box(
            mc.player.getX() - range,
            mc.player.getY() - range,
            mc.player.getZ() - range,
            mc.player.getX() + range,
            mc.player.getY() + range,
            mc.player.getZ() + range
        );

        return mc.world.getEntitiesByClass(ItemEntity.class, searchBox, ItemEntity::isAlive)
            .stream()
            .min(Comparator.comparingDouble(item -> item.squaredDistanceTo(mc.player)))
            .orElse(null);
    }

    private BlockPos findClosestPortal(int range) {
        if (mc.player == null || mc.world == null) return null;

        BlockPos center = mc.player.getBlockPos();
        BlockPos bestPos = null;
        double bestSq = Double.MAX_VALUE;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!mc.world.isInBuildLimit(mutable)) continue;
                    if (!mc.world.getBlockState(mutable).isOf(Blocks.NETHER_PORTAL)) continue;

                    double sq = mc.player.squaredDistanceTo(mutable.getX() + 0.5, mutable.getY(), mutable.getZ() + 0.5);
                    if (sq < bestSq) {
                        bestSq = sq;
                        bestPos = mutable.toImmutable();
                    }
                }
            }
        }

        return bestPos;
    }

    private void requestMove(BlockPos target) {
        if (target == null) return;

        if (activeGoal != null && activeGoal.equals(target) && repathTimer > 0) {
            repathTimer--;
            return;
        }

        PathManagers.get().moveTo(target, false);
        activeGoal = target.toImmutable();
        repathTimer = repathDelayTicks.get();
    }

    private void clearGoal(boolean stopPathing) {
        if (stopPathing) PathManagers.get().stop();
        activeGoal = null;
        repathTimer = 0;
    }

    private boolean isNear(BlockPos pos, double maxDistanceSq) {
        return mc.player != null && mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= maxDistanceSq;
    }

    private enum State {
        IDLE,
        NETHER_COLLECTING,
        NETHER_RETURN_AND_DIE,
        WAITING_RESPAWN,
        OVERWORLD_RETURN_TO_PORTAL,
        WAITING_PORTAL_TRANSFER
    }

}


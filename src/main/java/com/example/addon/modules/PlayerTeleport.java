//written by etianll :D
package com.example.addon.modules;

import com.example.addon.Dune;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;

public class PlayerTeleport extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Maximum distance to search for players.")
        .defaultValue(48)
        .sliderRange(8, 128)
        .min(1)
        .max(256)
        .build()
    );

    private final Setting<Integer> blocksUnder = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-under")
        .description("How many blocks under the target player to teleport.")
        .defaultValue(2)
        .sliderRange(1, 12)
        .min(1)
        .max(64)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Skips players on your Meteor friends list when selecting a target.")
        .defaultValue(true)
        .build()
    );

    public PlayerTeleport() {
        super(Dune.Main, "PlayerTeleport", "Automatically teleports you under the nearest player in range.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            error("No Player in range");
            toggle();
            return;
        }

        var candidates = mc.world.getPlayers().stream()
            .filter(player -> player != mc.player)
            .filter(PlayerEntity::isAlive)
            .filter(player -> !player.isSpectator())
            .filter(player -> !ignoreFriends.get() || !Friends.get().isFriend(player))
            .filter(player -> player.squaredDistanceTo(mc.player) <= range.get() * range.get())
            .sorted(Comparator.comparingDouble(player -> player.squaredDistanceTo(mc.player)))
            .toList();

        for (PlayerEntity candidate : candidates) {
            BlockPos destination = candidate.getBlockPos().down(blocksUnder.get());
            if (!isSafeDestination(destination)) continue;

            mc.player.setVelocity(0, 0, 0);
            mc.player.setPos(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
            toggle();
            return;
        }

        error("No Player in range");
        toggle();
    }

    private boolean isSafeDestination(BlockPos feetPos) {
        if (mc.world == null) return false;

        BlockPos headPos = feetPos.up();
        return mc.world.isInBuildLimit(feetPos)
            && mc.world.isInBuildLimit(headPos)
            && mc.world.getBlockState(feetPos).isReplaceable()
            && mc.world.getBlockState(headPos).isReplaceable();
    }
}

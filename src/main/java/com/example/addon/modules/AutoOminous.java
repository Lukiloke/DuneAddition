package com.example.addon.modules;

import com.example.addon.Dune;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoOminous extends Module {
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\b([1-5]|V|IV|III|II|I)\\b");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swaps back to your previous selected hotbar slot after drinking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> restartDelaySeconds = sgGeneral.add(new IntSetting.Builder()
        .name("restart-delay-seconds")
        .description("Delay before drinking the next ominous bottle.")
        .defaultValue(420)
        .min(5)
        .sliderRange(5, 1800)
        .build()
    );

    private int cooldownTicks;
    private boolean drinking;
    private boolean startedUsing;
    private int activeBottleSlot;
    private int activeBottleCount;

    public AutoOminous() {
        super(Dune.Main, "auto-ominous", "Drinks the strongest hotbar ominous bottle on a loop.");
    }

    @Override
    public void onActivate() {
        cooldownTicks = 0;
        drinking = false;
        startedUsing = false;
        activeBottleSlot = -1;
        activeBottleCount = 0;
    }

    @Override
    public void onDeactivate() {
        mc.options.useKey.setPressed(false);
        if (swapBack.get()) InvUtils.swapBack();
        drinking = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (drinking) {
            handleDrinkingState();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        if (mc.player.isUsingItem()) return;

        int slot = findBestOminousSlot();
        if (slot == -1) return;

        startDrinking(slot);
    }

    private void startDrinking(int slot) {
        if (!InvUtils.swap(slot, swapBack.get())) return;

        activeBottleSlot = slot;
        activeBottleCount = mc.player.getInventory().getStack(slot).getCount();
        startedUsing = false;
        drinking = true;

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.options.useKey.setPressed(true);
    }

    private void handleDrinkingState() {
        if (mc.player.isUsingItem() && mc.player.getMainHandStack().isOf(Items.OMINOUS_BOTTLE)) {
            startedUsing = true;
            mc.options.useKey.setPressed(true);
            return;
        }

        mc.options.useKey.setPressed(false);

        boolean consumed = startedUsing && wasBottleConsumed();
        if (swapBack.get()) InvUtils.swapBack();

        drinking = false;
        startedUsing = false;
        activeBottleSlot = -1;
        activeBottleCount = 0;

        cooldownTicks = consumed ? restartDelaySeconds.get() * 20 : 20;
    }

    private boolean wasBottleConsumed() {
        if (activeBottleSlot < 0 || activeBottleSlot > 8) return false;

        ItemStack stack = mc.player.getInventory().getStack(activeBottleSlot);
        if (!stack.isOf(Items.OMINOUS_BOTTLE)) return true;
        return stack.getCount() < activeBottleCount;
    }

    private int findBestOminousSlot() {
        int bestSlot = -1;
        int bestLevel = 0;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isOf(Items.OMINOUS_BOTTLE) || stack.isEmpty()) continue;

            int level = getBottleLevel(stack);
            if (level > bestLevel) {
                bestLevel = level;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private int getBottleLevel(ItemStack stack) {
        String name = stack.getName().getString();
        Matcher matcher = LEVEL_PATTERN.matcher(name);

        int best = 1;
        while (matcher.find()) {
            int parsed = parseLevelToken(matcher.group(1));
            if (parsed > best) best = parsed;
        }

        return best;
    }

    private int parseLevelToken(String token) {
        return switch (token) {
            case "I", "1" -> 1;
            case "II", "2" -> 2;
            case "III", "3" -> 3;
            case "IV", "4" -> 4;
            case "V", "5" -> 5;
            default -> 1;
        };
    }
}

/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static net.minecraft.entity.effect.StatusEffects.*;

public class AutoPot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHeal = settings.createGroup("Healing");
    private final SettingGroup sgBuff = settings.createGroup("Buffing");

    // General

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to throw splash potions (pitch look-down range).")
        .defaultValue(4.0)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between throwing potions.")
        .defaultValue(2)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates server-side before throwing the potion.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings hand client-side when throwing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Only throws potions when you are on the ground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> antiWeakness = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-weakness")
        .description("Throw a strength potion when you have weakness and no strength.")
        .defaultValue(false)
        .build()
    );

    // Healing

    private final Setting<Boolean> autoHeal = sgHeal.add(new BoolSetting.Builder()
        .name("auto-heal")
        .description("Automatically throws healing splash potions when health drops below threshold.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> healthThreshold = sgHeal.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("Throw a healing potion when your health falls below this value.")
        .defaultValue(8.0)
        .min(1)
        .sliderMax(20)
        .visible(autoHeal::get)
        .build()
    );

    private final Setting<Boolean> healConsiderAbsorption = sgHeal.add(new BoolSetting.Builder()
        .name("consider-absorption")
        .description("Factor in absorption hearts when checking health threshold.")
        .defaultValue(false)
        .visible(autoHeal::get)
        .build()
    );

    private final Setting<Boolean> preferHealthPot = sgHeal.add(new BoolSetting.Builder()
        .name("prefer-health-potions")
        .description("Prioritize healing potions over harming potions for self-heal.")
        .defaultValue(true)
        .visible(autoHeal::get)
        .build()
    );

    // Buffing

    private final Setting<Boolean> autoBuff = sgBuff.add(new BoolSetting.Builder()
        .name("auto-buff")
        .description("Automatically throws buff splash potions when effects are missing or expiring.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<StatusEffect>> buffEffects = sgBuff.add(new StatusEffectListSetting.Builder()
        .name("buff-effects")
        .description("Which effects to automatically re-pot.")
        .defaultValue(
            STRENGTH.value(),
            SPEED.value(),
            FIRE_RESISTANCE.value()
        )
        .visible(autoBuff::get)
        .build()
    );

    private final Setting<Boolean> buffCheckExisting = sgBuff.add(new BoolSetting.Builder()
        .name("check-existing")
        .description("Only throws buff potions when you don't already have the effect.")
        .defaultValue(true)
        .visible(autoBuff::get)
        .build()
    );

    private final Setting<Integer> buffMinDuration = sgBuff.add(new IntSetting.Builder()
        .name("min-duration")
        .description("Re-pot when effect duration is below this in ticks (only when check-existing is off).")
        .defaultValue(200)
        .min(0)
        .sliderMax(600)
        .visible(() -> autoBuff.get() && !buffCheckExisting.get())
        .build()
    );

    private final Setting<Boolean> buffCheckAmplifier = sgBuff.add(new BoolSetting.Builder()
        .name("check-amplifier")
        .description("Only throws buff potions if the found potion has equal or higher amplifier than current.")
        .defaultValue(true)
        .visible(() -> autoBuff.get() && buffCheckExisting.get())
        .build()
    );

    private final Setting<Boolean> swapBack = sgBuff.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to your previous hotbar slot after throwing.")
        .defaultValue(true)
        .build()
    );

    private int timer = 0;
    private int prevSlot = -1;

    public AutoPot() {
        super(Categories.Player, "auto-pot", "Automatically throws healing and buff splash potions.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        prevSlot = -1;
    }

    @Override
    public void onDeactivate() {
        if (prevSlot != -1) {
            InvUtils.swapBack();
            prevSlot = -1;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // Safety checks
        if (onlyOnGround.get() && !mc.player.isOnGround()) return;
        if (mc.player.isUsingItem()) return;

        // Delay
        if (timer > 0) {
            timer--;
            return;
        }

        // Check anti-weakness first (priority)
        if (antiWeakness.get() && shouldAntiWeakness()) {
            StatusEffect targetEffect = StatusEffects.STRENGTH.value();
            int slot = findPotionWithEffect(targetEffect, false);
            if (slot != -1) {
                throwPotion(slot);
                return;
            }
        }

        // Check healing
        if (autoHeal.get() && shouldHeal()) {
            int healSlot = findHealingPotion();
            if (healSlot != -1) {
                throwPotion(healSlot);
                return;
            }
        }

        // Check buffing
        if (autoBuff.get()) {
            for (StatusEffect effect : buffEffects.get()) {
                if (shouldBuff(effect)) {
                    int slot = findPotionWithEffect(effect, buffCheckAmplifier.get());
                    if (slot != -1) {
                        throwPotion(slot);
                        return;
                    }
                }
            }
        }
    }

    // ===== Healing Logic =====

    private boolean shouldHeal() {
        float health = mc.player.getHealth();
        if (healConsiderAbsorption.get()) {
            health += mc.player.getAbsorptionAmount();
        }
        return health <= healthThreshold.get();
    }

    /**
     * Finds a healing splash potion (instant health) or harming potion (if no healing found and preferHealthPot is off).
     * In 1.21+, healing and harming potions are both splash_potion items differentiated by their potion contents.
     */
    private int findHealingPotion() {
        int healthSlot = -1;
        int harmSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != Items.SPLASH_POTION) continue;

            StatusEffect effect = getFirstEffect(stack);
            if (effect == null) continue;

            if (effect == StatusEffects.INSTANT_HEALTH.value() && healthSlot == -1) {
                healthSlot = i;
            } else if (effect == StatusEffects.INSTANT_DAMAGE.value() && harmSlot == -1) {
                harmSlot = i;
            }
        }

        // Healing potions deal damage to undead mobs but heal the player.
        // Harming potions damage the player too, so we only use them as last resort.
        if (preferHealthPot.get()) {
            return healthSlot != -1 ? healthSlot : harmSlot;
        } else {
            return harmSlot != -1 ? harmSlot : healthSlot;
        }
    }

    // ===== Buffing Logic =====

    private boolean shouldBuff(StatusEffect effect) {
        if (buffCheckExisting.get()) {
            // Check if player already has the effect
            StatusEffectInstance existing = mc.player.getStatusEffect(net.minecraft.registry.entry.RegistryEntry.of(effect));
            if (existing != null) {
                // Effect already present, don't re-pot
                return false;
            }
            return true;
        } else {
            // Check duration
            StatusEffectInstance existing = mc.player.getStatusEffect(net.minecraft.registry.entry.RegistryEntry.of(effect));
            if (existing == null) return true;
            return existing.getDuration() <= buffMinDuration.get();
        }
    }

    private boolean shouldAntiWeakness() {
        boolean hasWeakness = false;
        boolean hasStrength = false;

        for (StatusEffectInstance effectInstance : mc.player.getStatusEffects()) {
            StatusEffect effect = effectInstance.getEffectType().value();
            if (effect == StatusEffects.WEAKNESS.value()) hasWeakness = true;
            if (effect == StatusEffects.STRENGTH.value()) hasStrength = true;
        }

        return hasWeakness && !hasStrength;
    }

    /**
     * Finds a splash potion in the hotbar that has the given status effect.
     * If checkAmplifier is true, only returns potions with amplifier >= current effect amplifier.
     */
    private int findPotionWithEffect(StatusEffect targetEffect, boolean checkAmplifier) {
        int bestSlot = -1;
        int bestAmplifier = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != Items.SPLASH_POTION) continue;

            StatusEffect effect = getFirstEffect(stack);
            if (effect == null || effect != targetEffect) continue;

            int amplifier = getAmplifier(stack);

            if (checkAmplifier) {
                StatusEffectInstance existing = mc.player.getStatusEffect(net.minecraft.registry.entry.RegistryEntry.of(targetEffect));
                int currentAmp = existing != null ? existing.getAmplifier() : -1;
                if (amplifier < currentAmp) continue;
            }

            // Pick the slot with the highest amplifier
            if (amplifier > bestAmplifier) {
                bestAmplifier = amplifier;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    // ===== Potion Utilities =====

    private StatusEffect getFirstEffect(ItemStack stack) {
        var contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return null;

        var effects = contents.getEffects();
        if (effects.isEmpty()) return null;

        return effects.get(0).getEffectType().value();
    }

    private int getAmplifier(ItemStack stack) {
        var contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return 0;

        var effects = contents.getEffects();
        if (effects.isEmpty()) return 0;

        return effects.get(0).getAmplifier();
    }

    // ===== Throwing Logic =====

    private void throwPotion(int slot) {
        // Rotate to look down at feet
        if (rotate.get()) {
            float yaw = mc.player.getYaw();
            float pitch = 90f; // Look straight down

            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround()));
        }

        // Swap to the potion slot
        InvUtils.swap(slot, swapBack.get());

        // Use the item (throw the splash potion)
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

        if (swing.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        // Reset timer
        timer = delay.get();
    }

    @Override
    public String getInfoString() {
        int potionCount = 0;
        for (int i = 0; i < 9; i++) {
            if (mc.player != null && mc.player.getInventory().getStack(i).getItem() == Items.SPLASH_POTION) {
                potionCount++;
            }
        }
        return potionCount > 0 ? String.valueOf(potionCount) : null;
    }
}

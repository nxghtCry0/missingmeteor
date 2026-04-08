/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

import java.util.function.Predicate;

public class CreeperAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to search for creepers to ignite.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between igniting creepers.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the creeper before igniting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings hand when igniting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> flintAndSteel = sgGeneral.add(new BoolSetting.Builder()
        .name("flint-and-steel")
        .description("Use flint and steel to ignite creepers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fireCharge = sgGeneral.add(new BoolSetting.Builder()
        .name("fire-charge")
        .description("Use fire charges to ignite creepers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to your previous hotbar slot after igniting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyAlreadyPrimed = sgGeneral.add(new BoolSetting.Builder()
        .name("only-primed")
        .description("Only ignite creepers that are already in their explosion fuse countdown (started by other damage).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses if the server is lagging.")
        .defaultValue(true)
        .build()
    );

    private Entity target;
    private Hand hand;
    private int timer = 0;

    public CreeperAura() {
        super(Categories.Combat, "creeper-aura", "Automatically ignites creepers with flint and steel or fire charges.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        target = null;
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Check server TPS
        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() >= 1f) return;

        // Delay
        if (timer > 0) {
            timer--;
            return;
        }

        // Don't act if a screen is open
        if (mc.currentScreen != null) return;

        // Build the item predicate based on settings
        Predicate<ItemStack> igniterPredicate = stack -> {
            if (flintAndSteel.get() && stack.getItem() == Items.FLINT_AND_STEEL) return true;
            if (fireCharge.get() && stack.getItem() == Items.FIRE_CHARGE) return true;
            return false;
        };

        // Find an igniter in the hotbar
        FindItemResult igniter = InvUtils.findInHotbar(igniterPredicate);
        if (!igniter.found()) return;

        // Search for the closest valid creeper
        target = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof CreeperEntity creeper)) continue;
            if (!creeper.isAlive()) continue;

            // Check if creeper is already ignited (has active fuse)
            if (creeper.isIgnited()) continue;

            // If only-primed, skip creepers not yet in fuse state
            if (onlyAlreadyPrimed.get() && creeper.getFuseSpeed() == 0) continue;

            // Check range
            if (!PlayerUtils.isWithin(entity, range.get())) continue;

            // Check interaction range
            if (!PlayerUtils.isWithinReach(entity)) continue;

            // Check visibility (can see the creeper)
            if (!mc.player.canSee(entity)) continue;

            double dist = mc.player.squaredDistanceTo(entity);
            if (dist < closestDist) {
                closestDist = dist;
                target = entity;
            }
        }

        if (target == null) return;

        // Swap to the igniter
        hand = igniter.getHand();
        InvUtils.swap(igniter.slot(), swapBack.get());

        // Ignite the creeper
        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), -100, this::ignite);
        } else {
            ignite();
        }

        timer = delay.get();
    }

    private void ignite() {
        if (target == null || !target.isAlive()) return;

        EntityHitResult hitResult = new EntityHitResult(target, target.getBoundingBox().getCenter());
        mc.interactionManager.interactEntityAtLocation(mc.player, target, hitResult, hand);
        mc.interactionManager.interactEntity(mc.player, target, hand);

        if (swing.get()) {
            mc.player.swingHand(hand);
        }

        if (swapBack.get()) {
            InvUtils.swapBack();
        }
    }

    @Override
    public String getInfoString() {
        return target != null ? "1" : null;
    }
}

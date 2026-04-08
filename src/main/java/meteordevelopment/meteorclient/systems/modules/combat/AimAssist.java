/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AimAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgSpeed = settings.createGroup("Speed");

    // General

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only assists aiming when holding left click.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyOnLook = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-look")
        .description("Only assists aiming when already looking near a target (within FOV).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Does not assist while using an item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses if the server is lagging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-attack")
        .description("Automatically attacks when you are on target.")
        .defaultValue(false)
        .build()
    );

    private final Setting<TargetPoint> targetPoint = sgGeneral.add(new EnumSetting.Builder<TargetPoint>()
        .name("target-point")
        .description("Which part of the entity to aim at.")
        .defaultValue(TargetPoint.Body)
        .build()
    );

    // Targeting

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to aim at.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range the entity can be to aim at it.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> wallsRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("The maximum range the entity can be aimed at through walls.")
        .defaultValue(0)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> fov = sgTargeting.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Maximum angle in degrees from your crosshair to start aiming.")
        .defaultValue(90.0)
        .min(1)
        .sliderMax(180)
        .build()
    );

    private final Setting<EntityAge> passiveMobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAge>()
        .name("passive-mob-age-filter")
        .description("Determines the age of passive mobs to target (animals, villagers).")
        .defaultValue(EntityAge.Adult)
        .build()
    );

    private final Setting<EntityAge> hostileMobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAge>()
        .name("hostile-mob-age-filter")
        .description("Determines the age of hostile mobs to target (zombies, piglins, hoglins, zoglins).")
        .defaultValue(EntityAge.Both)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Whether or not to aim at mobs with a name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-passive")
        .description("Will only aim at normally passive mobs if they are targeting you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-tamed")
        .description("Will avoid aiming at mobs you tamed.")
        .defaultValue(false)
        .build()
    );

    // Speed

    private final Setting<Double> horizontalSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("How fast to move horizontally towards the target (degrees per tick).")
        .defaultValue(30.0)
        .min(0)
        .sliderMax(180)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("How fast to move vertically towards the target (degrees per tick).")
        .defaultValue(30.0)
        .min(0)
        .sliderMax(180)
        .build()
    );

    private final Setting<Boolean> instantHorizontal = sgSpeed.add(new BoolSetting.Builder()
        .name("instant-horizontal")
        .description("Instantly snaps horizontal aim to the target.")
        .defaultValue(false)
        .visible(() -> horizontalSpeed.get() > 0)
        .build()
    );

    private final Setting<Boolean> instantVertical = sgSpeed.add(new BoolSetting.Builder()
        .name("instant-vertical")
        .description("Instantly snaps vertical aim to the target.")
        .defaultValue(false)
        .visible(() -> verticalSpeed.get() > 0)
        .build()
    );

    private final Setting<Boolean> lookSmoothServer = sgSpeed.add(new BoolSetting.Builder()
        .name("smooth-server-side")
        .description("Smoothly rotates on the server side as well, not just client side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> smoothingStrength = sgSpeed.add(new DoubleSetting.Builder()
        .name("smoothing-strength")
        .description("How aggressive the smoothing is. Higher = snappier, lower = smoother.")
        .defaultValue(0.5)
        .min(0.01)
        .sliderMax(1.0)
        .visible(lookSmoothServer::get)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();
    private Entity currentTarget;
    private float targetYaw, targetPitch;
    private boolean wasAttacking;

    public AimAssist() {
        super(Categories.Combat, "aim-assist", "Smoothly assists your aim towards the nearest target.");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
        wasAttacking = false;
        targets.clear();
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        currentTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;
        if (pauseOnUse.get() && (mc.interactionManager.isBreakingBlock() || mc.player.isUsingItem())) return;
        if (onlyOnClick.get() && !mc.options.attackKey.isPressed()) {
            currentTarget = null;
            return;
        }
        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1f && pauseOnLag.get()) return;

        // Find targets
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), 1);

        if (targets.isEmpty()) {
            currentTarget = null;
            return;
        }

        Entity target = targets.getFirst();
        currentTarget = target;

        // Calculate desired yaw and pitch
        double desiredYaw = mc.player.getYaw() + MathHelper.wrapDegrees((float) Math.toDegrees(
            Math.atan2(target.getZ() - mc.player.getZ(), target.getX() - mc.player.getX())) - 90f - mc.player.getYaw());

        double y;
        if (targetPoint.get() == TargetPoint.Head) y = target.getEyeY();
        else if (targetPoint.get() == TargetPoint.Body) y = target.getY() + target.getHeight() / 2;
        else y = target.getY();

        double diffX = target.getX() - mc.player.getX();
        double diffY = y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = target.getZ() - mc.player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        double desiredPitch = mc.player.getPitch() + MathHelper.wrapDegrees(
            (float) -Math.toDegrees(Math.atan2(diffY, diffXZ)) - mc.player.getPitch());

        // Check if target is within FOV
        if (onlyOnLook.get()) {
            double angleDiff = Math.sqrt(
                Math.pow(MathHelper.wrapDegrees((float) desiredYaw - mc.player.getYaw()), 2) +
                Math.pow(MathHelper.wrapDegrees((float) desiredPitch - mc.player.getPitch()), 2)
            );
            if (angleDiff > fov.get()) {
                currentTarget = null;
                return;
            }
        }

        // Calculate rotation deltas
        float yawDelta = MathHelper.wrapDegrees((float) desiredYaw - mc.player.getYaw());
        float pitchDelta = MathHelper.wrapDegrees((float) desiredPitch - mc.player.getPitch());

        // Apply smoothing
        float newYaw = mc.player.getYaw();
        float newPitch = mc.player.getPitch();

        // Horizontal
        if (instantHorizontal.get()) {
            newYaw += yawDelta;
        } else if (horizontalSpeed.get() > 0) {
            if (Math.abs(yawDelta) < horizontalSpeed.get().floatValue()) {
                newYaw += yawDelta;
            } else {
                newYaw += Math.signum(yawDelta) * horizontalSpeed.get().floatValue();
            }
        }

        // Vertical
        if (instantVertical.get()) {
            newPitch += pitchDelta;
        } else if (verticalSpeed.get() > 0) {
            if (Math.abs(pitchDelta) < verticalSpeed.get().floatValue()) {
                newPitch += pitchDelta;
            } else {
                newPitch += Math.signum(pitchDelta) * verticalSpeed.get().floatValue();
            }
        }

        // Clamp pitch to valid range
        newPitch = MathHelper.clamp(newPitch, -90f, 90f);

        // Apply client-side rotation
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        // Store target server rotation for smooth server-side rotation
        targetYaw = newYaw;
        targetPitch = newPitch;

        // Auto-attack when close enough to target
        if (autoAttack.get() && isOnTarget(target)) {
            if (!wasAttacking) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                wasAttacking = true;
            }
        } else {
            wasAttacking = false;
        }
    }

    @EventHandler
    private void onSendMovementPackets(SendMovementPacketsEvent.Pre event) {
        if (currentTarget == null || !lookSmoothServer.get()) return;
        if (mc.getCameraEntity() != mc.player) return;

        // Smoothly interpolate server-side rotation towards target
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float serverYawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float serverPitchDelta = MathHelper.wrapDegrees(targetPitch - currentPitch);

        float strength = smoothingStrength.get().floatValue();

        float newServerYaw = currentYaw + serverYawDelta * strength;
        float newServerPitch = currentPitch + serverPitchDelta * strength;
        newServerPitch = MathHelper.clamp(newServerPitch, -90f, 90f);

        mc.player.setYaw(newServerYaw);
        mc.player.setPitch(newServerPitch);
    }

    private boolean isOnTarget(Entity target) {
        double y;
        if (targetPoint.get() == TargetPoint.Head) y = target.getEyeY();
        else if (targetPoint.get() == TargetPoint.Body) y = target.getY() + target.getHeight() / 2;
        else y = target.getY();

        double diffX = target.getX() - mc.player.getX();
        double diffY = y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = target.getZ() - mc.player.getZ();

        double desiredYaw = Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f;
        double desiredPitch = -Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ)));

        double yawDiff = Math.abs(MathHelper.wrapDegrees((float) (desiredYaw - mc.player.getYaw())));
        double pitchDiff = Math.abs(MathHelper.wrapDegrees((float) (desiredPitch - mc.player.getPitch())));

        return yawDiff < 15 && pitchDiff < 15;
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead()) || !entity.isAlive()) return false;

        Box hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
            MathHelper.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
            MathHelper.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
            MathHelper.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
            range.get()
        )) return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, wallsRange.get())) return false;
        if (ignoreTamed.get()) {
            if (entity instanceof Tameable tameable
                && tameable.getOwner() != null
                && tameable.getOwner().equals(mc.player)
            ) return false;
        }
        if (ignorePassive.get()) {
            if (entity instanceof EndermanEntity enderman && !enderman.isAngry()) return false;
            if ((entity instanceof PiglinEntity || entity instanceof ZombifiedPiglinEntity || entity instanceof WolfEntity) && !((MobEntity) entity).isAttacking()) return false;
        }
        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (player instanceof FakePlayerEntity fakePlayer && fakePlayer.noHit) return false;
        }
        if (entity instanceof LivingEntity livingEntity) {
            // Hostile mobs with baby variants
            if (entity instanceof ZombieEntity || entity instanceof PiglinEntity
                || entity instanceof HoglinEntity || entity instanceof ZoglinEntity) {
                return switch (hostileMobAgeFilter.get()) {
                    case Baby -> livingEntity.isBaby();
                    case Adult -> !livingEntity.isBaby();
                    case Both -> true;
                };
            }
            // Passive mobs with baby variants
            if (entity instanceof PassiveEntity && (!(entity instanceof FrogEntity || entity instanceof ParrotEntity))) {
                return switch (passiveMobAgeFilter.get()) {
                    case Baby -> livingEntity.isBaby();
                    case Adult -> !livingEntity.isBaby();
                    case Both -> true;
                };
            }
        }
        return true;
    }

    public Entity getTarget() {
        return currentTarget;
    }

    @Override
    public String getInfoString() {
        if (currentTarget != null) return EntityUtils.getName(currentTarget);
        return null;
    }

    public enum TargetPoint {
        Head,
        Body,
        Feet
    }

    public enum EntityAge {
        Baby,
        Adult,
        Both
    }
}

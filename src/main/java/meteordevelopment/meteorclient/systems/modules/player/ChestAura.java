/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class ChestAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range to search for chests.")
        .defaultValue(4.5)
        .min(1)
        .max(6)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between chest interactions in ticks.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the chest before opening.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chests = sgGeneral.add(new BoolSetting.Builder()
        .name("chests")
        .description("Open regular chests and trapped chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enderChests = sgGeneral.add(new BoolSetting.Builder()
        .name("ender-chests")
        .description("Open ender chests.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> shulkerBoxes = sgGeneral.add(new BoolSetting.Builder()
        .name("shulker-boxes")
        .description("Open shulker boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("Swings hand client-side when interacting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyInView = sgGeneral.add(new BoolSetting.Builder()
        .name("only-in-view")
        .description("Only opens chests that are within your crosshair view angle.")
        .defaultValue(false)
        .build()
    );

    private int timer = 0;

    public ChestAura() {
        super(Categories.Player, "chest-aura", "Automatically opens nearby chests and other storage blocks.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Don't do anything if a screen is already open
        if (mc.currentScreen != null) return;

        // Delay timer
        if (timer > 0) {
            timer--;
            return;
        }

        // Search for the closest valid chest
        BlockPos closestPos = null;
        double closestDistance = Double.MAX_VALUE;

        BlockPos playerPos = mc.player.getBlockPos();
        int searchRange = (int) Math.ceil(range.get());

        for (int x = -searchRange; x <= searchRange; x++) {
            for (int y = -searchRange; y <= searchRange; y++) {
                for (int z = -searchRange; z <= searchRange; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    Block block = state.getBlock();

                    if (!isValidBlock(block)) continue;

                    // Check if within range
                    if (!PlayerUtils.isWithin(pos, range.get())) continue;

                    // Check if within reach (interaction range)
                    if (!PlayerUtils.isWithinReach(pos)) continue;

                    // Check if already open (skip viewer check as API varies by version)
                    // BlockEntity entity = mc.world.getBlockEntity(pos);

                    // Check if in view
                    if (onlyInView.get() && !isInView(pos)) continue;

                    double distance = PlayerUtils.squaredDistanceTo(pos);
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestPos = pos;
                    }
                }
            }
        }

        // Open the chest
        if (closestPos != null) {
            openBlock(closestPos);
            timer = delay.get();
        }
    }

    private boolean isValidBlock(Block block) {
        if (chests.get() && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST)) return true;
        if (enderChests.get() && block instanceof EnderChestBlock) return true;
        if (shulkerBoxes.get() && block instanceof ShulkerBoxBlock) return true;
        return false;
    }

    private boolean isInView(BlockPos pos) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d toBlock = pos.toCenterPos().subtract(eyePos).normalize();
        return lookVec.dotProduct(toBlock) > 0.5;
    }

    private void openBlock(BlockPos pos) {
        Vec3d hitPos = pos.toCenterPos();
        Direction side = BlockUtils.getDirection(pos);

        // Make sure we don't pick a side pointing into a block
        BlockHitResult hitResult = new BlockHitResult(hitPos, side, pos, false);

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), -100, () -> {
                BlockUtils.interact(hitResult, Hand.MAIN_HAND, swingHand.get());
            });
        } else {
            BlockUtils.interact(hitResult, Hand.MAIN_HAND, swingHand.get());
        }
    }
}

/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public class AutoExtinguish extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to search for fire blocks.")
        .defaultValue(4.0)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between punching fire blocks.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the fire block being punched.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings hand when punching.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fire = sgGeneral.add(new BoolSetting.Builder()
        .name("fire")
        .description("Extinguishes regular fire blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> soulFire = sgGeneral.add(new BoolSetting.Builder()
        .name("soul-fire")
        .description("Extinguishes soul fire blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> campfires = sgGeneral.add(new BoolSetting.Builder()
        .name("campfires")
        .description("Extinguishes (puts out) lit campfires and soul campfires.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> lava = sgGeneral.add(new BoolSetting.Builder()
        .name("lava")
        .description("Punches source lava blocks around you (does not remove flowing lava).")
        .defaultValue(false)
        .build()
    );

    private final List<BlockPos> toExtinguish = new ArrayList<>();
    private int timer = 0;

    public AutoExtinguish() {
        super(Categories.World, "auto-extinguish", "Automatically punches fire and other hazardous blocks around you.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        toExtinguish.clear();
    }

    @Override
    public void onDeactivate() {
        toExtinguish.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Delay
        if (timer > 0) {
            timer--;
            return;
        }

        toExtinguish.clear();

        BlockIterator.register((int) Math.ceil(range.get()) + 1, (int) Math.ceil(range.get()), (blockPos, blockState) -> {
            if (!PlayerUtils.isWithin(blockPos.toCenterPos(), range.get())) return;

            Block block = blockState.getBlock();

            // Regular fire and soul fire
            if (block instanceof AbstractFireBlock fireBlock) {
                // AbstractFireBlock covers both FireBlock and SoulFireBlock
                if (fireBlock.isInfinite(mc.world, blockPos, blockState)) {
                    // Infinite fire on bedrock - still punchable in the nether
                    toExtinguish.add(blockPos.toImmutable());
                } else {
                    if (block == Blocks.FIRE && fire.get()) {
                        toExtinguish.add(blockPos.toImmutable());
                    } else if (block == Blocks.SOUL_FIRE && soulFire.get()) {
                        toExtinguish.add(blockPos.toImmutable());
                    }
                }
                return;
            }

            // Campfires and soul campfires
            if (campfires.get() && block instanceof CampfireBlock campfire) {
                if (campfire.isLit(blockState)) {
                    toExtinguish.add(blockPos.toImmutable());
                }
                return;
            }

            // Lava source blocks
            if (lava.get() && block == Blocks.LAVA) {
                toExtinguish.add(blockPos.toImmutable());
                return;
            }
        });

        BlockIterator.after(() -> {
            if (toExtinguish.isEmpty()) return;

            for (BlockPos pos : toExtinguish) {
                BlockState state = mc.world.getBlockState(pos);

                // For fire/campfires we punch (interact), for lava we try to break
                boolean isFireOrCampfire = state.getBlock() instanceof AbstractFireBlock
                    || (state.getBlock() instanceof CampfireBlock cb && cb.isLit(state));

                if (isFireOrCampfire) {
                    // Punch the fire/campfire by interacting with it
                    if (rotate.get()) {
                        Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> interactBlock(pos));
                    } else {
                        interactBlock(pos);
                    }
                } else {
                    // For lava, try breaking (won't actually break but will place adjacent blocks)
                    if (!BlockUtils.canBreak(pos, state)) continue;

                    if (!hasLineOfSight(pos)) continue;

                    if (rotate.get()) {
                        Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> BlockUtils.breakBlock(pos, swing.get()));
                    } else {
                        BlockUtils.breakBlock(pos, swing.get());
                    }
                }

                timer = delay.get();
                break;
            }

            toExtinguish.clear();
        });
    }

    private void interactBlock(BlockPos pos) {
        // Check line of sight first
        if (!hasLineOfSight(pos)) return;

        BlockHitResult hitResult = new BlockHitResult(pos.toCenterPos(), net.minecraft.util.math.Direction.UP, pos, false);
        mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerInteractBlockPacket(
            Hand.MAIN_HAND, hitResult, 0
        ));
        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean hasLineOfSight(BlockPos blockPos) {
        Vec3d from = mc.player.getEyePos();
        Vec3d to = blockPos.toCenterPos();
        RaycastContext context = new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult result = mc.world.raycast(context);
        return result != null && result.getBlockPos().equals(blockPos);
    }

    @Override
    public String getInfoString() {
        return String.valueOf(toExtinguish.size());
    }
}

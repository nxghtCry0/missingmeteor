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
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public class AutoFarm extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFarming = settings.createGroup("Farming");
    private final SettingGroup sgStem = settings.createGroup("Stem Crops");

    // General

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away to search for farmable blocks.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between breaking blocks.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the block being broken.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings hand when breaking.")
        .defaultValue(true)
        .build()
    );

    // Farming - Crops

    private final Setting<Boolean> harvestCrops = sgFarming.add(new BoolSetting.Builder()
        .name("harvest-crops")
        .description("Automatically harvests fully grown crops (wheat, carrots, potatoes, beetroot).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> harvestNetherWart = sgFarming.add(new BoolSetting.Builder()
        .name("harvest-nether-wart")
        .description("Automatically harvests fully grown nether wart.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> harvestStemBlocks = sgFarming.add(new BoolSetting.Builder()
        .name("harvest-stem-blocks")
        .description("Harvests pumpkin and melon blocks.")
        .defaultValue(true)
        .build()
    );

    // Stem Crops - Cactus & Sugar Cane

    private final Setting<Boolean> harvestCactus = sgStem.add(new BoolSetting.Builder()
        .name("harvest-cactus")
        .description("Breaks cactus above the bottom block, leaving one to regrow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cactusBottomKeep = sgStem.add(new IntSetting.Builder()
        .name("cactus-bottom-keep")
        .description("How many cactus blocks to leave at the bottom for regrowth.")
        .defaultValue(1)
        .min(1)
        .sliderMax(3)
        .visible(harvestCactus::get)
        .build()
    );

    private final Setting<Boolean> harvestSugarCane = sgStem.add(new BoolSetting.Builder()
        .name("harvest-sugar-cane")
        .description("Breaks sugar cane above the bottom block, leaving one to regrow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> sugarCaneBottomKeep = sgStem.add(new IntSetting.Builder()
        .name("sugar-cane-bottom-keep")
        .description("How many sugar cane blocks to leave at the bottom for regrowth.")
        .defaultValue(1)
        .min(1)
        .sliderMax(3)
        .visible(harvestSugarCane::get)
        .build()
    );

    private final Setting<Boolean> harvestBamboo = sgStem.add(new BoolSetting.Builder()
        .name("harvest-bamboo")
        .description("Breaks bamboo above the bottom block, leaving one to regrow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> bambooBottomKeep = sgStem.add(new IntSetting.Builder()
        .name("bamboo-bottom-keep")
        .description("How many bamboo blocks to leave at the bottom for regrowth.")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .visible(harvestBamboo::get)
        .build()
    );

    private final Setting<Boolean> harvestKelp = sgStem.add(new BoolSetting.Builder()
        .name("harvest-kelp")
        .description("Breaks kelp above the bottom block, leaving one to regrow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> kelpBottomKeep = sgStem.add(new IntSetting.Builder()
        .name("kelp-bottom-keep")
        .description("How many kelp blocks to leave at the bottom for regrowth.")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .visible(harvestKelp::get)
        .build()
    );

    private final List<BlockPos> toBreak = new ArrayList<>();
    private int timer = 0;

    public AutoFarm() {
        super(Categories.World, "auto-farm", "Automatically breaks crops, cactus, sugar cane and more.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        toBreak.clear();
    }

    @Override
    public void onDeactivate() {
        toBreak.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Delay
        if (timer > 0) {
            timer--;
            return;
        }

        toBreak.clear();

        BlockIterator.register((int) Math.ceil(range.get()) + 1, (int) Math.ceil(range.get()), (blockPos, blockState) -> {
            if (!PlayerUtils.isWithin(blockPos.toCenterPos(), range.get())) return;

            Block block = blockState.getBlock();

            // Regular crops - check if mature
            if (harvestCrops.get() && isCropMature(blockState)) {
                toBreak.add(blockPos.toImmutable());
                return;
            }

            // Nether wart
            if (harvestNetherWart.get() && isNetherWartMature(blockState)) {
                toBreak.add(blockPos.toImmutable());
                return;
            }

            // Stem fruit blocks (pumpkin, melon)
            if (harvestStemBlocks.get() && (block == Blocks.PUMPKIN || block == Blocks.MELON)) {
                toBreak.add(blockPos.toImmutable());
                return;
            }

            // Cactus - break all except bottom N blocks
            if (harvestCactus.get() && block == Blocks.CACTUS) {
                int bottomY = findBottomStem(blockPos, Blocks.CACTUS);
                if (blockPos.getY() >= bottomY + cactusBottomKeep.get()) {
                    toBreak.add(blockPos.toImmutable());
                }
                return;
            }

            // Sugar cane - break all except bottom N blocks
            if (harvestSugarCane.get() && block == Blocks.SUGAR_CANE) {
                int bottomY = findBottomStem(blockPos, Blocks.SUGAR_CANE);
                if (blockPos.getY() >= bottomY + sugarCaneBottomKeep.get()) {
                    toBreak.add(blockPos.toImmutable());
                }
                return;
            }

            // Bamboo - break all except bottom N blocks
            if (harvestBamboo.get() && block == Blocks.BAMBOO_BLOCK) {
                int bottomY = findBottomBamboo(blockPos);
                if (blockPos.getY() >= bottomY + bambooBottomKeep.get()) {
                    toBreak.add(blockPos.toImmutable());
                }
                return;
            }

            // Bamboo shoot (younger bamboo)
            if (harvestBamboo.get() && block == Blocks.BAMBOO) {
                int bottomY = findBottomBambooShoot(blockPos);
                if (blockPos.getY() >= bottomY + bambooBottomKeep.get()) {
                    toBreak.add(blockPos.toImmutable());
                }
                return;
            }

            // Kelp - break all except bottom N blocks
            if (harvestKelp.get() && block == Blocks.KELP) {
                int bottomY = findBottomStem(blockPos, Blocks.KELP);
                if (blockPos.getY() >= bottomY + kelpBottomKeep.get()) {
                    toBreak.add(blockPos.toImmutable());
                }
                return;
            }

            // Kelp plant
            if (harvestKelp.get() && block == Blocks.KELP_PLANT) {
                int bottomY = findBottomKelpPlant(blockPos);
                if (blockPos.getY() >= bottomY + kelpBottomKeep.get()) {
                    toBreak.add(blockPos.toImmutable());
                }
                return;
            }
        });

        BlockIterator.after(() -> {
            if (toBreak.isEmpty()) return;

            for (BlockPos pos : toBreak) {
                BlockState state = mc.world.getBlockState(pos);
                if (!BlockUtils.canBreak(pos, state)) continue;

                // Check line of sight, skip if blocked
                if (!hasLineOfSight(pos)) continue;

                if (rotate.get()) {
                    Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> BlockUtils.breakBlock(pos, swing.get()));
                } else {
                    BlockUtils.breakBlock(pos, swing.get());
                }

                // Only break one block per tick to avoid issues, then apply delay
                timer = delay.get();
                break;
            }

            toBreak.clear();
        });
    }

    private boolean hasLineOfSight(BlockPos blockPos) {
        Vec3d from = mc.player.getEyePos();
        Vec3d to = blockPos.toCenterPos();
        RaycastContext context = new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult result = mc.world.raycast(context);
        return result != null && result.getBlockPos().equals(blockPos);
    }

    private boolean isCropMature(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMature(state);
        }
        return false;
    }

    private boolean isNetherWartMature(BlockState state) {
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.get(NetherWartBlock.AGE) >= 3;
        }
        return false;
    }

    /**
     * Finds the lowest Y position of a vertical stem block column.
     * Walks downward from the given position as long as the same block type exists below.
     */
    private int findBottomStem(BlockPos pos, Block block) {
        int y = pos.getY();
        while (mc.world.getBlockState(pos.withY(y - 1)).getBlock() == block) {
            y--;
        }
        return y;
    }

    /**
     * Finds the bottom of a bamboo stalk (bamboo_block).
     */
    private int findBottomBamboo(BlockPos pos) {
        int y = pos.getY();
        BlockPos below;
        while (true) {
            below = pos.withY(y - 1);
            Block b = mc.world.getBlockState(below).getBlock();
            if (b == Blocks.BAMBOO_BLOCK || b == Blocks.BAMBOO) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    /**
     * Finds the bottom of a bamboo shoot (bamboo).
     */
    private int findBottomBambooShoot(BlockPos pos) {
        int y = pos.getY();
        BlockPos below;
        while (true) {
            below = pos.withY(y - 1);
            Block b = mc.world.getBlockState(below).getBlock();
            if (b == Blocks.BAMBOO || b == Blocks.BAMBOO_BLOCK) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    /**
     * Finds the bottom of a kelp column.
     */
    private int findBottomKelpPlant(BlockPos pos) {
        int y = pos.getY();
        BlockPos below;
        while (true) {
            below = pos.withY(y - 1);
            Block b = mc.world.getBlockState(below).getBlock();
            if (b == Blocks.KELP_PLANT || b == Blocks.KELP) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    @Override
    public String getInfoString() {
        return String.valueOf(toBreak.size());
    }
}

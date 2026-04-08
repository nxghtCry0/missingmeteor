/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

public class AutoCraft extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPresets = settings.createGroup("Presets");
    private final SettingGroup sgCustom1 = settings.createGroup("Custom 1");
    private final SettingGroup sgCustom2 = settings.createGroup("Custom 2");
    private final SettingGroup sgCustom3 = settings.createGroup("Custom 3");
    private final SettingGroup sgCustom4 = settings.createGroup("Custom 4");
    private final SettingGroup sgCustom5 = settings.createGroup("Custom 5");

    // General

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between crafting operations.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> craftWhenInventoryOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("craft-in-inventory")
        .description("Also craft when player inventory is open (using 2x2 grid).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoCloseCrafting = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close")
        .description("Close the crafting table after all crafting is complete.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> shiftClick = sgGeneral.add(new BoolSetting.Builder()
        .name("shift-click")
        .description("Shift-click crafted items into inventory instead of picking them up.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxCraftsPerBatch = sgGeneral.add(new IntSetting.Builder()
        .name("max-crafts-per-batch")
        .description("Maximum crafts per recipe before moving to the next.")
        .defaultValue(64)
        .min(1)
        .sliderMax(64)
        .build()
    );

    // Preset Recipes

    private final Setting<Boolean> craftPlanks = sgPresets.add(new BoolSetting.Builder()
        .name("planks")
        .description("Craft planks from any available logs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftSticks = sgPresets.add(new BoolSetting.Builder()
        .name("sticks")
        .description("Craft sticks from planks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftCraftingTable = sgPresets.add(new BoolSetting.Builder()
        .name("crafting-table")
        .description("Craft crafting tables from planks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftChests = sgPresets.add(new BoolSetting.Builder()
        .name("chests")
        .description("Craft chests from planks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftFurnace = sgPresets.add(new BoolSetting.Builder()
        .name("furnace")
        .description("Craft furnaces from cobblestone.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftTorch = sgPresets.add(new BoolSetting.Builder()
        .name("torches")
        .description("Craft torches from coal and sticks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftIronBlock = sgPresets.add(new BoolSetting.Builder()
        .name("iron-block")
        .description("Craft iron blocks from iron ingots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftGoldBlock = sgPresets.add(new BoolSetting.Builder()
        .name("gold-block")
        .description("Craft gold blocks from gold ingots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftDiamondBlock = sgPresets.add(new BoolSetting.Builder()
        .name("diamond-block")
        .description("Craft diamond blocks from diamonds.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftBoneMeal = sgPresets.add(new BoolSetting.Builder()
        .name("bone-meal")
        .description("Craft bone meal from bones.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftBuckets = sgPresets.add(new BoolSetting.Builder()
        .name("buckets")
        .description("Craft buckets from iron ingots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftWool = sgPresets.add(new BoolSetting.Builder()
        .name("wool")
        .description("Craft wool from string.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftPaper = sgPresets.add(new BoolSetting.Builder()
        .name("paper")
        .description("Craft paper from sugar cane.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftBread = sgPresets.add(new BoolSetting.Builder()
        .name("bread")
        .description("Craft bread from wheat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftSandstone = sgPresets.add(new BoolSetting.Builder()
        .name("sandstone")
        .description("Craft sandstone from sand.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftIronIngots = sgPresets.add(new BoolSetting.Builder()
        .name("iron-ingots")
        .description("Craft iron ingots from iron blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftGoldIngots = sgPresets.add(new BoolSetting.Builder()
        .name("gold-ingots")
        .description("Craft gold ingots from gold blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftDiamonds = sgPresets.add(new BoolSetting.Builder()
        .name("diamonds")
        .description("Craft diamonds from diamond blocks.")
        .defaultValue(false)
        .build()
    );

    // ===== Custom Recipe 1 =====

    private final Setting<Boolean> custom1Enabled = sgCustom1.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable custom recipe 1.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Item> custom1_0 = sgCustom1.add(new ItemSetting.Builder().name("slot-0").description("Top-left grid slot.").defaultValue(Items.AIR).build());
    private final Setting<Item> custom1_1 = sgCustom1.add(new ItemSetting.Builder().name("slot-1").description("Top-center grid slot.").defaultValue(Items.AIR).build());
    private final Setting<Item> custom1_2 = sgCustom1.add(new ItemSetting.Builder().name("slot-2").description("Top-right grid slot.").defaultValue(Items.AIR).build());
    private final Setting<Item> custom1_3 = sgCustom1.add(new ItemSetting.Builder().name("slot-3").description("Mid-left grid slot.").defaultValue(Items.AIR).build());
    private final Setting<Item> custom1_4 = sgCustom1.add(new ItemSetting.Builder().name("slot-4").description("Center grid slot.").defaultValue(Items.AIR).build());
    private final Setting<Item> custom1_5 = sgCustom1.add(new ItemSetting.Builder().name("slot-5").description("Mid-right grid slot.").defaultValue(Items.AIR).build());
    private final Setting<Item> custom1_6 = sgCustom1.add(new ItemSetting.Builder().name("slot-6").description("Bot-left grid slot.").defaultValue(Items.AIR).build());
    private final Setting<Item> custom1_7 = sgCustom1.add(new ItemSetting.Builder().name("slot-7").description("Bot-center grid slot.").defaultValue(Items.AIR).build());
    private final Setting<Item> custom1_8 = sgCustom1.add(new ItemSetting.Builder().name("slot-8").description("Bot-right grid slot.").defaultValue(Items.AIR).build());

    // ===== Custom Recipe 2 =====

    private final Setting<Boolean> custom2Enabled = sgCustom2.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable custom recipe 2.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Item> custom2_0 = sgCustom2.add(new ItemSetting.Builder().name("slot-0").defaultValue(Items.AIR).build());
    private final Setting<Item> custom2_1 = sgCustom2.add(new ItemSetting.Builder().name("slot-1").defaultValue(Items.AIR).build());
    private final Setting<Item> custom2_2 = sgCustom2.add(new ItemSetting.Builder().name("slot-2").defaultValue(Items.AIR).build());
    private final Setting<Item> custom2_3 = sgCustom2.add(new ItemSetting.Builder().name("slot-3").defaultValue(Items.AIR).build());
    private final Setting<Item> custom2_4 = sgCustom2.add(new ItemSetting.Builder().name("slot-4").defaultValue(Items.AIR).build());
    private final Setting<Item> custom2_5 = sgCustom2.add(new ItemSetting.Builder().name("slot-5").defaultValue(Items.AIR).build());
    private final Setting<Item> custom2_6 = sgCustom2.add(new ItemSetting.Builder().name("slot-6").defaultValue(Items.AIR).build());
    private final Setting<Item> custom2_7 = sgCustom2.add(new ItemSetting.Builder().name("slot-7").defaultValue(Items.AIR).build());
    private final Setting<Item> custom2_8 = sgCustom2.add(new ItemSetting.Builder().name("slot-8").defaultValue(Items.AIR).build());

    // ===== Custom Recipe 3 =====

    private final Setting<Boolean> custom3Enabled = sgCustom3.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable custom recipe 3.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Item> custom3_0 = sgCustom3.add(new ItemSetting.Builder().name("slot-0").defaultValue(Items.AIR).build());
    private final Setting<Item> custom3_1 = sgCustom3.add(new ItemSetting.Builder().name("slot-1").defaultValue(Items.AIR).build());
    private final Setting<Item> custom3_2 = sgCustom3.add(new ItemSetting.Builder().name("slot-2").defaultValue(Items.AIR).build());
    private final Setting<Item> custom3_3 = sgCustom3.add(new ItemSetting.Builder().name("slot-3").defaultValue(Items.AIR).build());
    private final Setting<Item> custom3_4 = sgCustom3.add(new ItemSetting.Builder().name("slot-4").defaultValue(Items.AIR).build());
    private final Setting<Item> custom3_5 = sgCustom3.add(new ItemSetting.Builder().name("slot-5").defaultValue(Items.AIR).build());
    private final Setting<Item> custom3_6 = sgCustom3.add(new ItemSetting.Builder().name("slot-6").defaultValue(Items.AIR).build());
    private final Setting<Item> custom3_7 = sgCustom3.add(new ItemSetting.Builder().name("slot-7").defaultValue(Items.AIR).build());
    private final Setting<Item> custom3_8 = sgCustom3.add(new ItemSetting.Builder().name("slot-8").defaultValue(Items.AIR).build());

    // ===== Custom Recipe 4 =====

    private final Setting<Boolean> custom4Enabled = sgCustom4.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable custom recipe 4.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Item> custom4_0 = sgCustom4.add(new ItemSetting.Builder().name("slot-0").defaultValue(Items.AIR).build());
    private final Setting<Item> custom4_1 = sgCustom4.add(new ItemSetting.Builder().name("slot-1").defaultValue(Items.AIR).build());
    private final Setting<Item> custom4_2 = sgCustom4.add(new ItemSetting.Builder().name("slot-2").defaultValue(Items.AIR).build());
    private final Setting<Item> custom4_3 = sgCustom4.add(new ItemSetting.Builder().name("slot-3").defaultValue(Items.AIR).build());
    private final Setting<Item> custom4_4 = sgCustom4.add(new ItemSetting.Builder().name("slot-4").defaultValue(Items.AIR).build());
    private final Setting<Item> custom4_5 = sgCustom4.add(new ItemSetting.Builder().name("slot-5").defaultValue(Items.AIR).build());
    private final Setting<Item> custom4_6 = sgCustom4.add(new ItemSetting.Builder().name("slot-6").defaultValue(Items.AIR).build());
    private final Setting<Item> custom4_7 = sgCustom4.add(new ItemSetting.Builder().name("slot-7").defaultValue(Items.AIR).build());
    private final Setting<Item> custom4_8 = sgCustom4.add(new ItemSetting.Builder().name("slot-8").defaultValue(Items.AIR).build());

    // ===== Custom Recipe 5 =====

    private final Setting<Boolean> custom5Enabled = sgCustom5.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable custom recipe 5.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Item> custom5_0 = sgCustom5.add(new ItemSetting.Builder().name("slot-0").defaultValue(Items.AIR).build());
    private final Setting<Item> custom5_1 = sgCustom5.add(new ItemSetting.Builder().name("slot-1").defaultValue(Items.AIR).build());
    private final Setting<Item> custom5_2 = sgCustom5.add(new ItemSetting.Builder().name("slot-2").defaultValue(Items.AIR).build());
    private final Setting<Item> custom5_3 = sgCustom5.add(new ItemSetting.Builder().name("slot-3").defaultValue(Items.AIR).build());
    private final Setting<Item> custom5_4 = sgCustom5.add(new ItemSetting.Builder().name("slot-4").defaultValue(Items.AIR).build());
    private final Setting<Item> custom5_5 = sgCustom5.add(new ItemSetting.Builder().name("slot-5").defaultValue(Items.AIR).build());
    private final Setting<Item> custom5_6 = sgCustom5.add(new ItemSetting.Builder().name("slot-6").defaultValue(Items.AIR).build());
    private final Setting<Item> custom5_7 = sgCustom5.add(new ItemSetting.Builder().name("slot-7").defaultValue(Items.AIR).build());
    private final Setting<Item> custom5_8 = sgCustom5.add(new ItemSetting.Builder().name("slot-8").defaultValue(Items.AIR).build());

    // ===== State =====

    private int timer = 0;
    private int currentRecipeIndex = 0;
    private int craftsThisBatch = 0;
    private boolean craftingDone = false;

    public AutoCraft() {
        super(Categories.Player, "auto-craft", "Automatically crafts items when a crafting table is open. Supports custom recipes.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        currentRecipeIndex = 0;
        craftsThisBatch = 0;
        craftingDone = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ScreenHandler handler = mc.player.currentScreenHandler;

        boolean is3x3 = handler instanceof CraftingScreenHandler;
        boolean is2x2 = handler instanceof PlayerScreenHandler && craftWhenInventoryOpen.get();

        if (!is3x3 && !is2x2) return;

        if (craftingDone && autoCloseCrafting.get()) {
            mc.player.closeScreen();
            craftingDone = false;
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        if (craftsThisBatch >= maxCraftsPerBatch.get()) {
            craftsThisBatch = 0;
            currentRecipeIndex++;
            List<CraftRecipe> recipes = getEnabledRecipes();
            if (currentRecipeIndex >= recipes.size()) {
                currentRecipeIndex = 0;
                craftingDone = true;
            }
            timer = delay.get();
            return;
        }

        List<CraftRecipe> recipes = getEnabledRecipes();

        if (recipes.isEmpty()) {
            craftingDone = true;
            return;
        }

        if (currentRecipeIndex >= recipes.size()) currentRecipeIndex = 0;

        CraftRecipe recipe = recipes.get(currentRecipeIndex);

        if (!is3x3 && recipe.requires3x3) {
            currentRecipeIndex++;
            if (currentRecipeIndex >= recipes.size()) {
                currentRecipeIndex = 0;
                craftingDone = true;
            }
            return;
        }

        if (!hasIngredients(recipe)) {
            currentRecipeIndex++;
            if (currentRecipeIndex >= recipes.size()) {
                currentRecipeIndex = 0;
                craftingDone = true;
            }
            return;
        }

        if (!clearCraftingGrid(is3x3)) {
            timer = delay.get();
            return;
        }

        if (is3x3) {
            placeIngredients3x3(recipe);
        } else {
            placeIngredients2x2(recipe);
        }

        timer = delay.get();

        if (handler.slots.size() > 0) {
            ItemStack result = handler.slots.get(0).getStack();
            if (!result.isEmpty()) {
                if (shiftClick.get()) {
                    mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
                } else {
                    mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
                    int targetSlot = findEmptyInventorySlot();
                    if (targetSlot != -1) {
                        int targetId = SlotUtils.indexToId(targetSlot);
                        mc.interactionManager.clickSlot(handler.syncId, targetId, 0, SlotActionType.PICKUP, mc.player);
                    }
                }
                craftsThisBatch++;
            }
        }
    }

    // ===== Inventory Helpers =====

    private boolean hasIngredients(CraftRecipe recipe) {
        for (RecipeIngredient ingredient : recipe.ingredients) {
            if (ingredient.item == Items.AIR) continue;
            if (countItem(ingredient.item) < ingredient.count) return false;
        }
        return true;
    }

    private int countItem(Item item) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private int findItemSlot(Item item) {
        for (int i = mc.player.getInventory().size() - 1; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item && stack.getCount() > 0) return i;
        }
        return -1;
    }

    private int findEmptyInventorySlot() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean clearCraftingGrid(boolean is3x3) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        int gridSize = is3x3 ? 9 : 4;
        for (int i = 0; i < gridSize; i++) {
            int slotId = i + 1;
            if (slotId >= handler.slots.size()) continue;
            ItemStack stack = handler.slots.get(slotId).getStack();
            if (!stack.isEmpty()) {
                mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                return false;
            }
        }
        return true;
    }

    private void placeIngredients3x3(CraftRecipe recipe) {
        for (RecipeIngredient ingredient : recipe.ingredients) {
            if (ingredient.item == Items.AIR) continue;
            int slotId = ingredient.gridSlot3x3 + 1;
            int invSlot = findItemSlot(ingredient.item);
            if (invSlot == -1) return;
            int invSlotId = SlotUtils.indexToId(invSlot);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, invSlotId, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private void placeIngredients2x2(CraftRecipe recipe) {
        for (RecipeIngredient ingredient : recipe.ingredients) {
            if (ingredient.item == Items.AIR) continue;
            int slot2x2 = ingredient.getGridSlot2x2();
            if (slot2x2 == -1) return; // Can't place in 2x2
            int slotId = slot2x2 + 1;
            int invSlot = findItemSlot(ingredient.item);
            if (invSlot == -1) return;
            int invSlotId = SlotUtils.indexToId(invSlot);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, invSlotId, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    // ===== Recipe Collection =====

    private List<CraftRecipe> getEnabledRecipes() {
        List<CraftRecipe> recipes = new ArrayList<>();

        // Presets
        if (craftPlanks.get()) recipes.addAll(getPlanksRecipes());
        if (craftSticks.get()) recipes.add(STICKS);
        if (craftCraftingTable.get()) recipes.add(CRAFTING_TABLE);
        if (craftChests.get()) recipes.add(CHESTS);
        if (craftFurnace.get()) recipes.add(FURNACE);
        if (craftTorch.get()) recipes.add(TORCH);
        if (craftIronBlock.get()) recipes.add(IRON_BLOCK);
        if (craftGoldBlock.get()) recipes.add(GOLD_BLOCK);
        if (craftDiamondBlock.get()) recipes.add(DIAMOND_BLOCK);
        if (craftBoneMeal.get()) recipes.add(BONE_MEAL);
        if (craftBuckets.get()) recipes.add(BUCKETS);
        if (craftWool.get()) recipes.add(WOOL);
        if (craftPaper.get()) recipes.add(PAPER);
        if (craftBread.get()) recipes.add(BREAD);
        if (craftSandstone.get()) recipes.add(SANDSTONE);
        if (craftIronIngots.get()) recipes.add(IRON_INGOTS_FROM_BLOCK);
        if (craftGoldIngots.get()) recipes.add(GOLD_INGOTS_FROM_BLOCK);
        if (craftDiamonds.get()) recipes.add(DIAMONDS_FROM_BLOCK);

        // Custom recipes
        if (custom1Enabled.get()) recipes.add(buildCustomRecipe("Custom 1",
            custom1_0.get(), custom1_1.get(), custom1_2.get(),
            custom1_3.get(), custom1_4.get(), custom1_5.get(),
            custom1_6.get(), custom1_7.get(), custom1_8.get()
        ));
        if (custom2Enabled.get()) recipes.add(buildCustomRecipe("Custom 2",
            custom2_0.get(), custom2_1.get(), custom2_2.get(),
            custom2_3.get(), custom2_4.get(), custom2_5.get(),
            custom2_6.get(), custom2_7.get(), custom2_8.get()
        ));
        if (custom3Enabled.get()) recipes.add(buildCustomRecipe("Custom 3",
            custom3_0.get(), custom3_1.get(), custom3_2.get(),
            custom3_3.get(), custom3_4.get(), custom3_5.get(),
            custom3_6.get(), custom3_7.get(), custom3_8.get()
        ));
        if (custom4Enabled.get()) recipes.add(buildCustomRecipe("Custom 4",
            custom4_0.get(), custom4_1.get(), custom4_2.get(),
            custom4_3.get(), custom4_4.get(), custom4_5.get(),
            custom4_6.get(), custom4_7.get(), custom4_8.get()
        ));
        if (custom5Enabled.get()) recipes.add(buildCustomRecipe("Custom 5",
            custom5_0.get(), custom5_1.get(), custom5_2.get(),
            custom5_3.get(), custom5_4.get(), custom5_5.get(),
            custom5_6.get(), custom5_7.get(), custom5_8.get()
        ));

        return recipes;
    }

    /**
     * Builds a CraftRecipe from 9 item settings representing a 3x3 grid.
     * The grid layout matches the in-game crafting table:
     *  0 1 2
     *  3 4 5
     *  6 7 8
     * Any slot set to AIR is treated as empty.
     */
    private CraftRecipe buildCustomRecipe(String name, Item s0, Item s1, Item s2, Item s3, Item s4, Item s5, Item s6, Item s7, Item s8) {
        Item[] grid = {s0, s1, s2, s3, s4, s5, s6, s7, s8};
        boolean requires3x3 = usesSlotOutside2x2(grid);
        List<RecipeIngredient> ingredients = new ArrayList<>();

        for (int i = 0; i < 9; i++) {
            if (grid[i] != Items.AIR) {
                ingredients.add(new RecipeIngredient(grid[i], 1, i));
            }
        }

        return new CraftRecipe(name, requires3x3, ingredients.toArray(new RecipeIngredient[0]));
    }

    private boolean usesSlotOutside2x2(Item[] grid) {
        // 2x2 only uses slots 0,1,3,4 of the 3x3 grid
        for (int i = 0; i < 9; i++) {
            if (i == 2 || i == 5 || i == 6 || i == 7 || i == 8) {
                if (grid[i] != Items.AIR) return true;
            }
        }
        return false;
    }

    // ===== Planks Preset Recipes =====

    private List<CraftRecipe> getPlanksRecipes() {
        List<CraftRecipe> recipes = new ArrayList<>();
        recipes.add(new CraftRecipe("Oak Planks", new RecipeIngredient(Items.OAK_LOG, 1, 4)));
        recipes.add(new CraftRecipe("Spruce Planks", new RecipeIngredient(Items.SPRUCE_LOG, 1, 4)));
        recipes.add(new CraftRecipe("Birch Planks", new RecipeIngredient(Items.BIRCH_LOG, 1, 4)));
        recipes.add(new CraftRecipe("Jungle Planks", new RecipeIngredient(Items.JUNGLE_LOG, 1, 4)));
        recipes.add(new CraftRecipe("Acacia Planks", new RecipeIngredient(Items.ACACIA_LOG, 1, 4)));
        recipes.add(new CraftRecipe("Dark Oak Planks", new RecipeIngredient(Items.DARK_OAK_LOG, 1, 4)));
        recipes.add(new CraftRecipe("Mangrove Planks", new RecipeIngredient(Items.MANGROVE_LOG, 1, 4)));
        recipes.add(new CraftRecipe("Cherry Planks", new RecipeIngredient(Items.CHERRY_LOG, 1, 4)));
        recipes.add(new CraftRecipe("Bamboo Planks", new RecipeIngredient(Items.BAMBOO_BLOCK, 1, 4)));
        recipes.add(new CraftRecipe("Crimson Planks", new RecipeIngredient(Items.CRIMSON_STEM, 1, 4)));
        recipes.add(new CraftRecipe("Warped Planks", new RecipeIngredient(Items.WARPED_STEM, 1, 4)));
        return recipes;
    }

    // ===== Preset Recipe Definitions =====

    // Grid layout:
    // 0 1 2
    // 3 4 5
    // 6 7 8

    private static final CraftRecipe STICKS = new CraftRecipe("Sticks",
        new RecipeIngredient(Items.OAK_PLANKS, 1, 0),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 1),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 3),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 4)
    );

    private static final CraftRecipe CRAFTING_TABLE = new CraftRecipe("Crafting Table", true,
        new RecipeIngredient(Items.OAK_PLANKS, 1, 0), new RecipeIngredient(Items.OAK_PLANKS, 1, 1), new RecipeIngredient(Items.OAK_PLANKS, 1, 2),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 3), new RecipeIngredient(Items.OAK_PLANKS, 1, 4), new RecipeIngredient(Items.OAK_PLANKS, 1, 5),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 6), new RecipeIngredient(Items.OAK_PLANKS, 1, 7), new RecipeIngredient(Items.OAK_PLANKS, 1, 8)
    );

    private static final CraftRecipe CHESTS = new CraftRecipe("Chests", true,
        new RecipeIngredient(Items.OAK_PLANKS, 1, 0), new RecipeIngredient(Items.OAK_PLANKS, 1, 1), new RecipeIngredient(Items.OAK_PLANKS, 1, 2),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 3), new RecipeIngredient(Items.OAK_PLANKS, 1, 4), new RecipeIngredient(Items.OAK_PLANKS, 1, 5),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 6), new RecipeIngredient(Items.OAK_PLANKS, 1, 7), new RecipeIngredient(Items.OAK_PLANKS, 1, 8)
    );

    private static final CraftRecipe FURNACE = new CraftRecipe("Furnace", true,
        new RecipeIngredient(Items.COBBLESTONE, 1, 0), new RecipeIngredient(Items.COBBLESTONE, 1, 1), new RecipeIngredient(Items.COBBLESTONE, 1, 2),
        new RecipeIngredient(Items.COBBLESTONE, 1, 3), new RecipeIngredient(Items.COBBLESTONE, 1, 4), new RecipeIngredient(Items.COBBLESTONE, 1, 5),
        new RecipeIngredient(Items.COBBLESTONE, 1, 6), new RecipeIngredient(Items.COBBLESTONE, 1, 7), new RecipeIngredient(Items.COBBLESTONE, 1, 8)
    );

    private static final CraftRecipe TORCH = new CraftRecipe("Torches",
        new RecipeIngredient(Items.COAL, 1, 0),
        new RecipeIngredient(Items.STICK, 1, 3)
    );

    private static final CraftRecipe IRON_BLOCK = new CraftRecipe("Iron Block", true,
        new RecipeIngredient(Items.IRON_INGOT, 1, 0), new RecipeIngredient(Items.IRON_INGOT, 1, 1), new RecipeIngredient(Items.IRON_INGOT, 1, 2),
        new RecipeIngredient(Items.IRON_INGOT, 1, 3), new RecipeIngredient(Items.IRON_INGOT, 1, 4), new RecipeIngredient(Items.IRON_INGOT, 1, 5),
        new RecipeIngredient(Items.IRON_INGOT, 1, 6), new RecipeIngredient(Items.IRON_INGOT, 1, 7), new RecipeIngredient(Items.IRON_INGOT, 1, 8)
    );

    private static final CraftRecipe GOLD_BLOCK = new CraftRecipe("Gold Block", true,
        new RecipeIngredient(Items.GOLD_INGOT, 1, 0), new RecipeIngredient(Items.GOLD_INGOT, 1, 1), new RecipeIngredient(Items.GOLD_INGOT, 1, 2),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 3), new RecipeIngredient(Items.GOLD_INGOT, 1, 4), new RecipeIngredient(Items.GOLD_INGOT, 1, 5),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 6), new RecipeIngredient(Items.GOLD_INGOT, 1, 7), new RecipeIngredient(Items.GOLD_INGOT, 1, 8)
    );

    private static final CraftRecipe DIAMOND_BLOCK = new CraftRecipe("Diamond Block", true,
        new RecipeIngredient(Items.DIAMOND, 1, 0), new RecipeIngredient(Items.DIAMOND, 1, 1), new RecipeIngredient(Items.DIAMOND, 1, 2),
        new RecipeIngredient(Items.DIAMOND, 1, 3), new RecipeIngredient(Items.DIAMOND, 1, 4), new RecipeIngredient(Items.DIAMOND, 1, 5),
        new RecipeIngredient(Items.DIAMOND, 1, 6), new RecipeIngredient(Items.DIAMOND, 1, 7), new RecipeIngredient(Items.DIAMOND, 1, 8)
    );

    private static final CraftRecipe BONE_MEAL = new CraftRecipe("Bone Meal",
        new RecipeIngredient(Items.BONE, 1, 4)
    );

    private static final CraftRecipe BUCKETS = new CraftRecipe("Buckets",
        new RecipeIngredient(Items.IRON_INGOT, 1, 3), new RecipeIngredient(Items.IRON_INGOT, 1, 4),
        new RecipeIngredient(Items.IRON_INGOT, 1, 6), new RecipeIngredient(Items.IRON_INGOT, 1, 7)
    );

    private static final CraftRecipe WOOL = new CraftRecipe("Wool",
        new RecipeIngredient(Items.STRING, 1, 3), new RecipeIngredient(Items.STRING, 1, 4), new RecipeIngredient(Items.STRING, 1, 5),
        new RecipeIngredient(Items.STRING, 1, 6), new RecipeIngredient(Items.STRING, 1, 7), new RecipeIngredient(Items.STRING, 1, 8)
    );

    private static final CraftRecipe PAPER = new CraftRecipe("Paper",
        new RecipeIngredient(Items.SUGAR_CANE, 1, 6), new RecipeIngredient(Items.SUGAR_CANE, 1, 7), new RecipeIngredient(Items.SUGAR_CANE, 1, 8)
    );

    private static final CraftRecipe BREAD = new CraftRecipe("Bread",
        new RecipeIngredient(Items.WHEAT, 1, 6), new RecipeIngredient(Items.WHEAT, 1, 7), new RecipeIngredient(Items.WHEAT, 1, 8)
    );

    private static final CraftRecipe SANDSTONE = new CraftRecipe("Sandstone",
        new RecipeIngredient(Items.SAND, 1, 0), new RecipeIngredient(Items.SAND, 1, 1), new RecipeIngredient(Items.SAND, 1, 2),
        new RecipeIngredient(Items.SAND, 1, 3), new RecipeIngredient(Items.SAND, 1, 4), new RecipeIngredient(Items.SAND, 1, 5)
    );

    private static final CraftRecipe IRON_INGOTS_FROM_BLOCK = new CraftRecipe("Iron Ingots", true,
        new RecipeIngredient(Items.IRON_BLOCK, 1, 4)
    );

    private static final CraftRecipe GOLD_INGOTS_FROM_BLOCK = new CraftRecipe("Gold Ingots", true,
        new RecipeIngredient(Items.GOLD_BLOCK, 1, 4)
    );

    private static final CraftRecipe DIAMONDS_FROM_BLOCK = new CraftRecipe("Diamonds", true,
        new RecipeIngredient(Items.DIAMOND_BLOCK, 1, 4)
    );

    // ===== Helper Classes =====

    private static class CraftRecipe {
        final String name;
        final RecipeIngredient[] ingredients;
        final boolean requires3x3;

        CraftRecipe(String name, RecipeIngredient... ingredients) {
            this(name, false, ingredients);
        }

        CraftRecipe(String name, boolean requires3x3, RecipeIngredient... ingredients) {
            this.name = name;
            this.requires3x3 = requires3x3;
            this.ingredients = ingredients;
        }
    }

    private static class RecipeIngredient {
        final Item item;
        final int count;
        final int gridSlot3x3;

        RecipeIngredient(Item item, int count, int gridSlot3x3) {
            this.item = item;
            this.count = count;
            this.gridSlot3x3 = gridSlot3x3;
        }

        int getGridSlot2x2() {
            return switch (gridSlot3x3) {
                case 0 -> 0;
                case 1 -> 1;
                case 3 -> 2;
                case 4 -> 3;
                default -> -1;
            };
        }
    }
}

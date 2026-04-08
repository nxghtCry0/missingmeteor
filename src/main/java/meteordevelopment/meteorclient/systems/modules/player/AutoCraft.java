/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

public class AutoCraft extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRecipes = settings.createGroup("Recipes");

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
        .description("Close the crafting table after crafting is complete.")
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
        .description("Maximum crafts per recipe before re-checking all recipes.")
        .defaultValue(64)
        .min(1)
        .sliderMax(64)
        .build()
    );

    // Recipes

    private final Setting<Boolean> craftPlanks = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-planks")
        .description("Automatically craft planks from logs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftSticks = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-sticks")
        .description("Automatically craft sticks from planks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftCraftingTable = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-crafting-table")
        .description("Automatically craft crafting tables from planks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftChests = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-chests")
        .description("Automatically craft chests from planks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftFurnace = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-furnace")
        .description("Automatically craft furnaces from cobblestone.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftTorch = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-torches")
        .description("Automatically craft torches from coal and sticks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftIronBlock = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-iron-block")
        .description("Automatically craft iron blocks from iron ingots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftGoldBlock = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-gold-block")
        .description("Automatically craft gold blocks from gold ingots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftDiamondBlock = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-diamond-block")
        .description("Automatically craft diamond blocks from diamonds.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftBoneMeal = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-bone-meal")
        .description("Automatically craft bone meal from bones.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftLeather = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-leather")
        .description("Automatically craft leather from rabbit hides.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftBuckets = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-buckets")
        .description("Automatically craft buckets from iron ingots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craft Wool = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-wool")
        .description("Automatically craft wool from string.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftPaper = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-paper")
        .description("Automatically craft paper from sugar cane.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftBread = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-bread")
        .description("Automatically craft bread from wheat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftSnowballs = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-snowballs")
        .description("Automatically craft snowballs from snow blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftSandstone = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-sandstone")
        .description("Automatically craft sandstone from sand.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftIronIngots = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-iron-ingots")
        .description("Automatically craft iron ingots from iron blocks (requires crafting table).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftGoldIngots = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-gold-ingots")
        .description("Automatically craft gold ingots from gold blocks (requires crafting table).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftDiamonds = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-diamonds")
        .description("Automatically craft diamonds from diamond blocks (requires crafting table).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> craftQuartzBlock = sgRecipes.add(new BoolSetting.Builder()
        .name("craft-quartz-block")
        .description("Automatically craft quartz blocks from quartz.")
        .defaultValue(false)
        .build()
    );

    // Crafting table grid slot IDs (in CraftingScreenHandler, these are slot IDs 1-9)
    // The grid layout is:
    // 0 1 2
    // 3 4 5
    // 6 7 8  -> slot IDs 1,2,3,4,5,6,7,8,9
    // Result slot is ID 0

    private int timer = 0;
    private int currentRecipeIndex = 0;
    private int craftsThisBatch = 0;
    private boolean craftingDone = false;

    public AutoCraft() {
        super(Categories.Player, "auto-craft", "Automatically crafts configured items when a crafting table is open.");
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

        // Check if we have a valid crafting screen open
        boolean is3x3 = handler instanceof CraftingScreenHandler;
        boolean is2x2 = handler instanceof PlayerScreenHandler && craftWhenInventoryOpen.get();

        if (!is3x3 && !is2x2) return;

        if (craftingDone && autoCloseCrafting.get()) {
            mc.player.closeScreen();
            craftingDone = false;
            return;
        }

        // Delay
        if (timer > 0) {
            timer--;
            return;
        }

        // Reset batch counter
        if (craftsThisBatch >= maxCraftsPerBatch.get()) {
            craftsThisBatch = 0;
            currentRecipeIndex++;
            if (currentRecipeIndex >= getEnabledRecipes().size()) {
                currentRecipeIndex = 0;
                craftingDone = true;
            }
            timer = delay.get();
            return;
        }

        // Try each enabled recipe
        List<CraftRecipe> recipes = getEnabledRecipes();

        if (recipes.isEmpty()) {
            craftingDone = true;
            return;
        }

        if (currentRecipeIndex >= recipes.size()) currentRecipeIndex = 0;

        CraftRecipe recipe = recipes.get(currentRecipeIndex);

        // Check if recipe needs 3x3 grid
        if (!is3x3 && recipe.requires3x3) {
            currentRecipeIndex++;
            if (currentRecipeIndex >= recipes.size()) {
                currentRecipeIndex = 0;
                craftingDone = true;
            }
            return;
        }

        // Check if we have enough ingredients
        if (!hasIngredients(recipe)) {
            currentRecipeIndex++;
            if (currentRecipeIndex >= recipes.size()) {
                currentRecipeIndex = 0;
                craftingDone = true;
            }
            return;
        }

        // Clear the crafting grid first
        if (!clearCraftingGrid(is3x3)) {
            timer = delay.get();
            return;
        }

        // Place ingredients
        if (is3x3) {
            placeIngredients3x3(recipe);
        } else {
            placeIngredients2x2(recipe);
        }

        // Pick up or shift-click the result
        timer = delay.get();

        // Small delay for the server to process the craft
        if (handler.slots.size() > 0) {
            ItemStack result = handler.slots.get(0).getStack();
            if (!result.isEmpty()) {
                if (shiftClick.get()) {
                    // Shift-click result slot (slot ID 0)
                    mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
                } else {
                    // Pick up result, then drop it into inventory
                    mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
                    // Place into an inventory slot
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
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int findItemSlot(Item item) {
        for (int i = mc.player.getInventory().size() - 1; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item && stack.getCount() > 0) {
                return i;
            }
        }
        return -1;
    }

    private int findEmptyInventorySlot() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return i;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) return i;
        }
        return -1;
    }

    private boolean clearCraftingGrid(boolean is3x3) {
        ScreenHandler handler = mc.player.currentScreenHandler;

        // Check each crafting grid slot for items
        int gridSize = is3x3 ? 9 : 4;
        for (int i = 0; i < gridSize; i++) {
            // Slot ID for crafting grid: 1-9 for 3x3, 1-4 for 2x2
            int slotId = i + 1;
            if (slotId >= handler.slots.size()) continue;

            ItemStack stack = handler.slots.get(slotId).getStack();
            if (!stack.isEmpty()) {
                // Shift-click to return item to inventory
                mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
                return false; // Return and wait next tick for the grid to clear
            }
        }
        return true;
    }

    private void placeIngredients3x3(CraftRecipe recipe) {
        for (RecipeIngredient ingredient : recipe.ingredients) {
            if (ingredient.item == Items.AIR) continue;
            int slotId = ingredient.gridSlot3x3 + 1; // +1 because result is slot 0
            int invSlot = findItemSlot(ingredient.item);
            if (invSlot == -1) return;

            int invSlotId = SlotUtils.indexToId(invSlot);

            // Move item from inventory to crafting grid
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, invSlotId, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private void placeIngredients2x2(CraftRecipe recipe) {
        // 2x2 grid uses top-left of the 3x3 grid layout
        for (RecipeIngredient ingredient : recipe.ingredients) {
            if (ingredient.item == Items.AIR) continue;
            int slotId = ingredient.gridSlot2x2 + 1; // +1 because result is slot 0
            int invSlot = findItemSlot(ingredient.item);
            if (invSlot == -1) return;

            int invSlotId = SlotUtils.indexToId(invSlot);

            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, invSlotId, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private List<CraftRecipe> getEnabledRecipes() {
        List<CraftRecipe> recipes = new ArrayList<>();
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
        if (craftLeather.get()) recipes.add(LEATHER);
        if (craftBuckets.get()) recipes.add(BUCKETS);
        if (craftWool.get()) recipes.add(WOOL);
        if (craftPaper.get()) recipes.add(PAPER);
        if (craftBread.get()) recipes.add(BREAD);
        if (craftSnowballs.get()) recipes.add(SNOWBALLS);
        if (craftSandstone.get()) recipes.add(SANDSTONE);
        if (craftIronIngots.get()) recipes.add(IRON_INGOTS);
        if (craftGoldIngots.get()) recipes.add(GOLD_INGOTS);
        if (craftDiamonds.get()) recipes.add(DIAMONDS);
        if (craftQuartzBlock.get()) recipes.add(QUARTZ_BLOCK);
        return recipes;
    }

    private List<CraftRecipe> getPlanksRecipes() {
        List<CraftRecipe> recipes = new ArrayList<>();
        recipes.add(OAK_PLANKS);
        recipes.add(SPRUCE_PLANKS);
        recipes.add(BIRCH_PLANKS);
        recipes.add(JUNGLE_PLANKS);
        recipes.add(ACACIA_PLANKS);
        recipes.add(DARK_OAK_PLANKS);
        recipes.add(MANGROVE_PLANKS);
        recipes.add(CHERRY_PLANKS);
        recipes.add(BAMBOO_PLANKS);
        recipes.add(CRIMSON_PLANKS);
        recipes.add(WARPED_PLANKS);
        return recipes;
    }

    // ===== Recipe Definitions =====

    // Helper to create a single-ingredient recipe for planks (1 log -> 4 planks)
    private static CraftRecipe makePlanksRecipe(Item log, Item planks, String name) {
        return new CraftRecipe(name,
            new RecipeIngredient(log, 1, 4), // center slot (index 4), any slot in 2x2
            false
        );
    }

    // 3x3 grid slot indices:
    // 0 1 2
    // 3 4 5
    // 6 7 8

    private static final CraftRecipe STICKS = new CraftRecipe("Sticks",
        new RecipeIngredient(Items.OAK_PLANKS, 1, 0),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 1),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 3),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 4),
        false
    );

    private static final CraftRecipe CRAFTING_TABLE = new CraftRecipe("Crafting Table",
        new RecipeIngredient(Items.OAK_PLANKS, 1, 0),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 1),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 2),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 3),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 4),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 5),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 6),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 7),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 8),
        true
    );

    private static final CraftRecipe CHESTS = new CraftRecipe("Chests",
        new RecipeIngredient(Items.OAK_PLANKS, 1, 0),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 1),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 2),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 3),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 4),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 5),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 6),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 7),
        new RecipeIngredient(Items.OAK_PLANKS, 1, 8),
        true
    );

    private static final CraftRecipe FURNACE = new CraftRecipe("Furnace",
        new RecipeIngredient(Items.COBBLESTONE, 1, 0),
        new RecipeIngredient(Items.COBBLESTONE, 1, 1),
        new RecipeIngredient(Items.COBBLESTONE, 1, 2),
        new RecipeIngredient(Items.COBBLESTONE, 1, 3),
        new RecipeIngredient(Items.COBBLESTONE, 1, 4),
        new RecipeIngredient(Items.COBBLESTONE, 1, 5),
        new RecipeIngredient(Items.COBBLESTONE, 1, 6),
        new RecipeIngredient(Items.COBBLESTONE, 1, 7),
        new RecipeIngredient(Items.COBBLESTONE, 1, 8),
        true
    );

    private static final CraftRecipe TORCH = new CraftRecipe("Torches",
        new RecipeIngredient(Items.COAL, 1, 0),
        new RecipeIngredient(Items.STICK, 1, 3),
        false
    );

    private static final CraftRecipe IRON_BLOCK = new CraftRecipe("Iron Block",
        new RecipeIngredient(Items.IRON_INGOT, 1, 0),
        new RecipeIngredient(Items.IRON_INGOT, 1, 1),
        new RecipeIngredient(Items.IRON_INGOT, 1, 2),
        new RecipeIngredient(Items.IRON_INGOT, 1, 3),
        new RecipeIngredient(Items.IRON_INGOT, 1, 4),
        new RecipeIngredient(Items.IRON_INGOT, 1, 5),
        new RecipeIngredient(Items.IRON_INGOT, 1, 6),
        new RecipeIngredient(Items.IRON_INGOT, 1, 7),
        new RecipeIngredient(Items.IRON_INGOT, 1, 8),
        true
    );

    private static final CraftRecipe GOLD_BLOCK = new CraftRecipe("Gold Block",
        new RecipeIngredient(Items.GOLD_INGOT, 1, 0),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 1),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 2),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 3),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 4),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 5),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 6),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 7),
        new RecipeIngredient(Items.GOLD_INGOT, 1, 8),
        true
    );

    private static final CraftRecipe DIAMOND_BLOCK = new CraftRecipe("Diamond Block",
        new RecipeIngredient(Items.DIAMOND, 1, 0),
        new RecipeIngredient(Items.DIAMOND, 1, 1),
        new RecipeIngredient(Items.DIAMOND, 1, 2),
        new RecipeIngredient(Items.DIAMOND, 1, 3),
        new RecipeIngredient(Items.DIAMOND, 1, 4),
        new RecipeIngredient(Items.DIAMOND, 1, 5),
        new RecipeIngredient(Items.DIAMOND, 1, 6),
        new RecipeIngredient(Items.DIAMOND, 1, 7),
        new RecipeIngredient(Items.DIAMOND, 1, 8),
        true
    );

    private static final CraftRecipe BONE_MEAL = new CraftRecipe("Bone Meal",
        new RecipeIngredient(Items.BONE, 1, 4),
        false
    );

    private static final CraftRecipe LEATHER = new CraftRecipe("Leather",
        new RecipeIngredient(Items.RABBIT_HIDE, 1, 0),
        new RecipeIngredient(Items.RABBIT_HIDE, 1, 1),
        new RecipeIngredient(Items.RABBIT_HIDE, 1, 2),
        new RecipeIngredient(Items.RABBIT_HIDE, 1, 3),
        new RecipeIngredient(Items.RABBIT_HIDE, 1, 4),
        new RecipeIngredient(Items.RABBIT_HIDE, 1, 5),
        new RecipeIngredient(Items.RABBIT_HIDE, 1, 6),
        new RecipeIngredient(Items.RABBIT_HIDE, 1, 7),
        new RecipeIngredient(Items.RABBIT_HIDE, 1, 8),
        true
    );

    private static final CraftRecipe BUCKETS = new CraftRecipe("Buckets",
        new RecipeIngredient(Items.IRON_INGOT, 1, 3),
        new RecipeIngredient(Items.IRON_INGOT, 1, 4),
        new RecipeIngredient(Items.IRON_INGOT, 1, 6),
        new RecipeIngredient(Items.IRON_INGOT, 1, 7),
        false
    );

    private static final CraftRecipe WOOL = new CraftRecipe("Wool",
        new RecipeIngredient(Items.STRING, 1, 3),
        new RecipeIngredient(Items.STRING, 1, 4),
        new RecipeIngredient(Items.STRING, 1, 5),
        new RecipeIngredient(Items.STRING, 1, 6),
        new RecipeIngredient(Items.STRING, 1, 7),
        new RecipeIngredient(Items.STRING, 1, 8),
        false
    );

    private static final CraftRecipe PAPER = new CraftRecipe("Paper",
        new RecipeIngredient(Items.SUGAR_CANE, 1, 6),
        new RecipeIngredient(Items.SUGAR_CANE, 1, 7),
        new RecipeIngredient(Items.SUGAR_CANE, 1, 8),
        false
    );

    private static final CraftRecipe BREAD = new CraftRecipe("Bread",
        new RecipeIngredient(Items.WHEAT, 1, 6),
        new RecipeIngredient(Items.WHEAT, 1, 7),
        new RecipeIngredient(Items.WHEAT, 1, 8),
        false
    );

    private static final CraftRecipe SNOWBALLS = new CraftRecipe("Snowballs",
        new RecipeIngredient(Items.SNOW_BLOCK, 1, 0),
        new RecipeIngredient(Items.SNOW_BLOCK, 1, 1),
        new RecipeIngredient(Items.SNOW_BLOCK, 1, 2),
        new RecipeIngredient(Items.SNOW_BLOCK, 1, 3),
        new RecipeIngredient(Items.SNOW_BLOCK, 1, 4),
        new RecipeIngredient(Items.SNOW_BLOCK, 1, 5),
        false
    );

    private static final CraftRecipe SANDSTONE = new CraftRecipe("Sandstone",
        new RecipeIngredient(Items.SAND, 1, 0),
        new RecipeIngredient(Items.SAND, 1, 1),
        new RecipeIngredient(Items.SAND, 1, 2),
        new RecipeIngredient(Items.SAND, 1, 3),
        new RecipeIngredient(Items.SAND, 1, 4),
        new RecipeIngredient(Items.SAND, 1, 5),
        false
    );

    private static final CraftRecipe IRON_INGOTS = new CraftRecipe("Iron Ingots",
        new RecipeIngredient(Items.IRON_BLOCK, 1, 4),
        true
    );

    private static final CraftRecipe GOLD_INGOTS = new CraftRecipe("Gold Ingots",
        new RecipeIngredient(Items.GOLD_BLOCK, 1, 4),
        true
    );

    private static final CraftRecipe DIAMONDS = new CraftRecipe("Diamonds",
        new RecipeIngredient(Items.DIAMOND_BLOCK, 1, 4),
        true
    );

    private static final CraftRecipe QUARTZ_BLOCK = new CraftRecipe("Quartz Block",
        new RecipeIngredient(Items.QUARTZ, 1, 0),
        new RecipeIngredient(Items.QUARTZ, 1, 1),
        new RecipeIngredient(Items.QUARTZ, 1, 2),
        new RecipeIngredient(Items.QUARTZ, 1, 3),
        new RecipeIngredient(Items.QUARTZ, 1, 4),
        new RecipeIngredient(Items.QUARTZ, 1, 5),
        new RecipeIngredient(Items.QUARTZ, 1, 6),
        new RecipeIngredient(Items.QUARTZ, 1, 7),
        new RecipeIngredient(Items.QUARTZ, 1, 8),
        true
    );

    // Planks recipes (all wood types)
    private static final CraftRecipe OAK_PLANKS = new CraftRecipe("Oak Planks",
        new RecipeIngredient(Items.OAK_LOG, 1, 4), false
    );
    private static final CraftRecipe SPRUCE_PLANKS = new CraftRecipe("Spruce Planks",
        new RecipeIngredient(Items.SPRUCE_LOG, 1, 4), false
    );
    private static final CraftRecipe BIRCH_PLANKS = new CraftRecipe("Birch Planks",
        new RecipeIngredient(Items.BIRCH_LOG, 1, 4), false
    );
    private static final CraftRecipe JUNGLE_PLANKS = new CraftRecipe("Jungle Planks",
        new RecipeIngredient(Items.JUNGLE_LOG, 1, 4), false
    );
    private static final CraftRecipe ACACIA_PLANKS = new CraftRecipe("Acacia Planks",
        new RecipeIngredient(Items.ACACIA_LOG, 1, 4), false
    );
    private static final CraftRecipe DARK_OAK_PLANKS = new CraftRecipe("Dark Oak Planks",
        new RecipeIngredient(Items.DARK_OAK_LOG, 1, 4), false
    );
    private static final CraftRecipe MANGROVE_PLANKS = new CraftRecipe("Mangrove Planks",
        new RecipeIngredient(Items.MANGROVE_LOG, 1, 4), false
    );
    private static final CraftRecipe CHERRY_PLANKS = new CraftRecipe("Cherry Planks",
        new RecipeIngredient(Items.CHERRY_LOG, 1, 4), false
    );
    private static final CraftRecipe BAMBOO_PLANKS = new CraftRecipe("Bamboo Planks",
        new RecipeIngredient(Items.BAMBOO_BLOCK, 1, 4), false
    );
    private static final CraftRecipe CRIMSON_PLANKS = new CraftRecipe("Crimson Planks",
        new RecipeIngredient(Items.CRIMSON_STEM, 1, 4), false
    );
    private static final CraftRecipe WARPED_PLANKS = new CraftRecipe("Warped Planks",
        new RecipeIngredient(Items.WARPED_STEM, 1, 4), false
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
        final int gridSlot2x2;

        RecipeIngredient(Item item, int count, int gridSlot3x3) {
            this.item = item;
            this.count = count;
            this.gridSlot3x3 = gridSlot3x3;
            // Map 3x3 slot to 2x2 equivalent (top-left corner of 3x3)
            // Only slots 0,1,3,4 in 3x3 map to 0,1,2,3 in 2x2
            this.gridSlot2x2 = switch (gridSlot3x3) {
                case 0 -> 0;
                case 1 -> 1;
                case 3 -> 2;
                case 4 -> 3;
                default -> -1; // Not available in 2x2
            };
        }
    }
}

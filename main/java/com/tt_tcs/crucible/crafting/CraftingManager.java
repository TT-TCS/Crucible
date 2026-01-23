package com.tt_tcs.crucible.crafting;

import com.tt_tcs.crucible.CrucibleMain;
import com.tt_tcs.crucible.drugs.DrugItems;
import com.tt_tcs.crucible.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.Smoker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;

import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.RecipeChoice;
import org.slf4j.ILoggerFactory;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;

public class CraftingManager implements Listener {

    private final Set<NamespacedKey> customRecipeKeys = new HashSet<>();

    /**
     * Items that should NOT be allowed into any furnace-like smelting slot.
     * (Prevents fuel-wasting loops where the smelt is cancelled and the item remains.)
     */
    private boolean isBlockedCocaItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        String id = ItemUtil.getItemId(stack);
        return "coca_leaf".equals(id) || "dried_coca_leaf".equals(id) || "weed_leaf".equals(id);
    }

    private boolean isFurnaceLikeInventory(org.bukkit.inventory.Inventory inv) {
        if (inv == null) return false;
        InventoryType t = inv.getType();
        return t == InventoryType.FURNACE || t == InventoryType.SMOKER || t == InventoryType.BLAST_FURNACE;
    }


    public void registerRecipes() {
        ShapelessRecipe weedSeedDuping = new ShapelessRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "weed_seed_duping"),
                DrugItems.WEED_SEEDS.clone()
        );
        weedSeedDuping.addIngredient(Material.FERN);
        Bukkit.addRecipe(weedSeedDuping);
        customRecipeKeys.add(weedSeedDuping.getKey());

        ShapedRecipe jointRecipe = new ShapedRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "weed_joint_recipe"),
                DrugItems.JOINT.clone()
        );
        jointRecipe.shape(
                "PPP",
                "PBP",
                "PPP"
        );
        jointRecipe.setIngredient('P', Material.PAPER);
        jointRecipe.setIngredient('B', Material.GUNPOWDER);
        Bukkit.addRecipe(jointRecipe);
        customRecipeKeys.add(jointRecipe.getKey());

        ShapelessRecipe cocaSeedRecipe = new ShapelessRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "coca_seed_recipe"),
                DrugItems.COCA_SEEDS.clone()
        );
        cocaSeedRecipe.addIngredient(Material.DRIED_KELP);
        Bukkit.addRecipe(cocaSeedRecipe);
        customRecipeKeys.add(cocaSeedRecipe.getKey());

        ShapelessRecipe cocaSeedRecipe3 = new ShapelessRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "coca_seed_recipe3"),
                DrugItems.COCA_SEEDS.clone()
        );
        cocaSeedRecipe3.addIngredient(Material.KELP);
        Bukkit.addRecipe(cocaSeedRecipe3);
        customRecipeKeys.add(cocaSeedRecipe3.getKey());

        ShapelessRecipe cocaSeedRecipe2 = new ShapelessRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "coca_seed_recipe2"),
                DrugItems.COCA_SEEDS.clone()
        );
        cocaSeedRecipe2.addIngredient(Material.COCOA_BEANS);
        cocaSeedRecipe2.addIngredient(Material.COCOA_BEANS);
        cocaSeedRecipe2.addIngredient(Material.SUGAR);
        cocaSeedRecipe2.addIngredient(Material.PAPER);
        Bukkit.addRecipe(cocaSeedRecipe2);
        customRecipeKeys.add(cocaSeedRecipe2.getKey());

        ShapelessRecipe ephedraRecipe = new ShapelessRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "ephedra_recipe"),
                DrugItems.EPHEDRA_SEEDS.clone()
        );
        ephedraRecipe.addIngredient(Material.SUGAR_CANE);
        ephedraRecipe.addIngredient(Material.YELLOW_DYE);
        ephedraRecipe.addIngredient(Material.YELLOW_DYE);
        ephedraRecipe.addIngredient(Material.YELLOW_DYE);
        ephedraRecipe.addIngredient(Material.YELLOW_DYE);
        Bukkit.addRecipe(ephedraRecipe);
        customRecipeKeys.add(ephedraRecipe.getKey());

        ShapelessRecipe psilocybeSeedsRecipe = new ShapelessRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "psilocybe_seeds_recipe"),
                DrugItems.PSILOCYBE_SEEDS.clone()
        );
        psilocybeSeedsRecipe.addIngredient(Material.BROWN_MUSHROOM);
        psilocybeSeedsRecipe.addIngredient(Material.GLOWSTONE_DUST);
        psilocybeSeedsRecipe.addIngredient(Material.GLOWSTONE_DUST);
        psilocybeSeedsRecipe.addIngredient(Material.REDSTONE);
        psilocybeSeedsRecipe.addIngredient(Material.REDSTONE);
        Bukkit.addRecipe(psilocybeSeedsRecipe);
        customRecipeKeys.add(psilocybeSeedsRecipe.getKey());

        ShapelessRecipe psilocybeSeedsRecipe2 = new ShapelessRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "psilocybe_seeds_recipe2"),
                DrugItems.PSILOCYBE_SEEDS.clone()
        );
        psilocybeSeedsRecipe2.addIngredient(Material.BROWN_MUSHROOM);
        Bukkit.addRecipe(psilocybeSeedsRecipe2);
        customRecipeKeys.add(psilocybeSeedsRecipe2.getKey());

        ShapedRecipe stopwatchRecipe = new ShapedRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "stopwatch_recipe"),
                DrugItems.STOPWATCH.clone()
        );
        stopwatchRecipe.shape(
                " R ",
                "ICI",
                " R "
        );
        stopwatchRecipe.setIngredient('R', Material.REDSTONE);
        stopwatchRecipe.setIngredient('I', Material.IRON_INGOT);
        stopwatchRecipe.setIngredient('C', Material.CLOCK);
        Bukkit.addRecipe(stopwatchRecipe);
        customRecipeKeys.add(stopwatchRecipe.getKey());

        ItemStack plasticBagOut = DrugItems.PLASTIC_BAG.clone();
        plasticBagOut.setAmount(3);
        ShapedRecipe plasticBagRecipe = new ShapedRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "plastic_bag_recipe"),
                plasticBagOut
        );
        plasticBagRecipe.shape(
                " P ",
                "PBP",
                " P "
        );
        plasticBagRecipe.setIngredient('P', Material.PAPER);
        plasticBagRecipe.setIngredient('B', Material.GLASS_BOTTLE);
        Bukkit.addRecipe(plasticBagRecipe);
        customRecipeKeys.add(plasticBagRecipe.getKey());

        registerFurnaceRecipes();

        Bukkit.getLogger().info("[Crucible] Recipes registered.");
    }

    private void registerFurnaceRecipes() {
        FurnaceRecipe fernToSeeds = new FurnaceRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "fern_to_weed_seeds"),
                DrugItems.WEED_SEEDS.clone(),
                Material.FERN,
                0.1f,
                200
        );
        Bukkit.addRecipe(fernToSeeds);

        BlastingRecipe iodineRecipe = new BlastingRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "prismarine_to_iodine"),
                DrugItems.IODINE.clone(),
                Material.PRISMARINE_CRYSTALS,
                0.2f,
                100
        );
        Bukkit.addRecipe(iodineRecipe);

        BlastingRecipe coalSludgeRecipe = new BlastingRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "coal_tar_blasting"),
                DrugItems.COAL_SLUDGE.clone(),
                Material.CHARCOAL,
                0.1f,
                120
        );
        Bukkit.addRecipe(coalSludgeRecipe);

        SmokingRecipe ammoniumHydroxideRecipe = new SmokingRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "ammonium_hydroxide_gasification"),
                DrugItems.AMMONIUM_HYDROXIDE.clone(),
                Material.COAL,
                0.1f,
                160
        );
        Bukkit.addRecipe(ammoniumHydroxideRecipe);

        SmokingRecipe coalTarToPyridine = new SmokingRecipe(
                new NamespacedKey(CrucibleMain.getInstance(), "coal_tar_to_pyridine"),
                DrugItems.PYRIDINE.clone(),
                DrugItems.COAL_TAR.getType(),
                0.1f,
                160
        );
        Bukkit.addRecipe(coalTarToPyridine);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceInventoryClick(InventoryClickEvent event) {
        // Use the TOP inventory so we also catch shift-clicks from the player inventory into the furnace.
        Inventory top = event.getView().getTopInventory();
        if (!isFurnaceLikeInventory(top)) return;

        int rawSlot = event.getRawSlot();
        boolean clickingTop = rawSlot >= 0 && rawSlot < top.getSize();

        // Shift-click from the player inventory INTO the furnace: block moving coca/weed leaves into the input slot.
        if (event.isShiftClick() && !clickingTop) {
            ItemStack moving = event.getCurrentItem();
            if (isBlockedCocaItem(moving)) {
                event.setCancelled(true);
            }
            return;
        }

        // Only care about the furnace input/smelting slot (raw slot 0 in the top inventory).
        if (!clickingTop || rawSlot != 0) return;

        InventoryAction action = event.getAction();

        // Block placing coca/weed leaves into slot 0 via cursor placement.
        ItemStack cursor = event.getCursor();
        if (isBlockedCocaItem(cursor)) {
            switch (action) {
                case PLACE_ALL:
                case PLACE_ONE:
                case PLACE_SOME:
                case SWAP_WITH_CURSOR:
                case COLLECT_TO_CURSOR:
                    event.setCancelled(true);
                    return;
                default:
                    break;
            }
        }

        // Block number-key / hotbar swaps that would put a blocked item into slot 0.
        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            int btn = event.getHotbarButton();
            if (btn >= 0) {
                ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(btn);
                if (isBlockedCocaItem(hotbarItem)) {
                    event.setCancelled(true);
                }
            }
        }

        // IMPORTANT: Do NOT cancel when the blocked item is already in the slot and the player is taking it out.
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceInventoryDrag(InventoryDragEvent event) {
        if (!isFurnaceLikeInventory(event.getInventory())) return;

        // Prevent dragging coca items into slot 0.
        if (!event.getRawSlots().contains(0)) return;

        if (isBlockedCocaItem(event.getOldCursor()) || isBlockedCocaItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceInventoryMove(InventoryMoveItemEvent event) {
        if (!isFurnaceLikeInventory(event.getDestination())) return;

        // Hopper automation into the smelting slot
        if (isBlockedCocaItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        // Extra safety: if coca item is somehow in the input slot, prevent fuel being consumed.
        BlockState state = event.getBlock().getState();
        if (!(state instanceof Furnace furnace)) return;

        ItemStack smelting = furnace.getInventory().getSmelting();
        if (isBlockedCocaItem(smelting)) {
            event.setCancelled(true);
            furnace.setBurnTime((short) 0);
            furnace.setCookTime((short) 0);
            furnace.update(true, false);
        }
    }

    public void onCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        if (event.getRecipe() == null) return;

        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) return;
        NamespacedKey key = keyed.getKey();

        if (key == null || !customRecipeKeys.contains(key)) {
            if (containsAnyCustomItem(matrix)) {
                inv.setResult(null);
            }
            return;
        }

        String recipeKey = key.getKey();

        if (recipeKey.equals("weed_seed_duping")) {
            if (!containsCustomItem(matrix, "weed_leaf")) {
                inv.setResult(null);
                return;
            }
            inv.setResult(DrugItems.WEED_SEEDS.clone());
            return;
        }

        if (recipeKey.equals("weed_joint_recipe")) {
            ItemStack powder = getCustomItem(matrix, "weed_powder");
            if (powder == null) {
                inv.setResult(null);
                return;
            }

            ItemStack joint = DrugItems.JOINT.clone();
            ItemUtil.setQuality(joint, ItemUtil.getQuality(powder));
            inv.setResult(joint);
            return;
        }

        if (recipeKey.equals("coca_seed_recipe")) {
            if (getCustomItem(matrix, "dried_coca_leaf") == null) {
                inv.setResult(null);
                return;
            }
            inv.setResult(DrugItems.COCA_SEEDS.clone());
            return;
        }

        if (recipeKey.equals("coca_seed_recipe3")) {
            if (getCustomItem(matrix, "coca_leaf") == null) {
                inv.setResult(null);
                return;
            }
            inv.setResult(DrugItems.COCA_SEEDS.clone());
            return;
        }

        if (recipeKey.equals("coca_seed_recipe2")) {
            inv.setResult(DrugItems.COCA_SEEDS.clone());
            return;
        }

        if (recipeKey.equals("psilocybe_seeds_recipe")) {
            inv.setResult(DrugItems.PSILOCYBE_SEEDS.clone());
            return;
        }

        if (recipeKey.equals("psilocybe_seeds_recipe2")) {
            if (getCustomItem(matrix, "psilocybe_mushroom") == null) {
                inv.setResult(null);
                return;
            }
            inv.setResult(DrugItems.PSILOCYBE_SEEDS.clone());
            return;
        }

        if (recipeKey.equals("plastic_bag_recipe")) {
            ItemStack out = DrugItems.PLASTIC_BAG.clone();
            out.setAmount(3);
            inv.setResult(out);
            return;
        }
    }

    private boolean containsAnyCustomItem(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (item == null) continue;
            if (ItemUtil.getItemId(item) != null) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmelt(FurnaceSmeltEvent event) {
        ItemStack input = event.getSource();
        BlockState state = event.getBlock().getState();
        boolean isSmoker = event.getBlock().getType() == Material.SMOKER;

        String itemId = ItemUtil.getItemId(input);

        if (itemId != null) {
            if (itemId.equals("coal_tar")) {
                if (isSmoker) {
                    ItemStack pyridine = DrugItems.PYRIDINE.clone();
                    ItemUtil.setQuality(pyridine, ItemUtil.getQuality(input));
                    event.setResult(pyridine);
                    return;
                } else {
                    event.setCancelled(true);
                    return;
                }
            }

            // Weed leaf should NEVER be smeltable (only regular ferns -> weed seeds).
            // If it ends up in any furnace-like block, cancel the smelt to avoid producing items.
            if (itemId.equals("weed_leaf")) {
                event.setCancelled(true);
                return;
            }
            if (itemId.equals("coca_leaf") || itemId.equals("dried_coca_leaf")) {
                event.setCancelled(true);
                return;
            }

    return;
        }

        if (isSmoker && input.getType() == Material.CHARCOAL) {
            event.setCancelled(true);
            return;
        }

        if (isSmoker && input.getType() == Material.COAL) {
            ItemStack ammonium = DrugItems.AMMONIUM_HYDROXIDE.clone();
            event.setResult(ammonium);
            return;
        }

        if (input.getType() == Material.FERN) {
            event.setResult(DrugItems.WEED_SEEDS.clone());
            return;
        }

        if (input.getType() == Material.PRISMARINE_CRYSTALS) {
            if (state instanceof org.bukkit.block.BlastFurnace) {
                event.setResult(DrugItems.IODINE.clone());
            }
        }
    }

    private boolean containsCustomItem(ItemStack[] matrix, String id) {
        for (ItemStack item : matrix) {
            if (ItemUtil.isCustomItem(item, id)) return true;
        }
        return false;
    }

    private ItemStack getCustomItem(ItemStack[] matrix, String id) {
        for (ItemStack item : matrix) {
            if (ItemUtil.isCustomItem(item, id)) return item;
        }
        return null;
    }
}
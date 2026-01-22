package com.tt_tcs.crucible.util;

import com.tt_tcs.crucible.drugs.DrugEffect;
import com.tt_tcs.crucible.drugs.DrugItems;
import com.tt_tcs.crucible.drugs.DrugType;
import com.tt_tcs.crucible.drugs.MushroomHallucinationManager;
import com.tt_tcs.crucible.CrucibleMain;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CrucibleCommands implements CommandExecutor {

    // ==================== GEYSER / FLOODGATE ====================
    private static volatile Boolean floodgateChecked = null;
    private static volatile boolean floodgateAvailable = false;

    private static boolean isBedrockPlayer(Player player) {
        try {
            if (floodgateChecked == null) {
                Plugin fg = CrucibleMain.getInstance().getServer().getPluginManager().getPlugin("floodgate");
                floodgateAvailable = (fg != null && fg.isEnabled());
                floodgateChecked = Boolean.TRUE;
            }
            if (!floodgateAvailable) return false;

            Class<?> apiClz = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClz.getMethod("getInstance").invoke(null);
            Object res = apiClz.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(api, player.getUniqueId());
            return (res instanceof Boolean b) && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final Map<String, SimProcessingRecipe> SIM_PROCESSING_RECIPES = new LinkedHashMap<>();

    static {
        SIM_PROCESSING_RECIPES.put("weed_powder", new SimProcessingRecipe(3, Map.of(
                "weed_bud", 3
        )));
        SIM_PROCESSING_RECIPES.put("cocaine_powder", new SimProcessingRecipe(6, Map.of(
                "dried_coca_leaf", 3
        )));
        SIM_PROCESSING_RECIPES.put("ephedrine", new SimProcessingRecipe(2, Map.of(
                "ephedra", 3
        )));
        SIM_PROCESSING_RECIPES.put("white_phosphorus", new SimProcessingRecipe(1, Map.of(
                "bone_meal", 1
        )));
        SIM_PROCESSING_RECIPES.put("red_phosphorus", new SimProcessingRecipe(1, Map.of(
                "phosphorus_iodide", 1
        )));
        SIM_PROCESSING_RECIPES.put("phosphorus_iodide", new SimProcessingRecipe(3, Map.of(
                "white_phosphorus", 1,
                "iodine", 1
        )));
        SIM_PROCESSING_RECIPES.put("white_meth", new SimProcessingRecipe(10, Map.of(
                "ephedrine", 2,
                "red_phosphorus", 1,
                "iodine", 1
        )));
        SIM_PROCESSING_RECIPES.put("blue_meth", new SimProcessingRecipe(5, Map.of(
                "white_meth", 1,
                "blue_dye", 3
        )));
        SIM_PROCESSING_RECIPES.put("mushroom_base", new SimProcessingRecipe(8, Map.of(
                "brown_mushroom", 1,
                "glowstone_dust", 2,
                "redstone", 2
        )));
    }

    private static class SimProcessingRecipe {
        final int idealMinutes;
        final Map<String, Integer> ingredients;

        SimProcessingRecipe(int idealMinutes, Map<String, Integer> ingredients) {
            this.idealMinutes = idealMinutes;
            this.ingredients = ingredients;
        }
    }

    private static String formatIngredientName(String itemId) {
        if (itemId == null) return "Unknown";
        String[] parts = itemId.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!name.isEmpty()) name.append(" ");
            if (part.isEmpty()) continue;
            name.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return name.toString();
    }

    private static void applySimulatedProcessingLore(ItemStack item, SimProcessingRecipe recipe) {
        if (item == null || recipe == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        int processedMinutes = recipe.idealMinutes;
        int ingredientQualityStars = 10;

        meta.getPersistentDataContainer().set(ProcessingDataKeys.MINUTES_PROCESSED, PersistentDataType.INTEGER, processedMinutes);
        meta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITY, PersistentDataType.INTEGER, ingredientQualityStars);

        String ingredientNames = recipe.ingredients.entrySet().stream()
                .flatMap(e -> java.util.stream.IntStream.range(0, e.getValue()).mapToObj(i -> formatIngredientName(e.getKey())))
                .collect(Collectors.joining(","));
        String ingredientQualities = recipe.ingredients.entrySet().stream()
                .flatMap(e -> java.util.stream.IntStream.range(0, e.getValue()).mapToObj(i -> "10"))
                .collect(Collectors.joining(","));

        meta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_NAMES, PersistentDataType.STRING, ingredientNames);
        meta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITIES, PersistentDataType.STRING, ingredientQualities);

        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        int idx = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i) != null && lore.get(i).contains("minutes processed")) {
                idx = i;
                break;
            }
        }
        if (idx != -1) {
            lore = new ArrayList<>(lore.subList(0, idx));
            while (!lore.isEmpty() && lore.get(lore.size() - 1).isEmpty()) lore.remove(lore.size() - 1);
        }

        lore.add("");
        lore.add("§a" + processedMinutes + " minutes processed");
        lore.add("§8----------");
        lore.add("§7Ingredients " + ItemUtil.coloredStars(ingredientQualityStars));

        for (Map.Entry<String, Integer> entry : recipe.ingredients.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                lore.add("§7 - " + formatIngredientName(entry.getKey()) + " " + ItemUtil.coloredStars(10));
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("crucible.admin")) {
                player.sendMessage("§6--- Crucible Commands ---");
                player.sendMessage("§a/crucible tolerance §f- Show your tolerance");
                return true;
            } else {
                player.sendMessage("§6--- Crucible Commands ---");
                player.sendMessage("§a/crucible help §f- Show help menu");
                player.sendMessage("§a/crucible giveall §f- Get all drug items");
                player.sendMessage("§a/crucible tolerance §f- Show your tolerance");
                player.sendMessage("§a/crucible settolerance <player> <drug> <value> §f- Set tolerance");
                player.sendMessage("§a/crucible shrooms <on|off> [player] §f- Toggle shroom hallucinations");
                player.sendMessage("§a/crucible give <item> §f- Give a drug item");
                player.sendMessage("§a/crucible itemlist §f- List all custom item IDs");
                return true;
            }
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                return sendHelp(player);

            case "giveall":
                return giveItems(player);

            case "tolerance":
                return handleTolerance(player);

            case "settolerance":
                return modifyTolerance(player, args);

            case "shrooms":
                return toggleShrooms(player, args);

            case "give":
                return giveItem(player, args);

            case "itemlist":
                return listItems(player);

            default:
                player.sendMessage("§cUnknown subcommand. Try /crucible tolerance");
                return true;
        }
    }

    private boolean sendHelp(Player player) {
        player.sendMessage("§6--- Crucible Commands ---");
        player.sendMessage("§a/crucible help §f- Show help menu");
        player.sendMessage("§a/crucible giveall §f- Get all drug items");
        player.sendMessage("§a/crucible tolerance §f- Show your tolerance");
        player.sendMessage("§a/crucible settolerance <player> <drug> <value> §f- Set tolerance");
        player.sendMessage("§a/crucible shrooms <on|off> [player] §f- Toggle shroom hallucinations");
        player.sendMessage("§a/crucible give <item> §f- Give a drug item");
        player.sendMessage("§a/crucible itemlist §f- List all custom item IDs");
        return true;
    }

    private boolean giveItems(Player player) {
        if (!player.hasPermission("crucible.admin")) {
            player.sendMessage("§cYou don't have permission to use this.");
            return true;
        }

        player.sendMessage("§aGiving you all drug items...");

        // Weed items
        player.getInventory().addItem(DrugItems.WEED_SEEDS.clone());
        player.getInventory().addItem(DrugItems.WEED_LEAF.clone());
        player.getInventory().addItem(DrugItems.WEED_BUD.clone());
        player.getInventory().addItem(DrugItems.WEED_POWDER.clone());
        player.getInventory().addItem(DrugItems.JOINT.clone());

        // Cocaine items
        player.getInventory().addItem(DrugItems.COCA_SEEDS.clone());
        player.getInventory().addItem(DrugItems.COCA_LEAF.clone());
        player.getInventory().addItem(DrugItems.DRIED_COCA_LEAF.clone());
        player.getInventory().addItem(DrugItems.COCAINE.clone());

        // Meth items
        player.getInventory().addItem(DrugItems.EPHEDRA_SEEDS.clone());
        player.getInventory().addItem(DrugItems.EPHEDRA.clone());
        player.getInventory().addItem(DrugItems.EPHEDRINE.clone());
        player.getInventory().addItem(DrugItems.WHITE_PHOSPHORUS.clone());
        player.getInventory().addItem(DrugItems.RED_PHOSPHORUS.clone());
        player.getInventory().addItem(DrugItems.IODINE.clone());
        player.getInventory().addItem(DrugItems.PHOSPHORUS_IODIDE.clone());
        player.getInventory().addItem(DrugItems.WHITE_METH.clone());
        player.getInventory().addItem(DrugItems.BLUE_METH.clone());

        // Shrooms Items
        player.getInventory().addItem(DrugItems.PSILOCYBE_SEEDS.clone());
        player.getInventory().addItem(DrugItems.PSILOCYBE_MUSHROOM.clone());
        player.getInventory().addItem(DrugItems.DRIED_PSILOCYBE_MUSHROOM.clone());
        player.getInventory().addItem(DrugItems.PSILOCYBE_EXTRACT.clone());
        player.getInventory().addItem(DrugItems.PSILOCYBE.clone());

        player.sendMessage("§aReceived all drug items!");
        return true;
    }

    private boolean handleTolerance(Player player) {
        player.sendMessage("§6--- Tolerance Levels ---");
        for (DrugType type : DrugType.values()) {
            double tol = DrugEffect.getTolerance(player, type);
            String color = tol > 1.0 ? "§c" : tol > 0.8 ? "§e" : "§a";
            player.sendMessage(color + type.id + ": §f" + String.format("%.2f", tol));
        }
        return true;
    }

    private boolean modifyTolerance(Player player, String[] args) {

        if (!player.hasPermission("crucible.admin")) {
            player.sendMessage("§cYou don't have permission to use this.");
            return true;
        }

        if (args.length != 4) {
            player.sendMessage("§cUsage: /crucible settolerance <player> <drug> <value>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return true;
        }

        DrugType type = DrugType.fromId(args[2]);
        if (type == null) {
            player.sendMessage("§cInvalid drug type. Valid: " +
                    Arrays.stream(DrugType.values())
                            .map(t -> t.id)
                            .collect(Collectors.joining(", ")));
            return true;
        }

        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cValue must be a number.");
            return true;
        }

        DrugEffect.setTolerance(target, type, value);
        player.sendMessage("§aSet " + target.getName() + "'s tolerance for " + type.id + " to " + value);
        return true;
    }

    private boolean toggleShrooms(Player player, String[] args) {
        if (!player.hasPermission("crucible.admin")) {
            player.sendMessage("§cYou don't have permission to use this.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /crucible shrooms <on|off> [player]");
            return true;
        }

        String action = args[1].toLowerCase();
        if (!action.equals("on") && !action.equals("off")) {
            player.sendMessage("§cUsage: /crucible shrooms <on|off> [player]");
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                player.sendMessage("§cPlayer not found.");
                return true;
            }
        } else {
            target = player;
        }

        if (action.equals("on")) {
            MushroomHallucinationManager.startHallucination(target, 10);
            player.sendMessage("§aStarted shroom hallucination for " + target.getName());
            if (!target.equals(player)) {
                target.sendMessage("§dYou feel the mushrooms taking effect...");
            }
        } else {
            MushroomHallucinationManager.stopHallucination(target);
            player.sendMessage("§aStopped shroom hallucination for " + target.getName());
            if (!target.equals(player)) {
                target.sendMessage("§7The hallucinations fade away...");
            }
        }

        return true;
    }

    private boolean giveItem(Player player, String[] args) {

        if (!player.hasPermission("crucible.admin")) {
            player.sendMessage("§cYou don't have permission to use this.");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage("§cUsage: /crucible give <item>");
            return true;
        }

        String itemId = args[1].toLowerCase();

        var item = DrugItems.getById(itemId);

        if (item == null) {
            player.sendMessage("§cUnknown item: §f" + itemId);
            return true;
        }

        ItemStack give = item.clone();

        if (ItemUtil.supportsQuality(give) && !ItemUtil.hasQualityKey(give)) {
            ItemUtil.setQuality(give, 10);
        }

        SimProcessingRecipe sim = SIM_PROCESSING_RECIPES.get(itemId);
        if (sim != null) {
            applySimulatedProcessingLore(give, sim);
        }

        ItemUtil.renderLoreForClient(give, isBedrockPlayer(player));

        player.getInventory().addItem(give);
        player.sendMessage("§aGiven §f" + itemId);
        return true;
    }

    private boolean listItems(Player player) {
        if (!player.hasPermission("crucible.admin")) {
            player.sendMessage("§cYou don't have permission to use this.");
            return true;
        }

        player.sendMessage("§6--- Crucible Item List ---");
        player.sendMessage("§7Use with: §f/crucible give <item>");

        Map<DrugType, List<String>> byType = DrugItems.getRegisteredItemIdsByDrugType();

        for (DrugType type : DrugType.values()) {
            List<String> ids = byType.get(type);

            player.sendMessage("");
            player.sendMessage("§a" + formatDrugTypeHeading(type) + ":");

            if (ids == null || ids.isEmpty()) {
                player.sendMessage(" §7- (none)");
                continue;
            }

            for (String id : ids) {
                player.sendMessage(" §7- " + id);
            }
        }

        return true;
    }

    private static String formatDrugTypeHeading(DrugType type) {
        if (type == null || type.id == null || type.id.isEmpty()) return "Unknown";
        String s = type.id;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
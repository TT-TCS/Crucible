package com.tt_tcs.crucible.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ItemUtil {

    private static final Set<String> NO_QUALITY_ITEM_IDS = Set.of(
            "iodine",
            "weed_seeds",
            "coca_seeds",
            "ephedra_seeds",
            "plastic_bag"
    );

    private static String stripColorCodes(String s) {
        if (s == null) return "";

        return s.replaceAll("(?i)§.", "");
    }

    private static boolean isQualityLine(String loreLine) {

        String stripped = stripColorCodes(loreLine).trim();
        if (stripped.isEmpty()) return false;

        if (stripped.matches("[★☆⯪]+")) return true;

        return stripped.matches("[0-5](?:\\.5)?★\\s+Quality");
    }

    private static boolean isMinutesLine(String loreLine) {
        if (loreLine == null) return false;
        String stripped = stripColorCodes(loreLine).trim();
        return stripped.contains("minutes processed") || stripped.contains("minutes dried");
    }

    public static ItemStack createCustomItem(Material material, String name, String itemId, String drugType, Integer modelData) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, itemId);
        meta.getPersistentDataContainer().set(ItemKeys.DRUG_TYPE, PersistentDataType.STRING, drugType);

        if (modelData != null) meta.setCustomModelData(modelData);

        item.setItemMeta(meta);
        return item;
    }

    public static String getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(ItemKeys.ITEM_ID, PersistentDataType.STRING);
    }

    public static String getDrugType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(ItemKeys.DRUG_TYPE, PersistentDataType.STRING);
    }

    public static boolean isCustomItem(ItemStack item, String id) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        String storedId = meta.getPersistentDataContainer()
                .get(ItemKeys.ITEM_ID, PersistentDataType.STRING);

        return storedId != null && storedId.equals(id);
    }

    public static void setQuality(ItemStack item, int halfStars) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        halfStars = Math.max(0, Math.min(10, halfStars));

        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }

        lore.removeIf(ItemUtil::isQualityLine);

        if (halfStars <= 0) {
            meta.getPersistentDataContainer().remove(ItemKeys.QUALITY);
            meta.setLore(lore);
            item.setItemMeta(meta);
            return;
        }

        meta.getPersistentDataContainer().set(ItemKeys.QUALITY, PersistentDataType.INTEGER, halfStars);

        if (lore.isEmpty() || !lore.get(0).isEmpty()) {
            lore.add(0, "");
        }

        int insertIndex = (!lore.isEmpty() && lore.get(0).isEmpty()) ? 1 : 0;
        lore.add(insertIndex, getColoredStars(halfStars));

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public static void renderLoreForClient(ItemStack item, boolean bedrockClient) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        int halfStars = meta.getPersistentDataContainer().getOrDefault(ItemKeys.QUALITY, PersistentDataType.INTEGER, 0);
        if (halfStars <= 0) return;

        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        lore.removeIf(ItemUtil::isQualityLine);

        if (lore.isEmpty() || !lore.get(0).isEmpty()) lore.add(0, "");
        int insertIndex = (!lore.isEmpty() && lore.get(0).isEmpty()) ? 1 : 0;

        if (bedrockClient) {
            lore.add(insertIndex, getNumericQualityLine(halfStars));
        } else {
            lore.add(insertIndex, getColoredStars(halfStars));
        }

        if (bedrockClient) {
            for (int i = 0; i < lore.size(); i++) {
                lore.set(i, convertTrailingStarBarToNumeric(lore.get(i)));
            }
        } else {
            for (int i = 0; i < lore.size(); i++) {
                lore.set(i, convertTrailingNumericToStarBar(lore.get(i)));
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public static int getQuality(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        return meta.getPersistentDataContainer()
                .getOrDefault(ItemKeys.QUALITY, PersistentDataType.INTEGER, 0);
    }

    public static boolean hasQualityKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(ItemKeys.QUALITY, PersistentDataType.INTEGER);
    }

    public static boolean supportsQuality(ItemStack item) {
        String id = getItemId(item);
        if (id == null) return false;
        return !NO_QUALITY_ITEM_IDS.contains(id.toLowerCase());
    }

    public static int getQualityForRecipe(ItemStack item) {
        if (!hasQualityKey(item)) return 10;
        return getQuality(item);
    }

    private static String getColoredStars(int halfStars) {
        int fullStars = halfStars / 2;
        boolean half = (halfStars % 2) == 1;

        double stars = halfStars / 2.0;

        String color;
        if (stars > 4.0) {
            color = "§a"; // Green
        } else if (stars >= 2.5) {
            color = "§e"; // Yellow
        } else {
            color = "§c"; // Red
        }

        StringBuilder sb = new StringBuilder(color);
        for (int i = 0; i < fullStars; i++) sb.append("★");
        if (half) sb.append("⯪");

        int remaining = 5 - fullStars - (half ? 1 : 0);
        if (remaining > 0) {
            sb.append("§7");
            for (int i = 0; i < remaining; i++) sb.append("☆");
        }

        return sb.toString();
    }

    private static String getNumericQualityLine(int halfStars) {
        double stars = halfStars / 2.0;
        String color;
        if (stars > 4.0) {
            color = "§a";
        } else if (stars >= 2.5) {
            color = "§e";
        } else {
            color = "§c";
        }

        String numeric = (stars % 1.0 == 0.0)
                ? String.valueOf((int) stars)
                : String.format(java.util.Locale.ROOT, "%.1f", stars);
        return color + numeric + "★ Quality";
    }

    private static String convertTrailingStarBarToNumeric(String line) {
        if (line == null || line.isEmpty()) return line;
        String stripped = stripColorCodes(line);

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(.*?)([★☆⯪]+)\\s*$").matcher(stripped);
        if (!m.matches()) return line;

        String prefix = m.group(1);
        String bar = m.group(2);

        int full = countChar(bar, '★');
        int empty = countChar(bar, '☆');
        int half = countChar(bar, '⯪');
        if (full + empty + half == 0) return line;

        int halfStars = (full * 2) + (half > 0 ? 1 : 0);
        double stars = halfStars / 2.0;

        String color = getColorForStars(stars);
        String numeric = (stars % 1.0 == 0.0)
                ? String.valueOf((int) stars)
                : String.format(java.util.Locale.ROOT, "%.1f", stars);

        String cleanedPrefix = prefix.trim();
        if (!cleanedPrefix.isEmpty()) cleanedPrefix = cleanedPrefix + " ";
        return cleanedPrefix + color + numeric + "★";
    }

    private static String convertTrailingNumericToStarBar(String line) {
        if (line == null || line.isEmpty()) return line;
        String stripped = stripColorCodes(line);

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(.*?)([0-5](?:\\.5)?)★\\s*$").matcher(stripped);
        if (!m.matches()) return line;

        String prefix = m.group(1);
        double stars;
        try {
            stars = Double.parseDouble(m.group(2));
        } catch (Exception e) {
            return line;
        }

        int halfStars = (int) Math.round(stars * 2.0);
        String bar = getColoredStars(halfStars);
        String cleanedPrefix = prefix.trim();
        if (!cleanedPrefix.isEmpty()) cleanedPrefix = cleanedPrefix + " ";
        return cleanedPrefix + bar;
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) count++;
        return count;
    }

    private static String getColorForStars(double stars) {
        if (stars > 4.0) return "§a";
        if (stars >= 2.5) return "§e";
        return "§c";
    }

    public static String coloredStars(int halfStars) {
        return getColoredStars(halfStars);
    }

    public static void setMinutes(ItemStack item, int elapsedMinutes, int idealMinutes, boolean isDriedContext) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        lore.removeIf(ItemUtil::isMinutesLine);

        int starsIndex = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (isQualityLine(lore.get(i))) {
                starsIndex = i;
                break;
            }
        }

        elapsedMinutes = Math.max(0, elapsedMinutes);
        idealMinutes = Math.max(1, idealMinutes);

        int diff = Math.abs(elapsedMinutes - idealMinutes);

        String color;
        if (diff == 0) color = "§a";
        else if (diff <= 2) color = "§e";
        else color = "§c";

        String label = isDriedContext ? "minutes dried" : "minutes processed";
        String minutesLine = color + elapsedMinutes + " " + label;

        if (starsIndex != -1) {
            int insertIndex = starsIndex + 1;

            if (insertIndex < lore.size() && lore.get(insertIndex).isEmpty()) {
                lore.add(insertIndex + 1, minutesLine);
            } else {
                lore.add(insertIndex, "");
                lore.add(insertIndex + 1, minutesLine);
            }
        } else {
            if (!lore.isEmpty() && !lore.get(lore.size() - 1).isEmpty()) {
                lore.add("");
            }
            lore.add(minutesLine);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }
}
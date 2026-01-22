package com.tt_tcs.crucible.drugs;

import com.tt_tcs.crucible.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import com.tt_tcs.crucible.util.ItemUtil;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DrugItems {

    // ==================== WEED ====================

    public static final ItemStack WEED_SEEDS = ItemUtil.createCustomItem(
            Material.COCOA_BEANS,
            "§aWeed Seeds",
            "weed_seeds",
            "weed",
            6969
    );

    public static final ItemStack WEED_LEAF = ItemUtil.createCustomItem(
            Material.FERN,
            "§aWeed Leaf",
            "weed_leaf",
            "weed",
            6970
    );

    public static final ItemStack WEED_BUD = ItemUtil.createCustomItem(
            Material.GREEN_DYE,
            "§aWeed Bud",
            "weed_bud",
            "weed",
            6971
    );

    public static final ItemStack WEED_POWDER = ItemUtil.createCustomItem(
            Material.GUNPOWDER,
            "§aWeed Powder",
            "weed_powder",
            "weed",
            6977
    );

    public static final ItemStack JOINT = ItemUtil.createCustomItem(
            Material.STICK,
            "§aJoint",
            "joint",
            "weed",
            6972
    );

    // ==================== COCAINE ====================

    public static final ItemStack COCA_SEEDS = ItemUtil.createCustomItem(
            Material.WHEAT_SEEDS,
            "§fCoca Seeds",
            "coca_seeds",
            "cocaine",
            6973
    );

    public static final ItemStack COCA_LEAF = ItemUtil.createCustomItem(
            Material.KELP,
            "§fCoca Leaf",
            "coca_leaf",
            "cocaine",
            6974
    );

    public static final ItemStack DRIED_COCA_LEAF = ItemUtil.createCustomItem(
            Material.DRIED_KELP,
            "§fDried Coca Leaf",
            "dried_coca_leaf",
            "cocaine",
            6975
    );

    public static final ItemStack COCAINE = ItemUtil.createCustomItem(
            Material.SUGAR,
            "§fCocaine",
            "cocaine",
            "cocaine",
            6976
    );

    // ==================== METH ====================

    public static final ItemStack EPHEDRA_SEEDS = ItemUtil.createCustomItem(
            Material.MELON_SEEDS,
            "§aEphedra Seeds",
            "ephedra_seeds",
            "meth",
            7099
    );

    public static final ItemStack EPHEDRA = ItemUtil.createCustomItem(
            Material.SUGAR_CANE,
            "§aEphedra",
            "ephedra",
            "meth",
            7100
    );

    public static final ItemStack EPHEDRINE = ItemUtil.createCustomItem(
            Material.SUGAR,
            "§fEphedrine",
            "ephedrine",
            "meth",
            7101
    );

    public static final ItemStack WHITE_PHOSPHORUS = ItemUtil.createCustomItem(
            Material.SUGAR,
            "§fWhite Phosphorus",
            "white_phosphorus",
            "meth",
            7102
    );

    public static final ItemStack RED_PHOSPHORUS = ItemUtil.createCustomItem(
            Material.REDSTONE,
            "§cRed Phosphorus",
            "red_phosphorus",
            "meth",
            7103
    );

    public static final ItemStack IODINE = ItemUtil.createCustomItem(
            Material.PRISMARINE_CRYSTALS,
            "§9Iodine",
            "iodine",
            "meth",
            7104
    );

    public static final ItemStack PHOSPHORUS_IODIDE = ItemUtil.createCustomItem(
            Material.RED_DYE,
            "§cPhosphorus Iodide",
            "phosphorus_iodide",
            "meth",
            7105
    );

    public static final ItemStack WHITE_METH = ItemUtil.createCustomItem(
            Material.SUGAR,
            "§fWhite Methamphetamine",
            "white_meth",
            "meth",
            7106
    );

    public static final ItemStack BLUE_METH = ItemUtil.createCustomItem(
            Material.LIGHT_BLUE_DYE,
            "§bBlue Methamphetamine",
            "blue_meth",
            "meth",
            7107
    );

    
    public static final ItemStack METHCATHINONE = ItemUtil.createCustomItem(
            Material.SUGAR,
            "§fMethcathinone",
            "methcathinone",
            "meth",
            7108
    );

// ==================== PSILOCYBE (SHROOMS) ====================

    public static final ItemStack PSILOCYBE_SEEDS = ItemUtil.createCustomItem(
            Material.BEETROOT_SEEDS,
            "§5Psilocybe Seeds",
            "psilocybe_seeds",
            "shrooms",
            7200
    );

    public static final ItemStack PSILOCYBE_MUSHROOM = ItemUtil.createCustomItem(
            Material.BROWN_MUSHROOM,
            "§5Psilocybe Mushroom",
            "psilocybe_mushroom",
            "shrooms",
            7201
    );

    public static final ItemStack DRIED_PSILOCYBE_MUSHROOM = ItemUtil.createCustomItem(
            Material.BROWN_MUSHROOM,
            "§dDried Psilocybe Mushroom",
            "dried_psilocybe_mushroom",
            "shrooms",
            7202
    );

    public static final ItemStack PSILOCYBE_EXTRACT = ItemUtil.createCustomItem(
            Material.GUNPOWDER,
            "§dPsilocybe Extract",
            "psilocybe_extract",
            "shrooms",
            7203
    );

    public static final ItemStack PSILOCYBE = createShroomsGradientItem(
            Material.SUGAR,
            "Psilocybe",
            "psilocybe",
            "shrooms",
            7204
    );

    // ==================== FENTANYL ====================

    public static final ItemStack COAL_SLUDGE = ItemUtil.createCustomItem(
            Material.BLACK_DYE,
            "§8Coal Sludge",
            "coal_sludge",
            "fentanyl",
            7300
    );

    public static final ItemStack COAL_TAR = ItemUtil.createCustomItem(
            Material.CHARCOAL,
            "§8Coal Tar",
            "coal_tar",
            "fentanyl",
            7307
    );

    public static final ItemStack PHENOL = ItemUtil.createCustomItem(
            Material.QUARTZ,
            "§fPhenol",
            "phenol",
            "fentanyl",
            7308
    );

    public static final ItemStack PYRIDINE = ItemUtil.createCustomItem(
            Material.GLASS_BOTTLE,
            "§fPyridine",
            "pyridine",
            "fentanyl",
            7301
    );

    public static final ItemStack PIPERIDINE = ItemUtil.createCustomItem(
            Material.SUGAR,
            "§fPiperidine",
            "piperidine",
            "fentanyl",
            7302
    );

    public static final ItemStack AMMONIUM_HYDROXIDE = createLabPotion(
            "§6Ammonium Hydroxide",
            "ammonium_hydroxide",
            Color.fromRGB(168, 140, 90),
            7303
    );

    public static final ItemStack AMMONIA = ItemUtil.createCustomItem(
            Material.GLASS_BOTTLE,
            "§fAmmonia",
            "ammonia",
            "fentanyl",
            7304
    );

    public static final ItemStack ANILINE = createLabPotion(
            "§eAniline",
            "aniline",
            Color.fromRGB(235, 220, 110),
            7305
    );

    public static final ItemStack FENTANYL = ItemUtil.createCustomItem(
            Material.SUGAR,
            "§fFentanyl",
            "fentanyl",
            "fentanyl",
            7306
    );

    // ==================== MISC ITEMS ====================

    public static final ItemStack PLASTIC_BAG = ItemUtil.createCustomItem(
            Material.PAPER,
            "§fPlastic Bag",
            "plastic_bag",
            "misc",
            7999
    );

    public static final ItemStack STOPWATCH = ItemUtil.createCustomItem(
            Material.CLOCK,
            "§fStopwatch",
            "stopwatch",
            "misc",
            7990
    );



    // ==================== UTILITY METHODS ====================

    public static ItemStack getProcessingOutput(String outputItemId) {
        return switch (outputItemId.toLowerCase()) {
            case "weed_powder" -> WEED_POWDER.clone();
            case "cocaine" -> COCAINE.clone();
            case "ephedrine" -> EPHEDRINE.clone();
            case "white_phosphorus" -> WHITE_PHOSPHORUS.clone();
            case "red_phosphorus" -> RED_PHOSPHORUS.clone();
            case "phosphorus_iodide" -> PHOSPHORUS_IODIDE.clone();
            case "white_meth" -> WHITE_METH.clone();
            case "blue_meth" -> BLUE_METH.clone();
            case "methcathinone" -> METHCATHINONE.clone();
            case "psilocybe_extract" -> PSILOCYBE_EXTRACT.clone();
            case "psilocybe" -> PSILOCYBE.clone();
            case "psilocybe_seeds" -> PSILOCYBE_SEEDS.clone();
            case "coal_sludge" -> COAL_SLUDGE.clone();
            case "coal_tar" -> COAL_TAR.clone();
            case "phenol" -> PHENOL.clone();
            case "pyridine" -> PYRIDINE.clone();
            case "piperidine" -> PIPERIDINE.clone();
            case "ammonium_hydroxide" -> AMMONIUM_HYDROXIDE.clone();
            case "ammonia" -> AMMONIA.clone();
            case "aniline" -> ANILINE.clone();
            case "fentanyl" -> FENTANYL.clone();
            case "gold_ingot" -> new ItemStack(Material.GOLD_INGOT);
            case "plastic_bag" -> PLASTIC_BAG.clone();
            case "stopwatch" -> STOPWATCH.clone();
            default -> null;
        };
    }

    public static ItemStack getById(String id) {
        return switch(id.toLowerCase()) {
            case "weed_seeds" -> WEED_SEEDS.clone();
            case "weed_leaf" -> WEED_LEAF.clone();
            case "weed_bud" -> WEED_BUD.clone();
            case "weed_powder" -> WEED_POWDER.clone();
            case "joint" -> JOINT.clone();
            case "coca_seeds" -> COCA_SEEDS.clone();
            case "coca_leaf" -> COCA_LEAF.clone();
            case "dried_coca_leaf" -> DRIED_COCA_LEAF.clone();
            case "cocaine" -> COCAINE.clone();
            case "ephedra_seeds" -> EPHEDRA_SEEDS.clone();
            case "ephedra" -> EPHEDRA.clone();
            case "ephedrine" -> EPHEDRINE.clone();
            case "white_phosphorus" -> WHITE_PHOSPHORUS.clone();
            case "red_phosphorus" -> RED_PHOSPHORUS.clone();
            case "iodine" -> IODINE.clone();
            case "phosphorus_iodide" -> PHOSPHORUS_IODIDE.clone();
            case "white_meth" -> WHITE_METH.clone();
            case "blue_meth" -> BLUE_METH.clone();
            case "methcathinone" -> METHCATHINONE.clone();
            case "psilocybe_seeds" -> PSILOCYBE_SEEDS.clone();
            case "psilocybe_mushroom" -> PSILOCYBE_MUSHROOM.clone();
            case "dried_psilocybe_mushroom" -> DRIED_PSILOCYBE_MUSHROOM.clone();
            case "psilocybe_extract" -> PSILOCYBE_EXTRACT.clone();
            case "psilocybe" -> PSILOCYBE.clone();
            case "coal_sludge" -> COAL_SLUDGE.clone();
            case "coal_tar" -> COAL_TAR.clone();
            case "phenol" -> PHENOL.clone();
            case "pyridine" -> PYRIDINE.clone();
            case "piperidine" -> PIPERIDINE.clone();
            case "ammonium_hydroxide" -> AMMONIUM_HYDROXIDE.clone();
            case "ammonia" -> AMMONIA.clone();
            case "aniline" -> ANILINE.clone();
            case "fentanyl" -> FENTANYL.clone();
            case "plastic_bag" -> PLASTIC_BAG.clone();
            case "stopwatch" -> STOPWATCH.clone();
            default -> null;
        };
    }

    private static ItemStack createLabPotion(String name, String itemId, Color color, Integer modelData) {
        ItemStack item = ItemUtil.createCustomItem(Material.POTION, name, itemId, "fentanyl", modelData);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof PotionMeta pm) {
            pm.setColor(color);
            item.setItemMeta(pm);
        }
        return item;
    }

    public static Map<DrugType, List<String>> getRegisteredItemIdsByDrugType() {
        Map<DrugType, List<String>> out = new LinkedHashMap<>();
        for (DrugType type : DrugType.values()) {
            out.put(type, new ArrayList<>());
        }

        for (Field field : DrugItems.class.getDeclaredFields()) {
            if (field.getType() != ItemStack.class) continue;
            int mods = field.getModifiers();
            if (!Modifier.isStatic(mods)) continue;

            try {
                ItemStack stack = (ItemStack) field.get(null);
                if (stack == null) continue;

                ItemMeta meta = stack.getItemMeta();
                if (meta == null) continue;

                String itemId = meta.getPersistentDataContainer().get(ItemKeys.ITEM_ID, PersistentDataType.STRING);
                String drugTypeId = meta.getPersistentDataContainer().get(ItemKeys.DRUG_TYPE, PersistentDataType.STRING);
                if (itemId == null || drugTypeId == null) continue;

                DrugType type = DrugType.fromId(drugTypeId);
                if (type == null) continue;

                out.computeIfAbsent(type, k -> new ArrayList<>()).add(itemId);
            } catch (IllegalAccessException ignored) {
            }
        }

        for (List<String> ids : out.values()) {
            ids.sort(Comparator.naturalOrder());
        }

        return out;
    }

    private static ItemStack createShroomsGradientItem(Material material, String name, String itemId, String drugType, Integer modelData) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        TextColor[] colors = new TextColor[] {
                TextColor.fromHexString("#7c3aed"), // purple
                TextColor.fromHexString("#9333ea"),
                TextColor.fromHexString("#a855f7"),
                TextColor.fromHexString("#d946ef"), // pink
                TextColor.fromHexString("#f472b6")  // light pink
        };

        Component nameComponent = Component.empty();
        for (int i = 0; i < name.length(); i++) {
            TextColor color = colors[i * colors.length / name.length()];
            nameComponent = nameComponent.append(
                    Component.text(String.valueOf(name.charAt(i)), color)
            );
        }

        meta.displayName(nameComponent.decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(ItemKeys.ITEM_ID, PersistentDataType.STRING, itemId);
        meta.getPersistentDataContainer().set(ItemKeys.DRUG_TYPE, PersistentDataType.STRING, drugType);

        if (modelData != null) meta.setCustomModelData(modelData);
        item.setItemMeta(meta);
        return item;
    }
}
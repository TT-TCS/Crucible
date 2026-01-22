package com.tt_tcs.crucible.util;

import com.tt_tcs.crucible.CrucibleMain;
import org.bukkit.NamespacedKey;

public final class ProcessingDataKeys {

    private ProcessingDataKeys() {}

    public static final NamespacedKey MINUTES_PROCESSED =
            new NamespacedKey(CrucibleMain.getInstance(), "minutes_processed");

    public static final NamespacedKey INGREDIENT_QUALITY =
            new NamespacedKey(CrucibleMain.getInstance(), "ingredient_quality");

    public static final NamespacedKey INGREDIENT_NAMES =
            new NamespacedKey(CrucibleMain.getInstance(), "ingredient_names");

    public static final NamespacedKey INGREDIENT_QUALITIES =
            new NamespacedKey(CrucibleMain.getInstance(), "ingredient_qualities");
}

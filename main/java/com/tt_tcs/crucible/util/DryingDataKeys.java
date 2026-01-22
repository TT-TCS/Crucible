package com.tt_tcs.crucible.util;

import com.tt_tcs.crucible.CrucibleMain;
import org.bukkit.NamespacedKey;

public class DryingDataKeys {
    public static final NamespacedKey DRYING_ELAPSED_MINUTES =
            new NamespacedKey(CrucibleMain.getInstance(), "dry_elapsed_minutes");

    public static final NamespacedKey DRYING_RECIPE_ID =
            new NamespacedKey(CrucibleMain.getInstance(), "dry_recipe_id");

    public static final NamespacedKey DRYING_BASE_QUALITY =
            new NamespacedKey(CrucibleMain.getInstance(), "dry_base_quality");
}

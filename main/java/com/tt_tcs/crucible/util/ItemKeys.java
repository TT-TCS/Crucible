package com.tt_tcs.crucible.util;

import org.bukkit.NamespacedKey;

import com.tt_tcs.crucible.CrucibleMain;

public final class ItemKeys {

    private ItemKeys() {}

    public static final NamespacedKey ITEM_ID =
            new NamespacedKey(CrucibleMain.getInstance(), "item_id");

    public static final NamespacedKey QUALITY =
            new NamespacedKey(CrucibleMain.getInstance(), "quality");

    public static final NamespacedKey DRUG_TYPE =
            new NamespacedKey(CrucibleMain.getInstance(), "drug_type");

    public static final NamespacedKey STOPWATCH_UUID =
            new NamespacedKey(CrucibleMain.getInstance(), "stopwatch_uuid");

    public static final NamespacedKey STOPWATCH_SECONDS =
            new NamespacedKey(CrucibleMain.getInstance(), "stopwatch_seconds");

    public static final NamespacedKey STOPWATCH_ELAPSED_MS =
            new NamespacedKey(CrucibleMain.getInstance(), "stopwatch_elapsed_ms");

    public static final NamespacedKey STOPWATCH_START_MS =
            new NamespacedKey(CrucibleMain.getInstance(), "stopwatch_start_ms");

    public static final NamespacedKey STOPWATCH_RUNNING =
            new NamespacedKey(CrucibleMain.getInstance(), "stopwatch_running");

}
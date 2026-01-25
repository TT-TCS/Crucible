package com.tt_tcs.crucible;

import com.tt_tcs.crucible.crafting.CraftingManager;
import com.tt_tcs.crucible.crafting.ChemicalProcessorManager;
import com.tt_tcs.crucible.crafting.DryingRackManager;
import com.tt_tcs.crucible.crafting.DrugProcessingManager;
import com.tt_tcs.crucible.crops.CocaineCropManager;
import com.tt_tcs.crucible.crops.EphedraCropManager;
import com.tt_tcs.crucible.crops.PsilocybeCropManager;
import com.tt_tcs.crucible.crops.WeedCropManager;
import com.tt_tcs.crucible.drugs.DrugEffect;
import com.tt_tcs.crucible.drugs.DrugType;
import com.tt_tcs.crucible.drugs.DrugUsing;
import com.tt_tcs.crucible.util.CrucibleCommands;
import com.tt_tcs.crucible.util.CrucibleListener;
import com.tt_tcs.crucible.util.StopwatchManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CrucibleMain extends JavaPlugin {

    private static CrucibleMain instance;
    private DrugProcessingManager processingManager;
    private ChemicalProcessorManager chemicalProcessorManager;

    
    private final UpdateChecker updateChecker = new UpdateChecker();
public void startToleranceDecay() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {

                for (DrugType type : DrugType.values()) {
                    NamespacedKey key = DrugEffect.getToleranceKey(type);

                    double tolerance = player.getPersistentDataContainer()
                            .getOrDefault(key, PersistentDataType.DOUBLE, 0.0);

                    if (tolerance <= 0) continue;

                    // Decay rate: 0.01 per minute = 1hr 40m
                    tolerance -= 0.01;
                    if (tolerance < 0) tolerance = 0;

                    player.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, tolerance);
                }

            }
        }, 0L, 20 * 60); // every minute
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Crucible enabled!");

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Register event listeners
        
        // Check GitHub releases for updates (admins will be notified on join)
        updateChecker.checkAsync(this);

getServer().getPluginManager().registerEvents(new WeedCropManager(), this);
        getServer().getPluginManager().registerEvents(new CocaineCropManager(), this);
        getServer().getPluginManager().registerEvents(new EphedraCropManager(), this);
        getServer().getPluginManager().registerEvents(new PsilocybeCropManager(), this);
        getServer().getPluginManager().registerEvents(new CrucibleListener(), this);
        getServer().getPluginManager().registerEvents(new StopwatchManager(), this);
        getServer().getPluginManager().registerEvents(new DrugUsing(), this);

        // Processing manager
        processingManager = new DrugProcessingManager();
        getServer().getPluginManager().registerEvents(processingManager, this);

        // Chemical processor
        chemicalProcessorManager = new ChemicalProcessorManager();
        getServer().getPluginManager().registerEvents(chemicalProcessorManager, this);

        // Crafting manager
        CraftingManager craftingManager = new CraftingManager();
        getServer().getPluginManager().registerEvents(craftingManager, this);
        craftingManager.registerRecipes();

        // Drying rack manager
        DryingRackManager dryingRackManager = new DryingRackManager();
        getServer().getPluginManager().registerEvents(dryingRackManager, this);

        // Commands
        getCommand("crucible").setExecutor(new CrucibleCommands());

        startToleranceDecay();

        getLogger().info("All systems loaded successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Crucible disabled!");
        instance = null;
    }

    public static CrucibleMain getInstance() {
        return instance;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

}
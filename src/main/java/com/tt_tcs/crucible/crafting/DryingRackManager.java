package com.tt_tcs.crucible.crafting;

import com.tt_tcs.crucible.CrucibleMain;
import com.tt_tcs.crucible.drugs.DrugItems;
import com.tt_tcs.crucible.util.DryingDataKeys;
import com.tt_tcs.crucible.util.ItemUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DryingRackManager implements Listener {

    public static final Material RACK_POST = Material.SCAFFOLDING;
    public static final Material RACK_TOP = Material.IRON_TRAPDOOR;

    private static final Map<String, DryingRecipe> RECIPES = new HashMap<>();
    static {
        RECIPES.put("dry_psilocybe_mushroom", new DryingRecipe(
                "dry_psilocybe_mushroom",
                "psilocybe_mushroom",
                "dried_psilocybe_mushroom",
                5
        ));
        RECIPES.put("dry_weed_leaf", new DryingRecipe(
                "dry_weed_leaf",
                "weed_leaf",
                "weed_bud",
                5
        ));
        RECIPES.put("dry_coca_leaf", new DryingRecipe(
                "dry_coca_leaf",
                "coca_leaf",
                "dried_coca_leaf",
                5
        ));
        RECIPES.put("dry_psilocybe_extract", new DryingRecipe(
                "dry_psilocybe_extract",
                "psilocybe_extract",
                "psilocybe",
                3
        ));
        RECIPES.put("ephedra_to_seeds", new DryingRecipe(
                "ephedra_to_seeds",
                "ephedra",
                "ephedra_seeds",
                0
        ));
        RECIPES.put("ephedra_harvested_to_seeds", new DryingRecipe(
                "ephedra_harvested_to_seeds",
                "ephedra_harvested",
                "ephedra_seeds",
                0
        ));
    }

    /* ================================================== */
    /*  YAML PERSISTENCE                                  */
    /* ================================================== */

    private final File dataFolder = new File(CrucibleMain.getInstance().getDataFolder(), "Crucible");
    private final File rackFile = new File(dataFolder, "drying_racks.yml");
    private FileConfiguration rackConfig;

    private void initStorage() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        if (!rackFile.exists()) {
            try {
                rackFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        rackConfig = YamlConfiguration.loadConfiguration(rackFile);
    }

    private void saveRackFile() {
        try {
            rackConfig.save(rackFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final Map<Location, RackJob> jobs = new HashMap<>();

    private static final Set<String> BAG_REQUIRED_OUTPUTS = Set.of(
            "cocaine",
            "white_meth",
            "blue_meth",
            "psilocybe",
            "fentanyl"
    );

    public DryingRackManager() {
        initStorage();
        loadRacks();

        new BukkitRunnable() {
            @Override public void run() {
                tickMinute();
            }
        }.runTaskTimer(CrucibleMain.getInstance(), 20L * 60L, 20L * 60L);

        new BukkitRunnable() {
            @Override public void run() {
                tickParticles();
            }
        }.runTaskTimer(CrucibleMain.getInstance(), 10L, 40L);
    }

    /* ================================================== */
    /*  LOAD SAVED RACKS                                  */
    /* ================================================== */

    private void loadRacks() {
        if (!rackConfig.contains("racks")) return;

        for (String key : rackConfig.getConfigurationSection("racks").getKeys(false)) {
            String[] p = key.split(":");
            World world = Bukkit.getWorld(UUID.fromString(p[0]));
            if (world == null) continue;

            Location base = new Location(world,
                    Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]),
                    Integer.parseInt(p[3])
            );

            RackJob job = new RackJob(base, null, null, 0, 0);
            job.loadFromConfig("racks." + key);
            jobs.put(base, job);
        }
    }

    private void tickMinute() {
        for (RackJob job : new ArrayList<>(jobs.values())) {
            job.tickMinute();
        }
    }

    private void tickParticles() {
        for (RackJob job : jobs.values()) {
            job.spawnParticles();
        }
    }

    private boolean isRack(Block base) {
        if (base == null) return false;
        if (base.getType() == RACK_TOP) {
            base = base.getRelative(0, -1, 0);
        }
        return base.getType() == RACK_POST && base.getRelative(0, 1, 0).getType() == RACK_TOP;
    }

    private Location rackBase(Block block) {
        return block.getType() == RACK_TOP
                ? block.getRelative(0, -1, 0).getLocation()
                : block.getLocation();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();

        // check if the broken block is part of a rack
        if (broken.getType() != RACK_POST && broken.getType() != RACK_TOP) {
            return;
        }

        // find the base of the rack
        Location base;
        if (broken.getType() == RACK_TOP) {
            Block post = broken.getRelative(0, -1, 0);
            if (post.getType() != RACK_POST) return;
            base = post.getLocation();
        } else {
            // broken is the post
            Block top = broken.getRelative(0, 1, 0);
            if (top.getType() != RACK_TOP) return;
            base = broken.getLocation();
        }

        // check if theres a job at this rack
        RackJob job = jobs.remove(base);
        if (job == null) {
            return;
        }

        // get the item and drop it
        ItemStack drop = job.withdraw();
        if (BAG_REQUIRED_OUTPUTS.contains(job.recipe.outputId) && ItemUtil.supportsQuality(drop)) {
            int q = ItemUtil.getQuality(drop);
            q = Math.max(0, q - 4);
            ItemUtil.setQuality(drop, q);
        }
        broken.getWorld().dropItemNaturally(
                base.clone().add(0.5, 0.5, 0.5),
                drop
        );

        job.deleteSave();

        broken.getWorld().playSound(base, Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (!isRack(clicked)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        Location key = rackBase(clicked);
        RackJob job = jobs.get(key);
        ItemStack held = event.getItem();

        // CLOCK: show drying progress
        if (held != null && held.getType() == Material.CLOCK) {
            if (job == null) {
                player.sendActionBar("§7This rack is empty!");
                return;
            }

            player.sendActionBar(String.format("§eDrying... %d minutes", job.elapsedMinutes));
            return;
        }

        // WITHDRAW ITEM
        if (job != null) {
            boolean dried = job.elapsedMinutes >= job.recipe.idealMinutes - 1;
            boolean needsBag = dried && BAG_REQUIRED_OUTPUTS.contains(job.recipe.outputId);

            if (needsBag) {
                if (held == null || held.getType() == Material.AIR) {
                    player.sendActionBar("§cYou need a Plastic Bag to collect this product!");
                    return;
                }
                if (!ItemUtil.isCustomItem(held, "plastic_bag")) return;

                held.setAmount(held.getAmount() - 1);

                ItemStack out = job.withdraw();
                jobs.remove(key);
                job.deleteSave();

                HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(out);
                if (!leftovers.isEmpty()) {
                    clicked.getWorld().dropItemNaturally(clicked.getLocation().add(0.5, 1.0, 0.5), out);
                }

                player.playSound(clicked.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                return;
            } else {
                if (held != null && held.getType() != Material.AIR) return;

                ItemStack out = job.withdraw();
                jobs.remove(key);
                job.deleteSave();

                HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(out);
                if (!leftovers.isEmpty()) {
                    clicked.getWorld().dropItemNaturally(clicked.getLocation().add(0.5, 1.0, 0.5), out);
                }

                player.playSound(clicked.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                return;
            }
        }

        if (job != null) return;
        if (held == null || held.getType() == Material.AIR) return;

        String heldId = ItemUtil.getItemId(held);

        DryingRecipe recipe = null;
        if (heldId != null) {
            recipe = RECIPES.values().stream()
                    .filter(r -> r.inputId.equalsIgnoreCase(heldId))
                    .findFirst()
                    .orElse(null);
        }

        if (recipe == null) {
            ItemMeta m = held.getItemMeta();
            if (m != null) {
                String recipeId = m.getPersistentDataContainer().get(
                        DryingDataKeys.DRYING_RECIPE_ID,
                        PersistentDataType.STRING
                );
                if (recipeId != null) {
                    recipe = RECIPES.get(recipeId);
                }
            }
        }

        if (recipe == null) {
            player.sendActionBar("§cThat can't be dried!");
            return;
        }

        int elapsed = 0;
        int baseQuality = ItemUtil.getQualityForRecipe(held);
        ItemMeta meta = held.getItemMeta();
        if (meta != null) {
            elapsed = meta.getPersistentDataContainer()
                    .getOrDefault(DryingDataKeys.DRYING_ELAPSED_MINUTES, PersistentDataType.INTEGER, 0);

            baseQuality = meta.getPersistentDataContainer()
                    .getOrDefault(DryingDataKeys.DRYING_BASE_QUALITY, PersistentDataType.INTEGER, baseQuality);
        }

        ItemStack one = held.clone();
        one.setAmount(1);
        held.setAmount(held.getAmount() - 1);

        RackJob newJob = new RackJob(key, recipe, one, elapsed, baseQuality);
        jobs.put(key, newJob);
        newJob.save();

        player.playSound(clicked.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.7f, 1.2f);
    }

    private static class DryingRecipe {
        final String recipeId;
        final String inputId;
        final String outputId;
        final int idealMinutes;

        DryingRecipe(String recipeId, String inputId, String outputId, int idealMinutes) {
            this.recipeId = recipeId;
            this.inputId = inputId;
            this.outputId = outputId;
            this.idealMinutes = idealMinutes;
        }
    }

    private class RackJob {
        final Location rack;
        DryingRecipe recipe;
        ItemStack inputItem;
        int baseQuality;
        int elapsedMinutes;

        RackJob(Location rack, DryingRecipe recipe, ItemStack inputItem, int elapsedMinutes, int baseQuality) {
            this.rack = rack;
            this.recipe = recipe;
            this.inputItem = inputItem;
            this.elapsedMinutes = elapsedMinutes;
            this.baseQuality = baseQuality;
        }

        /* ================================================== */
        /*  YAML PERSISTENCE METHODS                          */
        /* ================================================== */

        private String getRackKey() {
            return rack.getWorld().getUID() + ":" +
                    rack.getBlockX() + ":" +
                    rack.getBlockY() + ":" +
                    rack.getBlockZ();
        }

        void save() {
            String path = "racks." + getRackKey();

            rackConfig.set(path + ".recipeId", recipe.recipeId);
            rackConfig.set(path + ".elapsedMinutes", elapsedMinutes);
            rackConfig.set(path + ".baseQuality", baseQuality);

            rackConfig.set(path + ".inputItem", inputItem);

            saveRackFile();
        }

        void deleteSave() {
            rackConfig.set("racks." + getRackKey(), null);
            saveRackFile();
        }

        void loadFromConfig(String path) {
            String recipeId = rackConfig.getString(path + ".recipeId");
            this.recipe = RECIPES.get(recipeId);
            this.elapsedMinutes = rackConfig.getInt(path + ".elapsedMinutes");
            this.baseQuality = rackConfig.getInt(path + ".baseQuality");
            this.inputItem = rackConfig.getItemStack(path + ".inputItem");
        }

        void tickMinute() {
            elapsedMinutes++;
            save();
        }

        void spawnParticles() {
            World w = rack.getWorld();
            if (w == null) return;

            double x = rack.getX() + 0.5;
            double z = rack.getZ() + 0.5;

            w.spawnParticle(Particle.DRIPPING_WATER, x, rack.getY() + 0.8, z, 4, 0.3, 0, 0.3, 0);
            if (elapsedMinutes < recipe.idealMinutes) {
                w.spawnParticle(Particle.ASH, x, rack.getY() + 1.2, z, 3, 0.3, 0.05, 0.3, 0);
                if (Math.random() < 0.2) {
                    w.playSound(rack, Sound.BLOCK_BEEHIVE_DRIP, 0.3f, 1.4f);
                }
            }
        }

        ItemStack withdraw() {
            if (recipe != null && "ephedra_seeds".equalsIgnoreCase(recipe.outputId)) {
                return DrugItems.getById("ephedra_seeds").clone();
            }

            ItemStack out;
            boolean dried = elapsedMinutes >= recipe.idealMinutes - 1;

            if (dried) {
                out = DrugItems.getById(recipe.outputId).clone();
                int over = elapsedMinutes - recipe.idealMinutes;
                double decay = Math.max(0.0, 1.0 - (over * 0.05));
                int q = (int) Math.round(baseQuality * decay);
                if (ItemUtil.supportsQuality(out)) {
                    ItemUtil.setQuality(out, q);
                }

                ItemUtil.setMinutes(out, elapsedMinutes, recipe.idealMinutes, true);
            } else {
                out = inputItem.clone();

                if (ItemUtil.supportsQuality(out)) {
                    int under = recipe.idealMinutes - elapsedMinutes;
                    double decay = Math.max(0.0, 1.0 - (under * 0.05));
                    int q = (int) Math.round(baseQuality * decay);
                    ItemUtil.setQuality(out, q);
                }
                ItemUtil.setMinutes(out, elapsedMinutes, recipe.idealMinutes, false);
            }

            ItemMeta meta = out.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(
                        DryingDataKeys.DRYING_ELAPSED_MINUTES,
                        PersistentDataType.INTEGER,
                        elapsedMinutes
                );
                meta.getPersistentDataContainer().set(
                        DryingDataKeys.DRYING_BASE_QUALITY,
                        PersistentDataType.INTEGER,
                        baseQuality
                );
                meta.getPersistentDataContainer().set(
                        DryingDataKeys.DRYING_RECIPE_ID,
                        PersistentDataType.STRING,
                        recipe.recipeId
                );
                out.setItemMeta(meta);
            }

            return out;
        }
    }
}
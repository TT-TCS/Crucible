package com.tt_tcs.crucible.crafting;

import com.tt_tcs.crucible.CrucibleMain;
import com.tt_tcs.crucible.drugs.DrugItems;
import com.tt_tcs.crucible.util.ItemKeys;
import com.tt_tcs.crucible.util.ItemUtil;
import com.tt_tcs.crucible.util.ProcessingDataKeys;
import net.kyori.adventure.text.Component;
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
import java.util.stream.Collectors;

public class DrugProcessingManager implements Listener {

    /* ================================================== */
    /*  YAML PERSISTENCE                                  */
    /* ================================================== */

    private final File dataFolder = new File(CrucibleMain.getInstance().getDataFolder(), "Crucible");
    private final File machineFile = new File(dataFolder, "machines.yml");
    private FileConfiguration machineConfig;

    private void initStorage() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        if (!machineFile.exists()) {
            try {
                machineFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        machineConfig = YamlConfiguration.loadConfiguration(machineFile);
    }

    private void saveMachineFile() {
        try {
            machineConfig.save(machineFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* ================================================== */
    /*  RECIPES                                           */
    /* ================================================== */

    private static final Map<String, DrugRecipe> RECIPES = new HashMap<>();

    static {
        RECIPES.put("weed", new DrugRecipe(
                "weed",
                3,
                "weed_powder",
                Map.of(
                        "weed_bud",
                        3
                )));
        RECIPES.put("cocaine", new DrugRecipe(
                "cocaine",
                6,
                "cocaine",
                Map.of(
                        "dried_coca_leaf",
                        3
                )));
        RECIPES.put("ephedrine", new DrugRecipe(
                "ephedrine",
                2,
                "ephedrine",
                Map.of(
                        "ephedra",
                        3
                )));
        RECIPES.put("white_phosphorus", new DrugRecipe(
                "white_phosphorus",
                1,
                "white_phosphorus",
                Map.of(
                        "bone_meal",
                        1
                )));
        RECIPES.put("red_phosphorus", new DrugRecipe(
                "red_phosphorus",
                1,
                "red_phosphorus",
                Map.of(
                        "phosphorus_iodide",
                        1
                )));
        RECIPES.put("phosphorus_iodide", new DrugRecipe(
                "phosphorus_iodide",
                3,
                "phosphorus_iodide",
                Map.of(
                        "white_phosphorus",
                        1,
                        "iodine",
                        1
                )));
        RECIPES.put("white_meth", new DrugRecipe(
                "white_meth",
                8,
                "white_meth",
                Map.of(
                        "ephedrine",
                        2,
                        "red_phosphorus",
                        1,
                        "iodine",
                        1
                )));
        RECIPES.put("blue_meth", new DrugRecipe(
                "blue_meth",
                5,
                "blue_meth",
                Map.of(
                        "white_meth",
                        1,
                        "lapis_lazuli",
                        3
                )));
        RECIPES.put("psilocybe_seeds", new DrugRecipe(
                "psilocybe_seeds",
                5,
                "psilocybe_seeds",
                Map.of(
                        "brown_mushroom", 1,
                        "glowstone_dust", 2,
                        "redstone", 2
                )));
        RECIPES.put("psilocybe_extract", new DrugRecipe(
                "psilocybe_extract",
                3,
                "psilocybe_extract",
                Map.of(
                        "dried_psilocybe_mushroom", 1,
                        "water_bucket", 1
                ),
                Map.of(
                        "bucket", 1
                )));
        RECIPES.put("coal_tar_press", new DrugRecipe(
                "coal_tar_press",
                2,
                "coal_tar",
                Map.of(
                        "coal_sludge", 2
                ),
                Map.of(
                        "phenol", 1
                )));
    }

    private final Map<Location, ProcessingJob> jobs = new HashMap<>();
    private final Map<Location, Integer> soundTasks = new HashMap<>();

    private static final Set<String> BAG_REQUIRED_OUTPUTS = Set.of(
            "cocaine",
            "white_meth",
            "blue_meth",
            "psilocybe",
            "fentanyl"
    );

    public DrugProcessingManager() {
        initStorage();
        loadMachines();

        new BukkitRunnable() {
            @Override
            public void run() {
                tickJobs();
            }
        }.runTaskTimer(CrucibleMain.getInstance(), 0L, 20L);
    }

    /* ================================================== */
    /*  LOAD SAVED MACHINES                               */
    /* ================================================== */

    private void loadMachines() {
        if (!machineConfig.contains("machines")) return;

        for (String key : machineConfig.getConfigurationSection("machines").getKeys(false)) {
            String[] p = key.split(":");
            World world = Bukkit.getWorld(UUID.fromString(p[0]));
            if (world == null) continue;

            Location base = new Location(world,
                    Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]),
                    Integer.parseInt(p[3])
            );

            ProcessingJob job = new ProcessingJob(base);
            job.loadFromConfig("machines." + key);
            jobs.put(base, job);

            // restart machine sounds for locked machines
            if (job.isLocked() && !job.isReady()) {
                startMachineSound(base);
            }
        }
    }

    private void tickJobs() {
        for (ProcessingJob job : new ArrayList<>(jobs.values())) {
            job.tick();
        }
    }

    private void startMachineSound(Location base) {
        if (soundTasks.containsKey(base)) return;

        int taskId = new BukkitRunnable() {
            private boolean extend = true;
            float pitch = extend ? 0.6f : 0.8f;
            private boolean extend2 = true;
            float pitch2 = extend2 ? 1.0f : 0.8f;

            @Override
            public void run() {
                base.getWorld().playSound(base, Sound.BLOCK_PISTON_EXTEND, 0.15f, pitch);
                base.getWorld().playSound(base, Sound.BLOCK_GRINDSTONE_USE, 0.15f, pitch2);
                extend = !extend;
                extend2 = !extend2;
            }
        }.runTaskTimer(CrucibleMain.getInstance(), 0L, 20L).getTaskId();

        soundTasks.put(base, taskId);
    }

    private void stopMachineSound(Location base) {
        Integer taskId = soundTasks.remove(base);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Location base = getValidBase(block);
        if (base == null) return;

        event.setCancelled(true);

        ProcessingJob job = jobs.get(base);
        if (job == null) {
            job = new ProcessingJob(base);
            jobs.put(base, job);
        }

        ItemStack held = event.getItem();
        Player player = event.getPlayer();

        // CLOCK: show progress
        if (held != null && held.getType() == Material.CLOCK) {
            if (job.ingredients.isEmpty() || !job.isLocked()) {
                player.sendActionBar(Component.text("§7This machine hasn't started grinding!"));
                return;
            }

            int lockedMinutes = (int) job.getLockedMinutes();
            player.sendActionBar(Component.text(String.format("§eGrinding... %d minutes", lockedMinutes)));
            return;
        }

        // PLASTIC BAG: retrieve final products
        if (held != null && ItemUtil.isCustomItem(held, "plastic_bag")) {
            if (job.isReady()) {
                if (!job.tryGiveOutput(player, true, true)) {
                    player.sendActionBar(Component.text("§cUse paper to collect this product!"));
                }
                return;
            }

            if (job.isLocked()) {
                if (!job.tryGiveOutput(player, false, true)) {
                    player.sendActionBar(Component.text("§cUse paper to collect this product!"));
                }
                return;
            }

            return;
        }

        // PAPER: retrieve product
        if (held != null && held.getType() == Material.PAPER) {
            if (job.isReady()) {
                if (!job.tryGiveOutput(player, true, false)) {
                    player.sendActionBar(Component.text("§cYou need a Plastic Bag to collect this product!"));
                }
                return;
            }

            if (job.isLocked()) {
                if (!job.tryGiveOutput(player, false, false)) {
                    player.sendActionBar(Component.text("§cYou need a Plastic Bag to collect this product!"));
                }
                return;
            }

            return;
        }

        // EMPTY HAND: withdraw ingredients before processing
        if (held == null || held.getType() == Material.AIR) {
            if (job.isLocked() || job.isReady()) {
                player.sendActionBar(Component.text("§cUse paper to collect the product!"));
                return;
            }

            if (job.ingredients.isEmpty()) {
                return;
            }

            ItemStack withdrawn = job.withdrawLastItem();
            if (withdrawn != null) {
                player.getInventory().addItem(withdrawn);
                player.playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

                int remaining = job.getTotalIngredients();
                if (remaining > 0) {
                    player.sendActionBar(Component.text(String.format("§eWithdrew item! §fRemaining: %d", remaining)));
                } else {
                    player.sendActionBar(Component.text("§eWithdrew last item!"));
                }
            }
            return;
        }

        String itemId = ItemUtil.getItemId(held);

        if (itemId == null && held.getType() == Material.BONE_MEAL) {
            itemId = "bone_meal";
        }
        if (itemId == null && held.getType() == Material.BLUE_DYE) {
            itemId = "blue_dye";
        }
        if (itemId == null && held.getType() == Material.BROWN_MUSHROOM) {
            itemId = "brown_mushroom";
        }
        if (itemId == null && held.getType() == Material.GLOWSTONE_DUST) {
            itemId = "glowstone_dust";
        }
        if (itemId == null && held.getType() == Material.REDSTONE) {
            itemId = "redstone";
        }
        if (itemId == null && held.getType() == Material.WATER_BUCKET) {
            itemId = "water_bucket";
        }
        if (itemId == null && held.getType() == Material.PRISMARINE_SHARD) {
            itemId = "prismarine_shard";
        }

        String displayName = getItemDisplayName(held);

        if (itemId == null) return;

        if (job.isReady()) {
            player.sendActionBar(Component.text("§aClick with paper to collect."));
            return;
        }

        if (job.isLocked()) {
            player.sendActionBar(Component.text("§cThis machine is locked and processing!"));
            return;
        }

        if (job.addIngredient(itemId, held)) {
            player.playSound(block.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);

            Location particleLoc = base.clone().add(0.5, 1.5, 0.5);
            player.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 15, 0.3, 0.1, 0.3, 0.05);

            int total = job.getTotalIngredients();
            player.sendActionBar(Component.text(String.format("§aAdded %s! §aTotal items: %d", displayName, total)));
        } else {
            player.sendActionBar(Component.text("§cCannot add this item to the current recipe!"));
        }
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            Component displayName = item.getItemMeta().displayName();
            if (displayName != null) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName);
            }
        }

        String itemId = ItemUtil.getItemId(item);
        if (itemId == null) {
            if (item.getType() == Material.BONE_MEAL) {
                return "Bone Meal";
            } else if (item.getType() == Material.BLUE_DYE) {
                return "Blue Dye";
            } else if (item.getType() == Material.BROWN_MUSHROOM) {
                return "Brown Mushroom";
            } else if (item.getType() == Material.GLOWSTONE_DUST) {
                return "Glowstone Dust";
            } else if (item.getType() == Material.REDSTONE) {
                return "Redstone";
            } else if (item.getType() == Material.WATER_BUCKET) {
                return "Water Bucket";
            } else if (item.getType() == Material.LAPIS_LAZULI) {
                return "Lapis Lazuli";
            }
        }

        return formatIngredientName(itemId != null ? itemId : item.getType().name().toLowerCase());
    }

    private String formatIngredientName(String itemId) {
        if (itemId == null) return "Unknown";
        String[] parts = itemId.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!name.isEmpty()) name.append(" ");
            name.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return name.toString();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location base = getValidBase(event.getBlock());
        if (base == null) return;

        ProcessingJob job = jobs.remove(base);
        if (job != null) {
            job.dropAll();
        }

        stopMachineSound(base);
    }

    private Location getValidBase(Block block) {
        if (block.getType() == Material.SMITHING_TABLE) {
            if (block.getRelative(0, 1, 0).getType() == Material.GRINDSTONE) {
                if (block.getRelative(0, 2, 0).getType() == Material.PISTON) {
                    return block.getLocation();
                }
            }
        }

        if (block.getType() == Material.GRINDSTONE) {
            if (block.getRelative(0, -1, 0).getType() == Material.SMITHING_TABLE) {
                if (block.getRelative(0, 1, 0).getType() == Material.PISTON) {
                    return block.getRelative(0, -1, 0).getLocation();
                }
            }
        }

        if (block.getType() == Material.PISTON) {
            if (block.getRelative(0, -2, 0).getType() == Material.SMITHING_TABLE) {
                if (block.getRelative(0, -1, 0).getType() == Material.GRINDSTONE) {
                    return block.getRelative(0, -2, 0).getLocation();
                }
            }
        }

        return null;
    }

    private static String getColoredStars(int halfStars) {
        int fullStars = halfStars / 2;
        boolean half = (halfStars % 2) == 1;

        double stars = halfStars / 2.0;

        String color;
        if (stars > 4.0) {
            color = "§a";
        } else if (stars >= 2.5) {
            color = "§e";
        } else {
            color = "§c";
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

    private static String getLoreQuality(IngredientEntry entry) {
        if (entry == null) return getColoredStars(10);

        // if ingredient had no QUALITY key treat and display it as 5 stars
        // if ingredient had a QUALITY key display its stored value
        return entry.hadQuality ? getColoredStars(entry.storedQuality) : getColoredStars(10);
    }

    private static class IngredientEntry {
        final String itemId;

        final int storedQuality;
        final boolean hadQuality;

        final int calcQuality;

        // Snapshot of the exact item that was inserted (keeps full lore + PDC when withdrawing)
        final ItemStack originalItem;

        IngredientEntry(String itemId, int storedQuality, boolean hadQuality, int calcQuality, ItemStack originalItem) {
            this.itemId = itemId;
            this.storedQuality = storedQuality;
            this.hadQuality = hadQuality;
            this.calcQuality = calcQuality;
            this.originalItem = originalItem;
        }
    }

    /* ================================================== */
    /*  PROCESSING JOB WITH YAML PERSISTENCE              */
    /* ================================================== */

    private class ProcessingJob {

        private final Location base;
        private final Map<String, Integer> ingredients = new HashMap<>();
        private final List<IngredientEntry> ingredientList = new ArrayList<>();

        private boolean ready = false;
        private boolean locked = false;

        private long startTime = 0;
        private long lockTime = 0;

        private String drugId = null;
        private int idealMinutes = 0;

        private int processedQuality = 0;
        private int processedMinutes = 0;
        private int ingredientQualityStars = 0;

        private int lastSmokeSecond = -1;

        // Used to keep output quality/minutes updated even after "ready" (over/under processing)
        private long lastOutputCalcMillis = 0L;

        ProcessingJob(Location base) {
            this.base = base;
            this.startTime = System.currentTimeMillis();
        }

        /* ================================================== */
        /*  YAML PERSISTENCE METHODS                          */
        /* ================================================== */

        private String getMachineKey() {
            return base.getWorld().getUID() + ":" +
                    base.getBlockX() + ":" +
                    base.getBlockY() + ":" +
                    base.getBlockZ();
        }

        void save() {
            String path = "machines." + getMachineKey();

            machineConfig.set(path + ".drugId", drugId);
            machineConfig.set(path + ".idealMinutes", idealMinutes);
            machineConfig.set(path + ".startTime", startTime);
            machineConfig.set(path + ".lockTime", lockTime);
            machineConfig.set(path + ".locked", locked);
            machineConfig.set(path + ".ready", ready);
            machineConfig.set(path + ".processedQuality", processedQuality);
            machineConfig.set(path + ".processedMinutes", processedMinutes);
            machineConfig.set(path + ".ingredientQualityStars", ingredientQualityStars);

            // Legacy string list (kept for backwards compatibility)
            List<String> list = new ArrayList<>();
            for (IngredientEntry e : ingredientList) {
                list.add(e.itemId + ":" + e.storedQuality + ":" + (e.hadQuality ? 1 : 0));
            }
            machineConfig.set(path + ".ingredients", list);

            // Full item snapshots so withdrawals keep full lore + PDC (e.g. ingredient lore)
            List<Map<String, Object>> entryData = new ArrayList<>();
            for (IngredientEntry e : ingredientList) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", e.itemId);
                m.put("storedQuality", e.storedQuality);
                m.put("hadQuality", e.hadQuality);
                if (e.originalItem != null) {
                    ItemStack snap = e.originalItem.clone();
                    snap.setAmount(1);
                    m.put("item", snap);
                }
                entryData.add(m);
            }
            machineConfig.set(path + ".ingredientEntries", entryData);

            saveMachineFile();
        }

        void deleteSave() {
            machineConfig.set("machines." + getMachineKey(), null);
            saveMachineFile();
        }

        void loadFromConfig(String path) {
            drugId = machineConfig.getString(path + ".drugId");
            idealMinutes = machineConfig.getInt(path + ".idealMinutes");
            startTime = machineConfig.getLong(path + ".startTime");
            lockTime = machineConfig.getLong(path + ".lockTime");
            locked = machineConfig.getBoolean(path + ".locked");
            ready = machineConfig.getBoolean(path + ".ready");
            processedQuality = machineConfig.getInt(path + ".processedQuality");
            processedMinutes = machineConfig.getInt(path + ".processedMinutes");
            ingredientQualityStars = machineConfig.getInt(path + ".ingredientQualityStars");

            // Prefer the newer persisted format (full item snapshots)
            if (machineConfig.isList(path + ".ingredientEntries")) {
                List<Map<?, ?>> entries = machineConfig.getMapList(path + ".ingredientEntries");
                for (Map<?, ?> raw : entries) {
                    if (raw == null) continue;

                    String id = raw.get("id") == null ? null : String.valueOf(raw.get("id"));
                    if (id == null) continue;

                    int stored = 0;
                    boolean had = false;
                    Object storedObj = raw.get("storedQuality");
                    if (storedObj instanceof Number n) stored = n.intValue();
                    else if (storedObj != null) {
                        try { stored = Integer.parseInt(String.valueOf(storedObj)); } catch (Exception ignored) {}
                    }

                    Object hadObj = raw.get("hadQuality");
                    if (hadObj instanceof Boolean b) had = b;
                    else if (hadObj != null) had = "1".equals(String.valueOf(hadObj)) || "true".equalsIgnoreCase(String.valueOf(hadObj));

                    int calc = had ? stored : 10;

                    ItemStack snap = null;
                    Object itemObj = raw.get("item");
                    if (itemObj instanceof ItemStack is) {
                        snap = is.clone();
                        snap.setAmount(1);
                    }

                    ingredients.put(id, ingredients.getOrDefault(id, 0) + 1);
                    ingredientList.add(new IngredientEntry(id, stored, had, calc, snap));
                }
            } else {
                // Legacy format
                List<String> ingredientStrings = machineConfig.getStringList(path + ".ingredients");
                for (String s : ingredientStrings) {
                    String[] split = s.split(":");
                    String id = split[0];
                    int stored = split.length > 1 ? Integer.parseInt(split[1]) : 0;
                    boolean had;

                    if (split.length >= 3) {
                        had = "1".equals(split[2]) || "true".equalsIgnoreCase(split[2]);
                    } else {
                        had = stored > 0;
                    }

                    int calc = had ? stored : 10;

                    ingredients.put(id, ingredients.getOrDefault(id, 0) + 1);
                    ingredientList.add(new IngredientEntry(id, stored, had, calc, null));
                }
            }
        }

        boolean isReady() { return ready; }
        boolean isLocked() { return locked; }

        double getElapsedSeconds() {
            long now = System.currentTimeMillis();
            return (now - startTime) / 1000.0;
        }

        double getLockedMinutes() {
            if (!locked) return 0.0;
            return (System.currentTimeMillis() - lockTime) / 60000.0;
        }

        int getTotalIngredients() {
            return ingredients.values().stream().mapToInt(Integer::intValue).sum();
        }

        ItemStack withdrawLastItem() {
            if (ingredientList.isEmpty()) return null;

            IngredientEntry last = ingredientList.remove(ingredientList.size() - 1);

            // Prefer returning the exact original item (keeps full lore + PDC)
            if (last.originalItem != null) {
                ItemStack ret = last.originalItem.clone();
                ret.setAmount(1);
                // Update ingredient counts/reset timers below, then return
                String itemId = last.itemId;
                ingredients.put(itemId, ingredients.get(itemId) - 1);
                if (ingredients.get(itemId) <= 0) {
                    ingredients.remove(itemId);
                }

                if (ingredients.isEmpty()) {
                    drugId = null;
                    idealMinutes = 0;
                }

                startTime = System.currentTimeMillis();
                save();
                return ret;
            }
            String itemId = last.itemId;
            int storedQuality = last.storedQuality;
            boolean hadQuality = last.hadQuality;

            ingredients.put(itemId, ingredients.get(itemId) - 1);
            if (ingredients.get(itemId) <= 0) {
                ingredients.remove(itemId);
            }

            if (ingredients.isEmpty()) {
                drugId = null;
                idealMinutes = 0;
            }

            startTime = System.currentTimeMillis();

            ItemStack item;
            if (itemId.equals("bone_meal")) {
                item = new ItemStack(Material.BONE_MEAL);
            } else if (itemId.equals("blue_dye")) {
                item = new ItemStack(Material.BLUE_DYE);
            } else if (itemId.equals("brown_mushroom")) {
                item = new ItemStack(Material.BROWN_MUSHROOM);
            } else if (itemId.equals("glowstone_dust")) {
                item = new ItemStack(Material.GLOWSTONE_DUST);
            } else if (itemId.equals("redstone")) {
                item = new ItemStack(Material.REDSTONE);
            } else {
                item = DrugItems.getById(itemId);
                if (item != null) {
                    item = item.clone();
                    if (hadQuality) {
                        ItemUtil.setQuality(item, storedQuality);
                    } else {
                        ItemUtil.setQuality(item, 0);
                    }
                }
            }

            save();
            return item;
        }

        void tick() {
            if (ingredients.isEmpty()) return;

            double elapsed = getElapsedSeconds();

            if (!locked && elapsed >= 20.0) {
                locked = true;
                lockTime = System.currentTimeMillis();

                Location displayLoc = base.clone().add(0.5, 1.5, 0.5);
                base.getWorld().spawnParticle(
                        Particle.DUST_COLOR_TRANSITION,
                        displayLoc,
                        25,
                        0.3, 0.1, 0.3,
                        0.05,
                        new Particle.DustTransition(
                                org.bukkit.Color.fromRGB(255, 255, 255),
                                org.bukkit.Color.fromRGB(210, 210, 210),
                                1.0f
                        )
                );
                base.getWorld().playSound(displayLoc, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);

                DrugProcessingManager.this.startMachineSound(base);
                save();
            }

            if (!locked) return;

            int lockedSeconds = (int) ((System.currentTimeMillis() - lockTime) / 1000L);
            if (lockedSeconds >= 0 && lockedSeconds % 30 == 0 && lockedSeconds != lastSmokeSecond) {
                lastSmokeSecond = lockedSeconds;
                Location displayLoc = base.clone().add(0.5, 1.5, 0.5);
                base.getWorld().spawnParticle(
                        Particle.DUST_COLOR_TRANSITION,
                        displayLoc,
                        25,
                        0.3, 0.1, 0.3,
                        0.05,
                        new Particle.DustTransition(
                                org.bukkit.Color.fromRGB(255, 255, 255),
                                org.bukkit.Color.fromRGB(210, 210, 210),
                                1.0f
                        )
                );
            }

            double lockedMinutes = getLockedMinutes();
            if (lockedMinutes >= idealMinutes) {
                ready = true;
            }

            // Keep recalculating output so minutes/quality continue to change if left running.
            // (Fixes "stops at ideal minutes" issue.)
            long now = System.currentTimeMillis();
            if (now - lastOutputCalcMillis >= 1000L) {
                lastOutputCalcMillis = now;
                calculateOutput();
            }
        }

        boolean addIngredient(String itemId, ItemStack held) {
            DrugRecipe recipe = RECIPES.get(drugId);
            if (recipe == null) {
                recipe = findRecipeForItem(itemId, ingredients);
                if (recipe == null) return false;
                drugId = recipe.drugId;
                idealMinutes = recipe.idealMinutes;
            }

            if (!recipe.ingredients.containsKey(itemId)) return false;

            ingredients.put(itemId, ingredients.getOrDefault(itemId, 0) + 1);

            ItemMeta meta = held.getItemMeta();
            boolean hadQuality = meta != null && meta.getPersistentDataContainer().has(ItemKeys.QUALITY, PersistentDataType.INTEGER);
            int storedQuality = hadQuality ? ItemUtil.getQuality(held) : 0;

            int calcQuality = hadQuality ? storedQuality : 10;

            // Save an exact snapshot of the item before we decrement it
            ItemStack snapshot = held.clone();
            snapshot.setAmount(1);

            ingredientList.add(new IngredientEntry(itemId, storedQuality, hadQuality, calcQuality, snapshot));

            held.setAmount(held.getAmount() - 1);

            if (startTime == 0 || !locked) {
                startTime = System.currentTimeMillis();
                locked = false;
            }

            save();
            return true;
        }

        boolean tryGiveOutput(Player player, boolean requireReady, boolean usingPlasticBag) {
            if (drugId == null) return false;
            if (requireReady && !ready) return false;
            if (!requireReady && !locked) return false;

            DrugRecipe recipe = RECIPES.get(drugId);
            if (recipe == null) return false;

            boolean needsBag = BAG_REQUIRED_OUTPUTS.contains(recipe.outputItemId);
            if (needsBag && !usingPlasticBag) {
                return false;
            }
            if (!needsBag && usingPlasticBag) {
                return false;
            }

            if (!ready) {
                calculateOutput();
            }

            boolean recipeComplete = true;
            for (Map.Entry<String, Integer> entry : recipe.ingredients.entrySet()) {
                String id = entry.getKey();
                int required = entry.getValue();
                int actual = ingredients.getOrDefault(id, 0);

                if (actual != required) {
                    recipeComplete = false;
                    break;
                }
            }

            List<ItemStack> outputs = new ArrayList<>();
            ItemStack mainOutput;
            if (recipeComplete) {
                mainOutput = DrugItems.getProcessingOutput(recipe.outputItemId);
                if (mainOutput == null) {
                    player.sendMessage("§cError: Output item not found for " + recipe.outputItemId);
                    return false;
                }
                mainOutput = mainOutput.clone();
                mainOutput.setAmount(1);
                if (ItemUtil.getItemId(mainOutput) != null && ItemUtil.supportsQuality(mainOutput)) {
                    ItemUtil.setQuality(mainOutput, processedQuality);
                }
            } else {
                mainOutput = new ItemStack(Material.GUNPOWDER);
                ItemMeta wasteMeta = mainOutput.getItemMeta();
                if (wasteMeta != null) {
                    wasteMeta.displayName(Component.text("§8Chemical Waste"));
                    List<String> wasteLore = new ArrayList<>();
                    wasteLore.add("");
                    wasteLore.add("§7A useless byproduct of");
                    wasteLore.add("§7an incorrect recipe.");
                    wasteLore.add("§8----------");
                    wasteLore.add("§7Contents:");

                    for (IngredientEntry entry : ingredientList) {
                        wasteLore.add("§7 • " + formatIngredientName(entry.itemId) + " " + getLoreQuality(entry));
                    }

                    wasteMeta.setLore(wasteLore);
                    mainOutput.setItemMeta(wasteMeta);
                }
            }

            if (recipeComplete) {
                ItemMeta meta = mainOutput.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(ProcessingDataKeys.MINUTES_PROCESSED, PersistentDataType.INTEGER, processedMinutes);
                    meta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITY, PersistentDataType.INTEGER, ingredientQualityStars);

                    String ingredientNames = ingredientList.stream()
                            .map(e -> formatIngredientName(e.itemId))
                            .collect(Collectors.joining(","));
                    String ingredientQualities = ingredientList.stream()
                            .map(e -> String.valueOf(e.hadQuality ? e.storedQuality : 10))
                            .collect(Collectors.joining(","));

                    meta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_NAMES, PersistentDataType.STRING, ingredientNames);
                    meta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITIES, PersistentDataType.STRING, ingredientQualities);

                    List<String> lore = meta.getLore();
                    if (lore == null) {
                        lore = new ArrayList<>();
                    } else {
                        lore = new ArrayList<>(lore);
                    }

                    String minutesColor;
                    int timeDiff = Math.abs(processedMinutes - idealMinutes);
                    if (timeDiff == 0) {
                        minutesColor = "§a";
                    } else if (timeDiff <= 2) {
                        minutesColor = "§e";
                    } else {
                        minutesColor = "§c";
                    }

                    lore.add("");
                    lore.add(minutesColor + processedMinutes + " minutes processed");
                    lore.add("§8----------");
                    lore.add("§7Ingredients " + getColoredStars(ingredientQualityStars));

                    for (IngredientEntry entry : ingredientList) {
                        lore.add("§7 • " + formatIngredientName(entry.itemId) + " " + getLoreQuality(entry));
                    }

                    meta.setLore(lore);
                    mainOutput.setItemMeta(meta);
                }

                for (Map.Entry<String, Integer> eo : recipe.extraOutputs.entrySet()) {
                    String outId = eo.getKey();
                    int amount = eo.getValue();
                    if (amount <= 0) continue;

                    if ("bucket".equalsIgnoreCase(outId)) {
                        outputs.add(new ItemStack(Material.BUCKET, amount));
                        continue;
                    }

                    ItemStack extra = DrugItems.getById(outId);
                    if (extra == null) extra = DrugItems.getProcessingOutput(outId);
                    if (extra == null) continue;
                    extra = extra.clone();
                    extra.setAmount(amount);

                    if (ItemUtil.getItemId(extra) != null && ItemUtil.supportsQuality(extra)) {
                        ItemUtil.setQuality(extra, processedQuality);
                    }

                    ItemMeta extraMeta = extra.getItemMeta();
                    if (extraMeta != null) {
                        extraMeta.getPersistentDataContainer().set(ProcessingDataKeys.MINUTES_PROCESSED, PersistentDataType.INTEGER, processedMinutes);
                        extraMeta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITY, PersistentDataType.INTEGER, ingredientQualityStars);

                        String ingredientNames = ingredientList.stream()
                                .map(e -> formatIngredientName(e.itemId))
                                .collect(Collectors.joining(","));
                        String ingredientQualities = ingredientList.stream()
                                .map(e -> String.valueOf(e.hadQuality ? e.storedQuality : 10))
                                .collect(Collectors.joining(","));

                        extraMeta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_NAMES, PersistentDataType.STRING, ingredientNames);
                        extraMeta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITIES, PersistentDataType.STRING, ingredientQualities);

                        List<String> lore = extraMeta.getLore();
                        if (lore == null) {
                            lore = new ArrayList<>();
                        } else {
                            lore = new ArrayList<>(lore);
                        }

                        String minutesColor;
                        int timeDiff = Math.abs(processedMinutes - idealMinutes);
                        if (timeDiff == 0) {
                            minutesColor = "§a";
                        } else if (timeDiff <= 2) {
                            minutesColor = "§e";
                        } else {
                            minutesColor = "§c";
                        }

                        lore.add("");
                        lore.add(minutesColor + processedMinutes + " minutes processed");
                        lore.add("§8----------");
                        lore.add("§7Ingredients " + getColoredStars(ingredientQualityStars));

                        for (IngredientEntry entry : ingredientList) {
                            lore.add("§7 • " + formatIngredientName(entry.itemId) + " " + getLoreQuality(entry));
                        }

                        extraMeta.setLore(lore);
                        extra.setItemMeta(extraMeta);
                    }

                    outputs.add(extra);
                }
            }

            outputs.add(0, mainOutput);

            if (needsBag && recipeComplete) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (ItemUtil.isCustomItem(hand, "plastic_bag")) {
                    hand.setAmount(hand.getAmount() - 1);
                    if (hand.getAmount() <= 0) player.getInventory().setItemInMainHand(null);
                    else player.getInventory().setItemInMainHand(hand);
                } else {
                    ItemStack off = player.getInventory().getItemInOffHand();
                    if (ItemUtil.isCustomItem(off, "plastic_bag")) {
                        off.setAmount(off.getAmount() - 1);
                        if (off.getAmount() <= 0) player.getInventory().setItemInOffHand(null);
                        else player.getInventory().setItemInOffHand(off);
                    } else {
                        return false;
                    }
                }
            }

            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(Component.text("§cInventory full!"));
                for (ItemStack it : outputs) {
                    base.getWorld().dropItemNaturally(base.clone().add(0.5, 1.0, 0.5), it);
                }
            } else {
                for (ItemStack it : outputs) {
                    player.getInventory().addItem(it);
                }
                if (recipeComplete) {
                    player.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } else {
                    player.playSound(base, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
                    player.sendMessage("§cIncorrect recipe!");
                }
            }

            DrugProcessingManager.this.stopMachineSound(base);

            reset();
            deleteSave();
            return true;
        }

        void calculateOutput() {
            DrugRecipe recipe = RECIPES.get(drugId);
            if (recipe == null) return;

            ingredientQualityStars = calculateIngredientQuality(recipe);

            long totalMillis = locked ? (System.currentTimeMillis() - lockTime) : 0L;
            int totalMinutes = (int) Math.round(totalMillis / 60000.0);

            int timeDiff = Math.abs(totalMinutes - idealMinutes);
            double timePenalty = Math.min(timeDiff * 0.15, 0.9);

            double ingredientMultiplier = ingredientQualityStars / 10.0;

            double finalQuality = 10.0 * ingredientMultiplier * (1.0 - timePenalty);
            finalQuality = Math.max(0, Math.min(10, finalQuality));

            processedQuality = (int) Math.round(finalQuality);
            processedMinutes = totalMinutes;

            save();
        }

        int calculateIngredientQuality(DrugRecipe recipe) {
            int totalIngredients = recipe.ingredients.size();
            int correctIngredients = 0;

            for (Map.Entry<String, Integer> entry : recipe.ingredients.entrySet()) {
                String id = entry.getKey();
                int ideal = entry.getValue();
                int actual = ingredients.getOrDefault(id, 0);

                if (actual == ideal) {
                    correctIngredients++;
                }
            }

            double correctnessRatio = totalIngredients == 0 ? 1.0 : (double) correctIngredients / totalIngredients;

            double avgItemQuality = 0.0;
            if (!ingredientList.isEmpty()) {
                double sum = 0;
                for (IngredientEntry entry : ingredientList) {
                    sum += entry.calcQuality;
                }
                avgItemQuality = sum / ingredientList.size();
            } else {
                avgItemQuality = 5.0;
            }

            double finalQuality = correctnessRatio * avgItemQuality;
            return (int) Math.round(finalQuality);
        }

        String formatIngredientName(String itemId) {
            if (itemId == null) return "Unknown";
            String[] parts = itemId.split("_");
            StringBuilder name = new StringBuilder();
            for (String part : parts) {
                if (!name.isEmpty()) name.append(" ");
                name.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
            return name.toString();
        }

        void reset() {
            ingredients.clear();
            ingredientList.clear();
            drugId = null;
            idealMinutes = 0;
            ready = false;
            locked = false;
            startTime = System.currentTimeMillis();
            lockTime = 0;
            processedQuality = 0;
            processedMinutes = 0;
            ingredientQualityStars = 0;
            lastSmokeSecond = -1;
        }

        void dropAll() {
            World w = base.getWorld();
            Location dropLoc = base.clone().add(0.5, 1.0, 0.5);

            if (ready && drugId != null) {
                DrugRecipe recipe = RECIPES.get(drugId);
                if (recipe != null) {
                    boolean recipeComplete = true;
                    for (Map.Entry<String, Integer> entry : recipe.ingredients.entrySet()) {
                        String id = entry.getKey();
                        int required = entry.getValue();
                        int actual = ingredients.getOrDefault(id, 0);

                        if (actual != required) {
                            recipeComplete = false;
                            break;
                        }
                    }

                    List<ItemStack> outputs = new ArrayList<>();
                    ItemStack output;
                    if (recipeComplete) {
                        output = DrugItems.getProcessingOutput(recipe.outputItemId);
                        if (output != null) {
                            output = output.clone();
                            output.setAmount(1);
                            if (ItemUtil.getItemId(output) != null && ItemUtil.supportsQuality(output)) {
                                int qOut = processedQuality;
                                if (BAG_REQUIRED_OUTPUTS.contains(recipe.outputItemId)) {
                                    qOut = Math.max(0, qOut - 4);
                                }
                                ItemUtil.setQuality(output, qOut);
                            }

                            ItemMeta meta = output.getItemMeta();
                            if (meta != null) {
                                meta.getPersistentDataContainer().set(ProcessingDataKeys.MINUTES_PROCESSED, PersistentDataType.INTEGER, processedMinutes);
                                meta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITY, PersistentDataType.INTEGER, ingredientQualityStars);

                                String ingredientNames = ingredientList.stream()
                                        .map(e -> formatIngredientName(e.itemId))
                                        .collect(Collectors.joining(","));
                                String ingredientQualities = ingredientList.stream()
                                        .map(e -> String.valueOf(e.hadQuality ? e.storedQuality : 10))
                                        .collect(Collectors.joining(","));

                                meta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_NAMES, PersistentDataType.STRING, ingredientNames);
                                meta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITIES, PersistentDataType.STRING, ingredientQualities);

                                List<String> lore = meta.getLore();
                                if (lore == null) {
                                    lore = new ArrayList<>();
                                } else {
                                    lore = new ArrayList<>(lore);
                                }

                                String minutesColor;
                                int timeDiff = Math.abs(processedMinutes - idealMinutes);
                                if (timeDiff == 0) {
                                    minutesColor = "§a";
                                } else if (timeDiff <= 2) {
                                    minutesColor = "§e";
                                } else {
                                    minutesColor = "§c";
                                }

                                lore.add("");
                                lore.add(minutesColor + processedMinutes + " minutes processed");
                                lore.add("§8----------");
                                lore.add("§7Ingredients " + getColoredStars(ingredientQualityStars));

                                for (IngredientEntry entry : ingredientList) {
                                    lore.add("§7 • " + formatIngredientName(entry.itemId) + " " + getLoreQuality(entry));
                                }

                                meta.setLore(lore);
                                output.setItemMeta(meta);
                            }

                            outputs.add(output);

                            for (Map.Entry<String, Integer> eo : recipe.extraOutputs.entrySet()) {
                                String outId = eo.getKey();
                                int amount = eo.getValue();
                                if (amount <= 0) continue;

                                if ("bucket".equalsIgnoreCase(outId)) {
                                    outputs.add(new ItemStack(Material.BUCKET, amount));
                                    continue;
                                }

                                ItemStack extra = DrugItems.getById(outId);
                                if (extra == null) extra = DrugItems.getProcessingOutput(outId);
                                if (extra == null) continue;
                                extra = extra.clone();
                                extra.setAmount(amount);

                                if (ItemUtil.getItemId(extra) != null && ItemUtil.supportsQuality(extra)) {
                                    ItemUtil.setQuality(extra, processedQuality);
                                }

                                ItemMeta extraMeta = extra.getItemMeta();
                                if (extraMeta != null) {
                                    extraMeta.getPersistentDataContainer().set(ProcessingDataKeys.MINUTES_PROCESSED, PersistentDataType.INTEGER, processedMinutes);
                                    extraMeta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITY, PersistentDataType.INTEGER, ingredientQualityStars);

                                    String ingredientNames = ingredientList.stream()
                                            .map(e -> formatIngredientName(e.itemId))
                                            .collect(Collectors.joining(","));
                                    String ingredientQualities = ingredientList.stream()
                                            .map(e -> String.valueOf(e.hadQuality ? e.storedQuality : 10))
                                            .collect(Collectors.joining(","));

                                    extraMeta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_NAMES, PersistentDataType.STRING, ingredientNames);
                                    extraMeta.getPersistentDataContainer().set(ProcessingDataKeys.INGREDIENT_QUALITIES, PersistentDataType.STRING, ingredientQualities);

                                    List<String> lore = extraMeta.getLore();
                                    if (lore == null) {
                                        lore = new ArrayList<>();
                                    } else {
                                        lore = new ArrayList<>(lore);
                                    }

                                    String minutesColor;
                                    int timeDiff = Math.abs(processedMinutes - idealMinutes);
                                    if (timeDiff == 0) {
                                        minutesColor = "§a";
                                    } else if (timeDiff <= 2) {
                                        minutesColor = "§e";
                                    } else {
                                        minutesColor = "§c";
                                    }

                                    lore.add("");
                                    lore.add(minutesColor + processedMinutes + " minutes processed");
                                    lore.add("§8----------");
                                    lore.add("§7Ingredients " + getColoredStars(ingredientQualityStars));

                                    for (IngredientEntry entry : ingredientList) {
                                        lore.add("§7 • " + formatIngredientName(entry.itemId) + " " + getLoreQuality(entry));
                                    }

                                    extraMeta.setLore(lore);
                                    extra.setItemMeta(extraMeta);
                                }

                                outputs.add(extra);
                            }

                            for (ItemStack it : outputs) {
                                w.dropItemNaturally(dropLoc, it);
                            }
                        }
                    } else {
                        output = new ItemStack(Material.GUNPOWDER);
                        ItemMeta wasteMeta = output.getItemMeta();
                        if (wasteMeta != null) {
                            wasteMeta.displayName(Component.text("§8Chemical Waste"));
                            List<String> wasteLore = new ArrayList<>();
                            wasteLore.add("");
                            wasteLore.add("§7A useless byproduct of");
                            wasteLore.add("§7an incorrect recipe.");
                            wasteLore.add("");
                            wasteLore.add("§8----------");
                            wasteLore.add("§7Ingredients used:");

                            for (IngredientEntry entry : ingredientList) {
                                wasteLore.add("§7 • " + formatIngredientName(entry.itemId) + " " + getLoreQuality(entry));
                            }

                            wasteMeta.setLore(wasteLore);
                            output.setItemMeta(wasteMeta);
                        }
                        w.dropItemNaturally(dropLoc, output);
                    }

                    DrugProcessingManager.this.stopMachineSound(base);
                }

                deleteSave();
                return;
            }

            for (IngredientEntry entry : ingredientList) {
                ItemStack item;
                if (entry.itemId.equals("bone_meal")) {
                    item = new ItemStack(Material.BONE_MEAL);
                } else if (entry.itemId.equals("blue_dye")) {
                    item = new ItemStack(Material.BLUE_DYE);
                } else if (entry.itemId.equals("brown_mushroom")) {
                    item = new ItemStack(Material.BROWN_MUSHROOM);
                } else if (entry.itemId.equals("glowstone_dust")) {
                    item = new ItemStack(Material.GLOWSTONE_DUST);
                } else if (entry.itemId.equals("redstone")) {
                    item = new ItemStack(Material.REDSTONE);
                } else if (entry.itemId.equals("water_bucket")) {
                    item = new ItemStack(Material.WATER_BUCKET);
                } else {
                    item = DrugItems.getById(entry.itemId);
                    if (item != null) {
                        item = item.clone();
                        if (entry.hadQuality) {
                            ItemUtil.setQuality(item, entry.storedQuality);
                        } else {
                            ItemUtil.setQuality(item, 0);
                        }
                    }
                }
                if (item != null) {
                    w.dropItemNaturally(dropLoc, item);
                }
            }

            deleteSave();
        }

        private DrugRecipe findRecipeForItem(String itemId, Map<String, Integer> currentIngredients) {
            if (itemId == null) return null;

            List<DrugRecipe> candidates = new ArrayList<>();
            for (DrugRecipe r : RECIPES.values()) {
                if (r.ingredients.containsKey(itemId)) {
                    candidates.add(r);
                }
            }
            if (candidates.isEmpty()) return null;

            if (currentIngredients != null && !currentIngredients.isEmpty()) {
                candidates.removeIf(r -> {
                    for (String existing : currentIngredients.keySet()) {
                        if (!r.ingredients.containsKey(existing)) return true;
                    }
                    return false;
                });
            }
            if (candidates.isEmpty()) return null;

            candidates.sort((a, b) -> {
                int aDistinct = a.ingredients.size();
                int bDistinct = b.ingredients.size();
                if (aDistinct != bDistinct) return Integer.compare(aDistinct, bDistinct);

                int aTotal = a.ingredients.values().stream().mapToInt(Integer::intValue).sum();
                int bTotal = b.ingredients.values().stream().mapToInt(Integer::intValue).sum();
                if (aTotal != bTotal) return Integer.compare(aTotal, bTotal);

                if (a.idealMinutes != b.idealMinutes) return Integer.compare(a.idealMinutes, b.idealMinutes);

                return a.drugId.compareToIgnoreCase(b.drugId);
            });

            return candidates.get(0);
        }
    }

    private static class DrugRecipe {
        final String drugId;
        final int idealMinutes;
        final String outputItemId;
        final Map<String, Integer> extraOutputs;
        final Map<String, Integer> ingredients;

        DrugRecipe(String drugId, int idealMinutes, String outputItemId, Map<String, Integer> ingredients) {
            this(drugId, idealMinutes, outputItemId, ingredients, Map.of());
        }

        DrugRecipe(String drugId, int idealMinutes, String outputItemId, Map<String, Integer> ingredients, Map<String, Integer> extraOutputs) {
            this.drugId = drugId;
            this.idealMinutes = idealMinutes;
            this.outputItemId = outputItemId;
            this.ingredients = ingredients;
            this.extraOutputs = extraOutputs == null ? Map.of() : extraOutputs;
        }
    }
}
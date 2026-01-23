package com.tt_tcs.crucible.crafting;

import com.tt_tcs.crucible.CrucibleMain;
import com.tt_tcs.crucible.drugs.DrugItems;
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
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ChemicalProcessorManager implements Listener {

    // Block layout
    public static final Material BASE = Material.QUARTZ_BLOCK;
    public static final Material CAULDRON = Material.CAULDRON;
    public static final Material STAND = Material.BREWING_STAND;
    public static final Material LID = Material.IRON_TRAPDOOR;

    /* ================================================== */
    /*  YAML PERSISTENCE                                  */
    /* ================================================== */

    private final File dataFolder = new File(CrucibleMain.getInstance().getDataFolder(), "Crucible");
    private final File machineFile = new File(dataFolder, "chemical_machines.yml");
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

    private static final Map<String, ChemicalRecipe> RECIPES = new LinkedHashMap<>();

    static {
        // Pyridine + Gold Ingot -> Piperidine (gold returned)
        RECIPES.put("piperidine_processing", new ChemicalRecipe(
                "piperidine_processing",
                3,
                "piperidine",
                Map.of(
                        "pyridine", 1,
                        "gold_ingot", 1
                ),
                Map.of(
                        "gold_ingot", 1
                )
        ));

        // Ammonium Hydroxide + Bone Meal -> Ammonia
        RECIPES.put("ammonia_alkilisation", new ChemicalRecipe(
                "ammonia_alkilisation",
                1,
                "ammonia",
                Map.of(
                        "ammonium_hydroxide", 1,
                        "bone_meal", 1
                )
        ));

        // 2 Phenol + Ammonia -> Aniline
        RECIPES.put("aniline_processing", new ChemicalRecipe(
                "aniline_processing",
                5,
                "aniline",
                Map.of(
                        "phenol", 2,
                        "ammonia", 1
                )
        ));

        // 2 Piperidine + Aniline -> Fentanyl
        RECIPES.put("fentanyl_processing", new ChemicalRecipe(
                "fentanyl_processing",
                8,
                "fentanyl",
                Map.of(
                        "piperidine", 2,
                        "aniline", 1
                )
        ));
    
        // White Methamphetamine -> Methcathinone
        RECIPES.put("methcathinone_processing", new ChemicalRecipe(
                "methcathinone_processing",
                3,
                "methcathinone",
                Map.of(
                        "white_meth", 1
                )
        ));

}

    private final Map<Location, ChemicalJob> jobs = new HashMap<>();
    private final Map<Location, Integer> soundTasks = new HashMap<>();

    private static final Set<String> BAG_REQUIRED_OUTPUTS = Set.of(
            "fentanyl",
            "cocaine",
            "white_meth",
            "blue_meth",
            "psilocybe",
            "methcathinone"
    );

    // Some bag-required outputs had an unintended quality nerf. Keep the bag requirement,
    // but only apply the "bag penalty" where it was actually intended.
    private static final Set<String> BAG_QUALITY_PENALTY_OUTPUTS = Set.of(
            "fentanyl",
            "cocaine",
            "white_meth",
            "blue_meth",
            "psilocybe"
    );

    public ChemicalProcessorManager() {
        initStorage();
        loadMachines();

        new BukkitRunnable() {
            @Override
            public void run() {
                tickJobs();
            }
        }.runTaskTimer(CrucibleMain.getInstance(), 0L, 20L);
    }

    private void tickJobs() {
        for (ChemicalJob job : new ArrayList<>(jobs.values())) {
            job.tick();
        }
    }

    private void startBubblingSound(Location base) {
        if (soundTasks.containsKey(base)) return;

        int taskId = new BukkitRunnable() {
            private float pitch = 0.9f;
            private boolean up = true;

            @Override
            public void run() {
                World w = base.getWorld();
                if (w == null) return;

                Block cauldronBlock = getCauldronBlock(base);
                if (cauldronBlock == null) {
                    cancel();
                    soundTasks.remove(base);
                    return;
                }

                Location cauldron = cauldronBlock.getLocation().add(0.5, 0.9, 0.5);
                w.playSound(cauldron, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.35f, pitch);

                pitch += up ? 0.05f : -0.05f;
                if (pitch >= 1.15f) up = false;
                if (pitch <= 0.85f) up = true;
            }
        }.runTaskTimer(CrucibleMain.getInstance(), 0L, 20L).getTaskId();

        soundTasks.put(base, taskId);
    }

    private void stopBubblingSound(Location base) {
        Integer taskId = soundTasks.remove(base);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    /* ================================================== */
    /*  CAULDRON FINDING + PARTICLES                      */
    /* ================================================== */

    private Block getCauldronBlock(Location base) {
        if (base == null) return null;
        Block b = base.getBlock();
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            Block cauldron = b.getRelative(d[0], 0, d[1]);
            if (cauldron.getType() != CAULDRON) continue;
            Block air = cauldron.getRelative(0, 1, 0);
            if (!air.getType().isAir()) continue;
            Block lid = cauldron.getRelative(0, 2, 0);
            if (lid.getType() != LID) continue;
            return cauldron;
        }
        return null;
    }

    private static Color randomVibrantColor() {
        float h = ThreadLocalRandom.current().nextFloat();
        float s = 0.85f + ThreadLocalRandom.current().nextFloat() * 0.15f;
        float v = 0.85f + ThreadLocalRandom.current().nextFloat() * 0.15f;
        return hsvToBukkitColor(h, s, v);
    }

    private static Color hsvToBukkitColor(float h, float s, float v) {
        float r, g, b;
        int i = (int) Math.floor(h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return Color.fromRGB(
                Math.min(255, Math.max(0, Math.round(r * 255f))),
                Math.min(255, Math.max(0, Math.round(g * 255f))),
                Math.min(255, Math.max(0, Math.round(b * 255f)))
        );
    }

    /* ================================================== */
    /*  INTERACTION                                       */
    /* ================================================== */

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Location base = getValidBase(clicked);
        if (base == null) {
            // custom empty bottle items shouldn't collect water from cauldrons/water
            handleCustomBottleWaterBlock(event);
            return;
        }

        // block all vanilla interactions (trapdoor, brewing stand GUI, cauldron fill, etc.)
        event.setCancelled(true);

        ChemicalJob job = jobs.get(base);
        if (job == null) {
            job = new ChemicalJob(base);
            jobs.put(base, job);
        }

        Player player = event.getPlayer();
        ItemStack held = event.getItem();

        // CLOCK: show progress
        if (held != null && held.getType() == Material.CLOCK) {
            if (job.ingredients.isEmpty() || !job.locked) {
                player.sendActionBar(Component.text("§7This machine hasn't started processing!"));
                return;
            }
            int minutes = (int) job.getLockedMinutes();
            player.sendActionBar(Component.text("§eProcessing..." + minutes + " minutes"));
            return;
        }

        // PLASTIC BAG: collect final output
        if (held != null && ItemUtil.isCustomItem(held, "plastic_bag")) {
            if (job.ready) {
                if (!job.tryGiveOutput(player, true, true)) {
                    player.sendActionBar(Component.text("§cUse paper to collect this product!"));
                }
                return;
            }
            if (job.locked) {
                if (!job.tryGiveOutput(player, false, true)) {
                    player.sendActionBar(Component.text("§cUse paper to collect this product!"));
                }
                return;
            }
            return;
        }

        // PAPER: collect output
        if (held != null && held.getType() == Material.PAPER) {
            if (job.ready) {
                if (!job.tryGiveOutput(player, true, false)) {
                player.sendActionBar(Component.text("§cYou need a Plastic Bag to collect this product!"));
            }
                return;
            }
            if (job.locked) {
                if (!job.tryGiveOutput(player, false, false)) {
                player.sendActionBar(Component.text("§cYou need a Plastic Bag to collect this product!"));
            }
                return;
            }
            return;
        }

        // EMPTY HAND: withdraw ingredients only before processing
        if (held == null || held.getType() == Material.AIR) {
            if (job.locked || job.ready) {
                player.sendActionBar(Component.text("§cUse paper to collect the product!"));
                return;
            }

            ItemStack withdrawn = job.withdrawLastItem();
            if (withdrawn != null) {
                player.getInventory().addItem(withdrawn);
                player.playSound(clicked.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                int remaining = job.getTotalIngredients();
                if (remaining > 0) {
                    player.sendActionBar(Component.text("§eWithdrew item! §fRemaining: " + remaining));
                } else {
                    player.sendActionBar(Component.text("§eWithdrew last item!"));
                }
            }
            return;
        }

        if (job.ready) {
            player.sendActionBar(Component.text("§aDone! Click with paper to collect."));
            return;
        }

        if (job.locked) {
            player.sendActionBar(Component.text("§cThis processor is locked and running!"));
            return;
        }

        String itemId = mapItemId(held);
        if (itemId == null) {
            player.sendActionBar(Component.text("§cThat item can't be used here."));
            return;
        }

        if (job.addIngredient(itemId, held)) {
            World w = base.getWorld();
            if (w != null) {
                Block cauldronBlock = getCauldronBlock(base);
                if (cauldronBlock != null) {
                    Location cauldronTop = cauldronBlock.getLocation().add(0.5, 1.05, 0.5);
                    for (int i = 0; i < 2; i++) {
                        Color from = randomVibrantColor();
                        Color to = randomVibrantColor();
                        Particle.DustTransition dust = new Particle.DustTransition(from, to, 1.35f);
                        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, cauldronTop, 14, 0.18, 0.12, 0.18, 0.0, dust);
                    }
                    w.playSound(cauldronTop, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.4f, 1.0f);
                }
            }

            player.sendActionBar(Component.text("§aAdded ingredient! §fTotal items: " + job.getTotalIngredients()));
        } else {
            player.sendActionBar(Component.text("§cCannot add this item to the current recipe!"));
        }
    }

    private void handleCustomBottleWaterBlock(PlayerInteractEvent event) {
        ItemStack held = event.getItem();
        if (held == null) return;
        if (held.getType() != Material.GLASS_BOTTLE) return;

        String itemId = ItemUtil.getItemId(held);
        if (itemId == null) return;

        Block b = event.getClickedBlock();
        if (b == null) return;

        Material t = b.getType();
        if (t == Material.WATER) {
            event.setCancelled(true);
            return;
        }

        String name = t.name();
        if (name.contains("CAULDRON")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location base = getValidBase(event.getBlock());
        if (base == null) return;

        ChemicalJob job = jobs.remove(base);
        if (job != null) {
            job.dropAll();
            job.deleteSave();
        }
        stopBubblingSound(base);
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block clicked = event.getBlockClicked();
        if (clicked == null) return;

        Location base = getValidBase(clicked);
        if (base != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block clicked = event.getBlockClicked();
        if (clicked == null) return;

        Location base = getValidBase(clicked);
        if (base != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        // lab intermediates shouldn't be drinkable
        String id = ItemUtil.getItemId(item);
        if (id == null) return;

        if (id.equalsIgnoreCase("ammonium_hydroxide") || id.equalsIgnoreCase("aniline")) {
            event.setCancelled(true);
            Player p = event.getPlayer();
            p.sendActionBar(Component.text("§cYou probably shouldn't drink lab chemicals."));
        }
    }

    /* ================================================== */
    /*  MULTIBLOCK DETECTION                              */
    /* ================================================== */

    private Location getValidBase(Block block) {
        List<Block> candidates = new ArrayList<>();

        // if clicked the base itself
        if (block.getType() == BASE) {
            candidates.add(block);
        }

        // if clicked the brewing stand, base is directly below
        if (block.getType() == STAND) {
            candidates.add(block.getRelative(0, -1, 0));
        }

        // if clicked the cauldron, base is adjacent (any horizontal direction)
        if (block.getType() == CAULDRON) {
            candidates.add(block.getRelative(1, 0, 0));
            candidates.add(block.getRelative(-1, 0, 0));
            candidates.add(block.getRelative(0, 0, 1));
            candidates.add(block.getRelative(0, 0, -1));
        }

        // if clicked a trapdoor, base could be two blocks below it, or adjacent two blocks below.
        if (block.getType() == LID) {
            Block below2 = block.getRelative(0, -2, 0);
            candidates.add(below2);
            candidates.add(below2.getRelative(1, 0, 0));
            candidates.add(below2.getRelative(-1, 0, 0));
            candidates.add(below2.getRelative(0, 0, 1));
            candidates.add(below2.getRelative(0, 0, -1));
        }

        for (Block cand : candidates) {
            if (cand == null) continue;
            if (cand.getType() != BASE) continue;
            if (isValidAt(cand.getLocation())) return cand.getLocation();
        }

        return null;
    }

    private boolean isValidAt(Location base) {
        Block b = base.getBlock();
        if (b.getType() != BASE) return false;

        // brewing stand must be above the quartz
        Block stand = b.getRelative(0, 1, 0);
        if (stand.getType() != STAND) return false;

        // trapdoor above the brewing stand
        Block standLid = b.getRelative(0, 2, 0);
        if (standLid.getType() != LID) return false;

        // cauldron can be on any horizontal side.
        // above the cauldron must be air, and above that must be a trapdoor.
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            Block cauldron = b.getRelative(d[0], 0, d[1]);
            if (cauldron.getType() != CAULDRON) continue;

            Block air = cauldron.getRelative(0, 1, 0);
            if (!air.getType().isAir()) continue;

            Block cauldronLid = cauldron.getRelative(0, 2, 0);
            if (cauldronLid.getType() != LID) continue;

            return true;
        }

        return false;
    }

    /* ================================================== */
    /*  ITEM ID MAPPING                                   */
    /* ================================================== */

    private String mapItemId(ItemStack item) {
        String itemId = ItemUtil.getItemId(item);
        if (itemId != null) return itemId;

        return switch (item.getType()) {
            case BONE_MEAL -> "bone_meal";
            case QUARTZ -> "phenol";
            case GOLD_INGOT -> "gold_ingot";
            default -> null;
        };
    }

    /* ================================================== */
    /*  PERSISTENCE LOAD                                  */
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

            ChemicalJob job = new ChemicalJob(base);
            job.loadFromConfig("machines." + key);
            jobs.put(base, job);

            if (job.locked) {
                startBubblingSound(base);
            }
        }
    }

    private String keyOf(Location base) {
        return base.getWorld().getUID() + ":" + base.getBlockX() + ":" + base.getBlockY() + ":" + base.getBlockZ();
    }

    /* ================================================== */
    /*  RECIPE STRUCT                                     */
    /* ================================================== */

    private static class ChemicalRecipe {
        final String id;
        final int idealMinutes;
        final String outputItemId;
        final Map<String, Integer> ingredients;
        final Map<String, Integer> extraOutputs;

        ChemicalRecipe(String id, int idealMinutes, String outputItemId, Map<String, Integer> ingredients) {
            this(id, idealMinutes, outputItemId, ingredients, Map.of());
        }

        ChemicalRecipe(String id, int idealMinutes, String outputItemId, Map<String, Integer> ingredients, Map<String, Integer> extraOutputs) {
            this.id = id;
            this.idealMinutes = idealMinutes;
            this.outputItemId = outputItemId;
            this.ingredients = ingredients;
            this.extraOutputs = extraOutputs;
        }
    }

    private static class IngredientEntry {
        final String itemId;
        final int storedQuality;
        final boolean hadQuality;
        final int calcQuality;
        final ItemStack originalItem;

        IngredientEntry(String itemId, int storedQuality, boolean hadQuality, int calcQuality, ItemStack originalItem) {
            this.itemId = itemId;
            this.storedQuality = storedQuality;
            this.hadQuality = hadQuality;
            this.calcQuality = calcQuality;
            this.originalItem = originalItem;
        }
    }

    private static String formatIngredientName(String itemId) {
        if (itemId == null) return "Unknown";
        String[] parts = itemId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }
        return sb.toString().trim();
    }

    private static String getLoreQuality(IngredientEntry entry) {
        // If the ingredient had no QUALITY key at all, treat and display it as 5 stars (10 half-stars)
        return entry.hadQuality ? ItemUtil.coloredStars(entry.storedQuality) : ItemUtil.coloredStars(10);
    }

    private static int calculateIngredientQualityStars(List<IngredientEntry> ingredientList) {
        if (ingredientList == null || ingredientList.isEmpty()) return 10;
        double sum = 0.0;
        for (IngredientEntry e : ingredientList) sum += e.calcQuality;
        return (int) Math.round(sum / ingredientList.size());
    }

    private static int calculateProcessedQuality(int ingredientQualityStars, int processedMinutes, int idealMinutes) {
        idealMinutes = Math.max(1, idealMinutes);
        int timeDiff = Math.abs(processedMinutes - idealMinutes);
        double timePenalty = Math.min(timeDiff * 0.15, 0.9);
        double ingredientMultiplier = ingredientQualityStars / 10.0;
        double finalQuality = 10.0 * ingredientMultiplier * (1.0 - timePenalty);
        finalQuality = max(0.0, min(10.0, finalQuality));
        return (int) Math.round(finalQuality);
    }

    private static double max(double a, double b) { return a > b ? a : b; }
    private static double min(double a, double b) { return a < b ? a : b; }

    private static void applyProcessingMetadataAndLore(ItemStack item,
                                                       int processedQuality,
                                                       int processedMinutes,
                                                       int idealMinutes,
                                                       int ingredientQualityStars,
                                                       List<IngredientEntry> ingredientList) {
        if (item == null) return;
        // only lore custom items (vanilla catalysts/returns like gold ingots should remain vanilla)
        if (ItemUtil.getItemId(item) == null) return;

        if (ItemUtil.supportsQuality(item)) {
            ItemUtil.setQuality(item, processedQuality);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

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
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        String minutesColor;
        int timeDiff = Math.abs(processedMinutes - Math.max(1, idealMinutes));
        if (timeDiff == 0) minutesColor = "§a";
        else if (timeDiff <= 2) minutesColor = "§e";
        else minutesColor = "§c";

        lore.add("");
        lore.add(minutesColor + processedMinutes + " minutes processed");
        lore.add("§8----------");
        lore.add("§7Ingredients " + ItemUtil.coloredStars(ingredientQualityStars));
        for (IngredientEntry entry : ingredientList) {
            lore.add("§7 • " + formatIngredientName(entry.itemId) + " " + getLoreQuality(entry));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /* ================================================== */
    /*  JOB                                               */
    /* ================================================== */

    private class ChemicalJob {
        private final Location base;

        private final Map<String, Integer> ingredients = new HashMap<>();
        private final List<IngredientEntry> ingredientList = new ArrayList<>();

        private String recipeId;
        private boolean locked;
        private boolean ready;
        private long lockTime;

        ChemicalJob(Location base) {
            this.base = base;
        }

        void tick() {
            if (!locked) {
                String match = matchRecipeId();
                if (match != null) {
                    recipeId = match;
                    locked = true;
                    lockTime = System.currentTimeMillis();
                    startBubblingSound(base);
                    save();
                }
                return;
            }

            ChemicalRecipe recipe = RECIPES.get(recipeId);
            if (recipe == null) {
                reset();
                return;
            }

            long millis = System.currentTimeMillis() - lockTime;
            double minutes = millis / 60000.0;

            if (minutes >= recipe.idealMinutes) {
                if (!ready) {
                    ready = true;
                    save();
                }
            }

            // randomly emit vibrant particles from the top of the cauldron while processing
            maybeSpawnProcessingParticles();
        }

        private void maybeSpawnProcessingParticles() {
            if (!locked) return;
            World w = base.getWorld();
            if (w == null) return;
            Block cauldronBlock = getCauldronBlock(base);
            if (cauldronBlock == null) return;

            // chance per second to emit
            if (ThreadLocalRandom.current().nextDouble() > 0.45) return;

            Location top = cauldronBlock.getLocation().add(0.5, 1.08, 0.5);
            int count = ThreadLocalRandom.current().nextInt(8, 16);
            Color from = randomVibrantColor();
            Color to = randomVibrantColor();
            Particle.DustTransition dust = new Particle.DustTransition(from, to, 1.5f);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, top, count, 0.18, 0.06, 0.18, 0.0, dust);
        }

        double getLockedMinutes() {
            if (!locked) return 0;
            return (System.currentTimeMillis() - lockTime) / 60000.0;
        }

        int getTotalIngredients() {
            int total = 0;
            for (int v : ingredients.values()) total += v;
            return total;
        }

        boolean addIngredient(String itemId, ItemStack held) {
            // if recipe is empty, allow any item that is part of any recipe
            if (ingredients.isEmpty()) {
                if (!isItemInAnyRecipe(itemId)) return false;
            } else {
                // if already building toward a recipe, only allow items that keep at least one recipe possible
                if (!wouldStillBePossible(itemId)) return false;
            }

            ingredients.put(itemId, ingredients.getOrDefault(itemId, 0) + 1);
            int storedQuality = ItemUtil.hasQualityKey(held) ? ItemUtil.getQuality(held) : 0;
            boolean hadQuality = ItemUtil.hasQualityKey(held);
            int calcQuality = ItemUtil.getQualityForRecipe(held);
            ItemStack saved = held.clone();
            saved.setAmount(1);
            ingredientList.add(new IngredientEntry(itemId, storedQuality, hadQuality, calcQuality, saved));

            held.setAmount(held.getAmount() - 1);
            save();
            return true;
        }

        ItemStack withdrawLastItem() {
            if (ingredientList.isEmpty()) return null;

            IngredientEntry last = ingredientList.remove(ingredientList.size() - 1);

            String lastId = last.itemId;
            int current = ingredients.getOrDefault(lastId, 0);
            if (current <= 1) {
                ingredients.remove(lastId);
            } else {
                ingredients.put(lastId, current - 1);
            }

            save();

            // prefer returning the exact original item (keeps full lore + PDC)
            if (last.originalItem != null) {
                ItemStack ret = last.originalItem.clone();
                ret.setAmount(1);
                return ret;
            }

            ItemStack it;
            if ("phenol".equalsIgnoreCase(lastId)) {
                it = DrugItems.getProcessingOutput("phenol");
                if (it == null) it = new ItemStack(Material.QUARTZ);
            } else if ("gold_ingot".equalsIgnoreCase(lastId)) {
                it = new ItemStack(Material.GOLD_INGOT);
            } else if ("bone_meal".equalsIgnoreCase(lastId)) {
                it = new ItemStack(Material.BONE_MEAL);
            } else {
                it = DrugItems.getById(lastId);
                if (it == null) it = DrugItems.getProcessingOutput(lastId);
                if (it == null) return null;
            }

            it = it.clone();
            it.setAmount(1);
            return it;
        }

        boolean tryGiveOutput(Player player, boolean requireReady, boolean usingPlasticBag) {
            if (recipeId == null) return false;
            if (requireReady && !ready) return false;
            if (!requireReady && !locked) return false;

            ChemicalRecipe recipe = RECIPES.get(recipeId);
            if (recipe == null) return false;

            boolean needsBag = BAG_REQUIRED_OUTPUTS.contains(recipe.outputItemId);
            if (needsBag && !usingPlasticBag) return false;
            if (!needsBag && usingPlasticBag) return false;

            boolean recipeComplete = isExactMatch(recipe);

            List<ItemStack> outputs = new ArrayList<>();
            ItemStack main;

            if (recipeComplete) {
                main = DrugItems.getProcessingOutput(recipe.outputItemId);
                if (main == null) main = DrugItems.getById(recipe.outputItemId);
                if (main == null) {
                    player.sendMessage("§cError: Output item not found for " + recipe.outputItemId);
                    return false;
                }
                main = main.clone();
                main.setAmount(1);

                // extra outputs (e.g. return catalyst)
                for (Map.Entry<String, Integer> eo : recipe.extraOutputs.entrySet()) {
                    String outId = eo.getKey();
                    int amount = eo.getValue();
                    if (amount <= 0) continue;
                    ItemStack extra = DrugItems.getProcessingOutput(outId);
                    if (extra == null) extra = DrugItems.getById(outId);
                    if (extra == null) {
                        // vanilla fallbacks
                        if ("gold_ingot".equalsIgnoreCase(outId)) extra = new ItemStack(Material.GOLD_INGOT);
                    }
                    if (extra == null) continue;
                    extra = extra.clone();
                    extra.setAmount(amount);
                    outputs.add(extra);
                }

                outputs.add(0, main);

                // apply full processing metadata/lore to the main output and any custom byproducts
                int processedMinutes = (int) Math.round((System.currentTimeMillis() - lockTime) / 60000.0);
                int ingredientQualityStars = calculateIngredientQualityStars(ingredientList);
                int processedQuality = calculateProcessedQuality(ingredientQualityStars, processedMinutes, recipe.idealMinutes);

                if (BAG_QUALITY_PENALTY_OUTPUTS.contains(recipe.outputItemId)) {
                        processedQuality = Math.max(0, processedQuality - 4);
                    }

                for (ItemStack out : outputs) {
                    applyProcessingMetadataAndLore(out, processedQuality, processedMinutes, recipe.idealMinutes, ingredientQualityStars, ingredientList);
                }

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
                    for (ItemStack it : outputs) {
                        base.getWorld().dropItemNaturally(base.clone().add(0.5, 1.0, 0.5), it);
                    }
                    player.sendMessage(Component.text("§cInventory full! Dropped outputs."));
                } else {
                    for (ItemStack it : outputs) {
                        player.getInventory().addItem(it);
                    }
                    player.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            } else {
                // incorrect recipe -> chemical waste
                main = new ItemStack(Material.GUNPOWDER);
                var meta = main.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("§8Chemical Waste"));
                    main.setItemMeta(meta);
                }
                base.getWorld().dropItemNaturally(base.clone().add(0.5, 1.0, 0.5), main);
                player.playSound(base, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
                player.sendMessage(Component.text("§cIncorrect recipe! Produced chemical waste."));
            }

            reset();
            deleteSave();
            return true;
        }

        void dropAll() {
            Location drop = base.clone().add(0.5, 1.0, 0.5);
            World w = base.getWorld();
            if (w == null) return;

            // if the processor is running breaking is force-collect
            if (locked && recipeId != null) {
                ChemicalRecipe recipe = RECIPES.get(recipeId);
                if (recipe != null && isExactMatch(recipe)) {
                    List<ItemStack> outputs = new ArrayList<>();
                    ItemStack main = DrugItems.getProcessingOutput(recipe.outputItemId);
                    if (main == null) main = DrugItems.getById(recipe.outputItemId);
                    if (main != null) {
                        main = main.clone();
                        main.setAmount(1);
                        outputs.add(main);
                    }

                    for (Map.Entry<String, Integer> eo : recipe.extraOutputs.entrySet()) {
                        String outId = eo.getKey();
                        int amount = eo.getValue();
                        if (amount <= 0) continue;
                        ItemStack extra = DrugItems.getProcessingOutput(outId);
                        if (extra == null) extra = DrugItems.getById(outId);
                        if (extra == null) {
                            if ("gold_ingot".equalsIgnoreCase(outId)) extra = new ItemStack(Material.GOLD_INGOT);
                        }
                        if (extra == null) continue;
                        extra = extra.clone();
                        extra.setAmount(amount);
                        outputs.add(extra);
                    }

                    int processedMinutes = (int) Math.round((System.currentTimeMillis() - lockTime) / 60000.0);
                    int ingredientQualityStars = calculateIngredientQualityStars(ingredientList);
                    int processedQuality = calculateProcessedQuality(ingredientQualityStars, processedMinutes, recipe.idealMinutes);

                    if (BAG_QUALITY_PENALTY_OUTPUTS.contains(recipe.outputItemId)) {
                        processedQuality = Math.max(0, processedQuality - 4);
                    }

                    for (ItemStack out : outputs) {
                        applyProcessingMetadataAndLore(out, processedQuality, processedMinutes, recipe.idealMinutes, ingredientQualityStars, ingredientList);
                        w.dropItemNaturally(drop, out);
                    }

                    reset();
                    return;
                }
            }

            for (IngredientEntry entry : new ArrayList<>(ingredientList)) {
                ItemStack it = (entry.originalItem != null) ? entry.originalItem.clone() : null;
                if (it == null) {
                    String id = entry.itemId;
                    if ("phenol".equalsIgnoreCase(id)) {
                        it = DrugItems.getProcessingOutput("phenol");
                        if (it == null) it = new ItemStack(Material.QUARTZ);
                    } else if ("gold_ingot".equalsIgnoreCase(id)) {
                        it = new ItemStack(Material.GOLD_INGOT);
                    } else if ("bone_meal".equalsIgnoreCase(id)) {
                        it = new ItemStack(Material.BONE_MEAL);
                    } else {
                        it = DrugItems.getById(id);
                        if (it == null) it = DrugItems.getProcessingOutput(id);
                        if (it == null) continue;
                    }
                }
                it.setAmount(1);
                w.dropItemNaturally(drop, it);
            }

            reset();
        }

        private boolean isExactMatch(ChemicalRecipe recipe) {
            for (Map.Entry<String, Integer> req : recipe.ingredients.entrySet()) {
                if (ingredients.getOrDefault(req.getKey(), 0) != req.getValue()) return false;
            }
            for (Map.Entry<String, Integer> have : ingredients.entrySet()) {
                if (!recipe.ingredients.containsKey(have.getKey())) return false;
                if (!recipe.ingredients.get(have.getKey()).equals(have.getValue())) return false;
            }
            return true;
        }

        private boolean isItemInAnyRecipe(String itemId) {
            for (ChemicalRecipe r : RECIPES.values()) {
                if (r.ingredients.containsKey(itemId)) return true;
            }
            return false;
        }

        private boolean wouldStillBePossible(String addId) {
            Map<String, Integer> temp = new HashMap<>(ingredients);
            temp.put(addId, temp.getOrDefault(addId, 0) + 1);

            for (ChemicalRecipe r : RECIPES.values()) {
                boolean ok = true;
                for (Map.Entry<String, Integer> have : temp.entrySet()) {
                    int required = r.ingredients.getOrDefault(have.getKey(), -1);
                    if (required == -1) {
                        ok = false;
                        break;
                    }
                    if (have.getValue() > required) {
                        ok = false;
                        break;
                    }
                }
                if (ok) return true;
            }
            return false;
        }

        private String matchRecipeId() {
            for (ChemicalRecipe r : RECIPES.values()) {
                if (isExactMatch(r)) {
                    return r.id;
                }
            }
            return null;
        }

        void reset() {
            ingredients.clear();
            ingredientList.clear();
            recipeId = null;
            locked = false;
            ready = false;
            lockTime = 0L;
            stopBubblingSound(base);
            save();
        }

        void save() {
            String key = keyOf(base);
            String path = "machines." + key;

            machineConfig.set(path + ".recipeId", recipeId);
            machineConfig.set(path + ".locked", locked);
            machineConfig.set(path + ".ready", ready);
            machineConfig.set(path + ".lockTime", lockTime);
            List<Map<String, Object>> ingredientEntries = new ArrayList<>();
            List<String> enc = new ArrayList<>();
            for (IngredientEntry e : ingredientList) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", e.itemId);
                m.put("storedQuality", e.storedQuality);
                m.put("hadQuality", e.hadQuality);
                m.put("calcQuality", e.calcQuality);
                if (e.originalItem != null) {
                    m.put("item", e.originalItem);
                }
                ingredientEntries.add(m);

                enc.add(e.itemId + ":" + e.storedQuality + ":" + (e.hadQuality ? 1 : 0));
            }
            machineConfig.set(path + ".ingredientEntries", ingredientEntries);
            machineConfig.set(path + ".ingredientList", enc);

            Map<String, Integer> map = new HashMap<>(ingredients);
            machineConfig.set(path + ".ingredients", map);

            saveMachineFile();
        }

        void loadFromConfig(String path) {
            recipeId = machineConfig.getString(path + ".recipeId", null);
            locked = machineConfig.getBoolean(path + ".locked", false);
            ready = machineConfig.getBoolean(path + ".ready", false);
            lockTime = machineConfig.getLong(path + ".lockTime", 0L);

            ingredientList.clear();
            List<?> rawEntries = machineConfig.getList(path + ".ingredientEntries", null);
            if (rawEntries != null && !rawEntries.isEmpty()) {
                for (Object o : rawEntries) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    Object idObj = m.get("id");
                    String itemId = (idObj == null) ? "" : String.valueOf(idObj);

                    int storedQuality = 0;
                    boolean hadQuality = false;
                    int calcQuality = 10;

                    Object sqObj = m.get("storedQuality");
                    if (sqObj instanceof Number n) {
                        storedQuality = n.intValue();
                    }

                    Object hqObj = m.get("hadQuality");
                    if (hqObj instanceof Boolean b) {
                        hadQuality = b;
                    } else if (hqObj != null) {
                        hadQuality = "true".equalsIgnoreCase(String.valueOf(hqObj)) || "1".equals(String.valueOf(hqObj));
                    }

                    Object cqObj = m.get("calcQuality");
                    if (cqObj instanceof Number n) {
                        calcQuality = n.intValue();
                    } else {
                        calcQuality = hadQuality ? storedQuality : 10;
                    }

                    ItemStack original = null;
                    try {
                        Object it = m.get("item");
                        if (it instanceof ItemStack stack) original = stack;
                    } catch (Exception ignored) {}

                    ingredientList.add(new IngredientEntry(itemId, storedQuality, hadQuality, calcQuality, original));
                }
            } else {
                for (String line : machineConfig.getStringList(path + ".ingredientList")) {
                    try {
                        String[] parts = line.split(":");
                        String itemId = parts.length > 0 ? parts[0] : line;
                        int storedQuality = (parts.length > 1) ? Integer.parseInt(parts[1]) : 0;
                        boolean hadQuality = (parts.length > 2) && ("1".equals(parts[2]) || "true".equalsIgnoreCase(parts[2]));
                        int calcQuality = hadQuality ? storedQuality : 10;
                        ingredientList.add(new IngredientEntry(itemId, storedQuality, hadQuality, calcQuality, null));
                    } catch (Exception ex) {
                        ingredientList.add(new IngredientEntry(line, 0, false, 10, null));
                    }
                }
            }

            ingredients.clear();
            if (machineConfig.isConfigurationSection(path + ".ingredients")) {
                for (String k : machineConfig.getConfigurationSection(path + ".ingredients").getKeys(false)) {
                    ingredients.put(k, machineConfig.getInt(path + ".ingredients." + k));
                }
            }
        }

        void deleteSave() {
            String key = keyOf(base);
            machineConfig.set("machines." + key, null);
            saveMachineFile();
        }
    }
}

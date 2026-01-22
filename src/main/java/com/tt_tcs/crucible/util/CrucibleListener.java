package com.tt_tcs.crucible.util;

import com.tt_tcs.crucible.drugs.DrugEffect;
import com.tt_tcs.crucible.drugs.DrugType;
import com.tt_tcs.crucible.drugs.DrugUsing;
import com.tt_tcs.crucible.drugs.MushroomHallucinationManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Sound;

import com.tt_tcs.crucible.CrucibleMain;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class CrucibleListener implements Listener {

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

    private static void renderInventoryLoreFor(Player player) {
        boolean bedrock = isBedrockPlayer(player);
        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            ItemUtil.renderLoreForClient(it, bedrock);
        }
        for (ItemStack it : player.getInventory().getArmorContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            ItemUtil.renderLoreForClient(it, bedrock);
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType() != Material.AIR) ItemUtil.renderLoreForClient(off, bedrock);
    }

    private static void renderInventoryLoreFor(Player player, org.bukkit.inventory.Inventory inv) {
        if (inv == null) return;
        boolean bedrock = isBedrockPlayer(player);
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            ItemUtil.renderLoreForClient(it, bedrock);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(CrucibleMain.getInstance(), () -> renderInventoryLoreFor(p), 1L);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        Bukkit.getScheduler().runTaskLater(CrucibleMain.getInstance(), () -> renderInventoryLoreFor(p), 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        Bukkit.getScheduler().runTaskLater(CrucibleMain.getInstance(), () -> {
            renderInventoryLoreFor(p);
            renderInventoryLoreFor(p, event.getView().getTopInventory());
        }, 1L);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        Bukkit.getScheduler().runTaskLater(CrucibleMain.getInstance(), () -> {
            renderInventoryLoreFor(p);
            renderInventoryLoreFor(p, event.getView().getTopInventory());
        }, 1L);
    }

    // ==================== FENTANYL DAMAGE STORAGE ====================

    private static final NamespacedKey FENT_WINDOW_END =
            new NamespacedKey(CrucibleMain.getInstance(), "fent_window_end_ms");
    private static final NamespacedKey FENT_STORED =
            new NamespacedKey(CrucibleMain.getInstance(), "fent_stored_damage");
    private static final NamespacedKey FENT_WITHDRAWAL =
            new NamespacedKey(CrucibleMain.getInstance(), "fent_withdrawal");

    private static final NamespacedKey FENT_WITHDRAWAL_KILLED =
            new NamespacedKey(CrucibleMain.getInstance(), "fent_withdrawal_killed");
    private static final NamespacedKey FENT_PREVENT_FACTOR =
            new NamespacedKey(CrucibleMain.getInstance(), "fent_prevent_factor");

    private static final Map<UUID, BukkitTask> fentStartTasks = new HashMap<>();
    private static final Map<UUID, BukkitTask> fentCrashTasks = new HashMap<>();
    private static final Map<UUID, BukkitTask> fentHeartbeatTasks = new HashMap<>();

    public static void startFentanylWindow(Player player, int windowTicks) {
        startFentanylWindow(player, windowTicks, 3);
    }

    public static void startFentanylWindow(Player player, int windowTicks, int resistanceAmp) {
        UUID uuid = player.getUniqueId();

        BukkitTask oldStart = fentStartTasks.remove(uuid);
        if (oldStart != null) oldStart.cancel();

        boolean wasWithdrawing = player.getPersistentDataContainer().getOrDefault(FENT_WITHDRAWAL, PersistentDataType.INTEGER, 0) == 1;
        if (wasWithdrawing) {
            BukkitTask oldCrash = fentCrashTasks.remove(uuid);
            if (oldCrash != null) oldCrash.cancel();
            player.getPersistentDataContainer().remove(FENT_WITHDRAWAL_KILLED);
            player.getPersistentDataContainer().set(FENT_WITHDRAWAL, PersistentDataType.INTEGER, 0);
        }

        BukkitTask oldHeartbeat = fentHeartbeatTasks.remove(uuid);
        if (oldHeartbeat != null) oldHeartbeat.cancel();

        long endMs = System.currentTimeMillis() + (windowTicks * 50L);
        player.getPersistentDataContainer().set(FENT_WINDOW_END, PersistentDataType.LONG, endMs);
        if (!wasWithdrawing) {
            player.getPersistentDataContainer().set(FENT_STORED, PersistentDataType.DOUBLE, 0.0);
        }

        startFentanylHeartbeat(player, windowTicks);

        int level = Math.max(1, Math.min(4, resistanceAmp + 1));
        double takenFraction = Math.max(0.2, 1.0 - 0.2 * level);
        double preventFactor = (1.0 / takenFraction) - 1.0;
        player.getPersistentDataContainer().set(FENT_PREVENT_FACTOR, PersistentDataType.DOUBLE, preventFactor);

        BukkitTask startTask = new BukkitRunnable() {
            @Override
            public void run() {
                fentStartTasks.remove(uuid);
                startFentanylCrash(player);
            }
        }.runTaskLater(CrucibleMain.getInstance(), windowTicks);

        fentStartTasks.put(uuid, startTask);
    }

    private static void startFentanylHeartbeat(Player player, int windowTicks) {
        UUID uuid = player.getUniqueId();

        final int bpm = 72;
        final double cyclesPerSecond = bpm / 60.0;
        final long cycleTicks = Math.max(7L, Math.min(20L, Math.round(20.0 / cyclesPerSecond)));

        final long endTick = Bukkit.getCurrentTick() + windowTicks;

        BukkitTask task = new BukkitRunnable() {
            long last = 0;
            boolean dubPending = false;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                long now = Bukkit.getCurrentTick();
                if (now >= endTick) {
                    cancel();
                    return;
                }

                if (!dubPending && now - last >= cycleTicks) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.6f, 0.9f);
                    dubPending = true;
                    last = now;

                    Bukkit.getScheduler().runTaskLater(
                            CrucibleMain.getInstance(),
                            () -> {
                                if (!player.isOnline() || player.isDead()) return;
                                // DUB
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.55f, 1.05f);
                                dubPending = false;
                            },
                            4L
                    );
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                fentHeartbeatTasks.remove(uuid);
            }
        }.runTaskTimer(CrucibleMain.getInstance(), 0L, 1L);

        fentHeartbeatTasks.put(uuid, task);
    }

    private static void startFentanylCrash(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isOnline()) return;
        if (player.isDead()) return;

        double stored = player.getPersistentDataContainer().getOrDefault(FENT_STORED, PersistentDataType.DOUBLE, 0.0);
        if (stored <= 0.0) {
            // Cleanup
            player.getPersistentDataContainer().remove(FENT_WINDOW_END);
            player.getPersistentDataContainer().remove(FENT_STORED);
            player.getPersistentDataContainer().remove(FENT_PREVENT_FACTOR);
            return;
        }

        int ticksPerPoint = 10;
        player.getPersistentDataContainer().set(FENT_WITHDRAWAL, PersistentDataType.INTEGER, 1);

        BukkitTask crashTask = new BukkitRunnable() {
            double remaining = stored;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                if (remaining <= 0.0) {
                    cleanup(player);
                    cancel();
                    return;
                }

                double tickDamage = Math.min(1.0, remaining);
                remaining -= tickDamage;

                double before = player.getHealth();
                if (before <= 0.0) {
                    cleanup(player);
                    cancel();
                    return;
                }

                player.playHurtAnimation(0.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

                boolean lethal = (before - tickDamage) <= 0.0;
                if (lethal) {
                    player.getPersistentDataContainer().set(FENT_WITHDRAWAL_KILLED, PersistentDataType.INTEGER, 1);
                }

                double after = Math.max(0.0, before - tickDamage);
                player.setHealth(after);

                if (lethal) {
                    cleanup(player);
                    cancel();
                    return;
                }

                player.getPersistentDataContainer().set(FENT_STORED, PersistentDataType.DOUBLE, remaining);
            }

            private void cleanup(Player p) {
                fentCrashTasks.remove(uuid);
                p.getPersistentDataContainer().remove(FENT_WINDOW_END);
                p.getPersistentDataContainer().remove(FENT_STORED);
                p.getPersistentDataContainer().remove(FENT_PREVENT_FACTOR);
                if (p.getHealth() > 0.0) {
                    p.getPersistentDataContainer().remove(FENT_WITHDRAWAL);
                    p.getPersistentDataContainer().remove(FENT_WITHDRAWAL_KILLED);
                }
            }
        }.runTaskTimer(CrucibleMain.getInstance(), 0L, ticksPerPoint);

        fentCrashTasks.put(uuid, crashTask);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        MushroomHallucinationManager.stopHallucination(player);

        UUID uuid = player.getUniqueId();
        BukkitTask start = fentStartTasks.remove(uuid);
        if (start != null) start.cancel();
        BukkitTask crash = fentCrashTasks.remove(uuid);
        if (crash != null) crash.cancel();
        BukkitTask hb = fentHeartbeatTasks.remove(uuid);
        if (hb != null) hb.cancel();

        player.getPersistentDataContainer().remove(FENT_WINDOW_END);
        player.getPersistentDataContainer().remove(FENT_STORED);
        player.getPersistentDataContainer().remove(FENT_PREVENT_FACTOR);
        player.getPersistentDataContainer().remove(FENT_WITHDRAWAL);

        Integer killed = player.getPersistentDataContainer().get(FENT_WITHDRAWAL_KILLED, PersistentDataType.INTEGER);
        if (killed != null && killed == 1) {
            event.setDeathMessage(player.getName() + " couldn't stand the withdrawals");
            player.getPersistentDataContainer().remove(FENT_WITHDRAWAL_KILLED);
        } else {
            player.getPersistentDataContainer().remove(FENT_WITHDRAWAL_KILLED);
        }

        String drugName = DrugEffect.getOverdoseDrug(player);

        if (drugName != null) {
            event.setDeathMessage(player.getName() + " overdosed on " + drugName);
            for (DrugType type : DrugType.values()) {
                player.getPersistentDataContainer().remove(DrugEffect.getToleranceKey(type));
            }
            DrugEffect.clearOverdose(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFentanylDamageStore(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        long endMs = player.getPersistentDataContainer().getOrDefault(FENT_WINDOW_END, PersistentDataType.LONG, 0L);
        boolean inWindow = endMs > 0L && System.currentTimeMillis() <= endMs;

        Integer withdrawal = player.getPersistentDataContainer().get(FENT_WITHDRAWAL, PersistentDataType.INTEGER);
        if (withdrawal != null && withdrawal == 1 && event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
            event.setCancelled(true);
            return;
        }

        if (!inWindow) return;

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) return;

        double factor = player.getPersistentDataContainer().getOrDefault(FENT_PREVENT_FACTOR, PersistentDataType.DOUBLE, 4.0);
        double prevented = finalDamage * factor;

        double stored = player.getPersistentDataContainer().getOrDefault(FENT_STORED, PersistentDataType.DOUBLE, 0.0);
        player.getPersistentDataContainer().set(FENT_STORED, PersistentDataType.DOUBLE, stored + prevented);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        MushroomHallucinationManager.stopHallucination(event.getPlayer());

        for (Map<UUID, Long> map : DrugUsing.cooldowns.values()) {
            map.remove(uuid);
        }

        BukkitTask start = fentStartTasks.remove(uuid);
        if (start != null) start.cancel();
        BukkitTask crash = fentCrashTasks.remove(uuid);
        if (crash != null) crash.cancel();
        BukkitTask hb = fentHeartbeatTasks.remove(uuid);
        if (hb != null) hb.cancel();
    }

    @EventHandler
    public void onPlacePrevent(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (ItemUtil.isCustomItem(item, "weed_leaf")) {
            event.setCancelled(true);
            return;
        }

        if (ItemUtil.isCustomItem(item, "coca_seeds")) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && (clicked.getType() == Material.JUNGLE_LOG ||
                    clicked.getType() == Material.JUNGLE_WOOD ||
                    clicked.getType() == Material.STRIPPED_JUNGLE_LOG ||
                    clicked.getType() == Material.STRIPPED_JUNGLE_WOOD)) {
                event.setCancelled(true);
                return;
            }
        }

        if (ItemUtil.isCustomItem(item, "ephedra_seeds")) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType() == Material.FARMLAND) {
                event.setCancelled(true);
                return;
            }
        }

        if (ItemUtil.isCustomItem(item, "ephedra") || ItemUtil.isCustomItem(item, "ephedra_harvested")) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaCraftPrevent(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        for (ItemStack item : matrix) {
            if (item == null) continue;

            String itemId = ItemUtil.getItemId(item);
            if (itemId == null) continue;

            switch (itemId) {
                case "ephedra":
                case "ephedra_harvested":
                    if (isVanillaRecipe(event)) {
                        inv.setResult(null);
                        return;
                    }
                    break;
                case "coca_seeds":
                    if (isVanillaRecipe(event)) {
                        inv.setResult(null);
                        return;
                    }
                    break;
                case "ephedra_seeds":
                    if (isVanillaRecipe(event)) {
                        inv.setResult(null);
                        return;
                    }
                    break;
            }
        }
    }

    private boolean isVanillaRecipe(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return false;

        if (event.getRecipe() instanceof org.bukkit.inventory.Recipe recipe) {
            if (recipe instanceof org.bukkit.Keyed keyed) {
                return !keyed.getKey().getNamespace().equals("crucible");
            }
        }
        return true;
    }
}
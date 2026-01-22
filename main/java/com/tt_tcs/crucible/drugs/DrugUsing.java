package com.tt_tcs.crucible.drugs;

import com.tt_tcs.crucible.CrucibleMain;
import com.tt_tcs.crucible.crafting.DryingRackManager;
import com.tt_tcs.crucible.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DrugUsing implements Listener {

    private static final long COOLDOWN_MS = 500; // 0.5 seconds (default for most drugs)
    private static final long FENTANYL_COOLDOWN_MS = 200L; // 10s

    // Cooldowns per drug type (shared tolerance)
    public static final Map<DrugType, Map<UUID, Long>> cooldowns = new HashMap<>();

    static {
        for (DrugType type : DrugType.values()) {
            cooldowns.put(type, new HashMap<>());
        }
    }



    // ==================== METHCATHINONE (SUGAR) DASH ====================
    private final Map<UUID, Long> mcatChargeReadyAt = new HashMap<>();   // ms timestamp when charged (0 = not charged)
    private final Map<UUID, Long> mcatCooldownUntil = new HashMap<>();   // ms timestamp when dash can be used again

    private boolean isMcatCharged(Player player) {
        Long t = mcatChargeReadyAt.get(player.getUniqueId());
        return t != null && t > 0;
    }

    private void clearMcatCharge(Player player) {
        mcatChargeReadyAt.remove(player.getUniqueId());
    }

/* ================================================== */
    /* COOLDOWN HELPERS                                   */
    /* ================================================== */

    private boolean isOnCooldown(Player player, DrugType type) {
        Long last = cooldowns.get(type).get(player.getUniqueId());
        if (last == null) return false;
        long cooldown = (type == DrugType.fentanyl) ? FENTANYL_COOLDOWN_MS : COOLDOWN_MS;
        return System.currentTimeMillis() - last < cooldown;
    }

    private long getRemainingCooldown(Player player, DrugType type) {
        Long last = cooldowns.get(type).get(player.getUniqueId());
        if (last == null) return 0;
        long cooldown = (type == DrugType.fentanyl) ? FENTANYL_COOLDOWN_MS : COOLDOWN_MS;
        return Math.max(0, cooldown - (System.currentTimeMillis() - last));
    }

    private void setCooldown(Player player, DrugType type) {
        cooldowns.get(type).put(player.getUniqueId(), System.currentTimeMillis());
    }

    /* ================================================== */
    /* DRUG USE LOGIC                                     */
    /* ================================================== */

    private void useDrug(Player player, ItemStack item, DrugType type, String variant) {
        Location loc = player.getEyeLocation();
        int quality = ItemUtil.getQuality(item);
        double oldTolerance = DrugEffect.getTolerance(player, type);

        switch (type) {

            case weed -> {
                player.getWorld().spawnParticle(
                        Particle.DUST_COLOR_TRANSITION,
                        loc,
                        10,
                        0.2, 0.2, 0.2,
                        0.02,
                        new Particle.DustTransition(
                                Color.fromRGB(255, 255, 255),
                                Color.fromRGB(210, 210, 210),
                                1.0f
                        )
                );
                player.getWorld().playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.0f);
                DrugEffect.applyWeedEffects(player, quality);
            }

            case cocaine -> {
                player.getWorld().spawnParticle(
                        Particle.DUST_COLOR_TRANSITION,
                        loc,
                        12,
                        0.2, 0.2, 0.2,
                        0.02,
                        new Particle.DustTransition(
                                Color.fromRGB(255, 255, 255),
                                Color.fromRGB(210, 210, 210),
                                1.0f
                        )
                );
                player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_BURP, 1.0f, 1.5f);
                DrugEffect.applyCocaineEffects(player, quality);
            }

            case meth -> {
                if ("white".equals(variant)) {
                    player.getWorld().spawnParticle(
                            Particle.DUST_COLOR_TRANSITION,
                            loc,
                            12,
                            0.2, 0.2, 0.2,
                            0.02,
                            new Particle.DustTransition(
                                    Color.fromRGB(255, 255, 255),
                                    Color.fromRGB(210, 210, 210),
                                    1.0f
                            )
                    );
                    player.getWorld().playSound(loc, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.0f);
                    DrugEffect.applyWhiteMethEffects(player, quality);

                } else if ("blue".equals(variant)) {
                    player.getWorld().spawnParticle(
                            Particle.DUST_COLOR_TRANSITION,
                            loc,
                            12,
                            0.2, 0.2, 0.2,
                            0.02,
                            new Particle.DustTransition(
                                    Color.fromRGB(50, 50, 255),
                                    Color.fromRGB(25, 25, 210),
                                    1.0f
                            )
                    );
                    player.getWorld().playSound(loc, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
                    DrugEffect.applyBlueMethEffects(player, quality);
                }
                else if ("mcat".equals(variant)) {
                    player.getWorld().spawnParticle(
                            Particle.DUST_COLOR_TRANSITION,
                            loc,
                            14,
                            0.2, 0.2, 0.2,
                            0.02,
                            new Particle.DustTransition(
                                    Color.fromRGB(255, 255, 255),
                                    Color.fromRGB(240, 240, 240),
                                    1.0f
                            )
                    );
                    player.getWorld().playSound(loc, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.4f);
                    DrugEffect.applyMethcathinoneEffects(player, quality);
                }

            }

            case shrooms -> {
                player.getWorld().spawnParticle(
                        Particle.DUST_COLOR_TRANSITION,
                        loc,
                        18,
                        0.3, 0.3, 0.3,
                        0.05,
                        new Particle.DustTransition(
                                Color.fromRGB(255, 50, 255),
                                Color.fromRGB(210, 25, 210),
                                1.0f
                        )
                );
                player.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 0.6f);
                if ("dried".equalsIgnoreCase(variant)) {
                    DrugEffect.applyDriedPsilocybeEffects(player, quality);
                } else {
                    DrugEffect.applyShroomsEffects(player, quality);
                }
            }

            case fentanyl -> {
                player.getWorld().spawnParticle(
                        Particle.DUST_COLOR_TRANSITION,
                        loc,
                        12,
                        0.2, 0.2, 0.2,
                        0.02,
                        new Particle.DustTransition(
                                Color.fromRGB(255, 255, 255),
                                Color.fromRGB(210, 210, 210),
                                1.0f
                        )
                );

                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 5, 0));

                // quality-scaled resistance:
                // >= 4.0 stars (>= 8 half-stars) -> Res IV (amp 3)
                // >= 2.5 stars (>= 5 half-stars) -> Res III (amp 2)
                // <  2.5 stars                   -> Res II (amp 1)
                int resAmp;
                if (quality >= 8) resAmp = 3;
                else if (quality >= 5) resAmp = 2;
                else resAmp = 1;

                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 5, resAmp));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 5, 1));   // Slowness II

                if (oldTolerance > 1.0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 10, 0));
                }

                com.tt_tcs.crucible.util.CrucibleListener.startFentanylWindow(player, 20 * 5, resAmp);
            }
        }

        item.setAmount(item.getAmount() - 1);

        DrugEffect.increaseTolerance(player, type);
        double newTolerance = DrugEffect.getTolerance(player, type);

        if (newTolerance >= 1.10) {
            DrugEffect.setOverdose(player, type);
            player.setHealth(0.0);
            DrugEffect.resetTolerance(player, type);
            return;
        }

        DrugEffect.maybeSendToleranceThresholdMessages(player, type, oldTolerance, newTolerance);

        player.sendActionBar(buildToleranceActionBar(type, newTolerance));

        setCooldown(player, type);
    }

    private String buildToleranceActionBar(DrugType type, double tolerance) {
        final int totalBars = 20;
        double clamped = Math.max(0.0, Math.min(1.0, tolerance));
        int filled = (int) Math.round(clamped * totalBars);
        filled = Math.max(0, Math.min(totalBars, filled));

        String color;
        if (tolerance >= 1.0) {
            color = "§c";
        } else if (tolerance >= 0.6) {
            color = "§e";
        } else {
            color = "§a";
        }

        String filledBars = color + "|".repeat(filled);
        String emptyBars = "§7" + "|".repeat(Math.max(0, totalBars - filled));

        String drugName = switch (type) {
            case weed -> "Weed";
            case cocaine -> "Cocaine";
            case meth -> "Meth";
            case shrooms -> "Shrooms";
            case fentanyl -> "Fentanyl";
        };

        return "§7Tolerance " + filledBars + emptyBars + " §f(" + drugName + ")";
    }

    /* ================================================== */
    /* IF CLICKING DRYING RACK                            */
    /* ================================================== */

    private boolean isClickingDryingRack(PlayerInteractEvent event, Block base) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;

        if (base == null) return false;
        if (base.getType() == DryingRackManager.RACK_TOP) {
            base = base.getRelative(0, -1, 0);
        }
        return base.getType() == DryingRackManager.RACK_POST && base.getRelative(0, 1, 0).getType() == DryingRackManager.RACK_TOP;
    }

    private boolean isClickingGrindingPress(PlayerInteractEvent event, Block clicked) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        if (clicked == null) return false;

        Block b = clicked;
        if (b.getType() == Material.SMITHING_TABLE) {
            return b.getRelative(0, 1, 0).getType() == Material.GRINDSTONE &&
                    b.getRelative(0, 2, 0).getType() == Material.PISTON;
        }
        if (b.getType() == Material.GRINDSTONE) {
            return b.getRelative(0, -1, 0).getType() == Material.SMITHING_TABLE &&
                    b.getRelative(0, 1, 0).getType() == Material.PISTON;
        }
        if (b.getType() == Material.PISTON) {
            return b.getRelative(0, -2, 0).getType() == Material.SMITHING_TABLE &&
                    b.getRelative(0, -1, 0).getType() == Material.GRINDSTONE;
        }
        return false;
    }

    /* ================================================== */
    /* INTERACTION HANDLER                                */
    /* ================================================== */

    @EventHandler
    public void onUseDrug(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();

        Block clicked = event.getClickedBlock();

        if (isClickingDryingRack(event, clicked)) {
            return;
        }

        if (isClickingGrindingPress(event, clicked)) {
            return;
        }

        // weed
        if (ItemUtil.isCustomItem(item, "joint")) {
            handleUse(event, player, item, DrugType.weed, null);
        }

        // cocaine
        else if (ItemUtil.isCustomItem(item, "cocaine")) {
            handleUse(event, player, item, DrugType.cocaine, null);
        }

        // white meth
        else if (ItemUtil.isCustomItem(item, "white_meth")) {
            handleUse(event, player, item, DrugType.meth, "white");
        }

        // blue meth
        else if (ItemUtil.isCustomItem(item, "blue_meth")) {
            handleUse(event, player, item, DrugType.meth, "blue");
        }


        // methcathinone (sugar)
        else if (ItemUtil.isCustomItem(item, "methcathinone")) {
            handleUse(event, player, item, DrugType.meth, "mcat");
        }

        // shrooms
        else if (ItemUtil.isCustomItem(item, "dried_psilocybe_mushroom")) {
            handleUse(event, player, item, DrugType.shrooms, "dried");
        }

        else if (ItemUtil.isCustomItem(item, "psilocybe")) {
            handleUse(event, player, item, DrugType.shrooms, "psilocybe");
        }

        // fentanyl
        else if (ItemUtil.isCustomItem(item, "fentanyl")) {
            handleUse(event, player, item, DrugType.fentanyl, null);
        }
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        DrugEffect.restoreMethcathinone(event.getPlayer());
    }

private void handleUse(PlayerInteractEvent event, Player player, ItemStack item, DrugType type, String variant) {
        if (isOnCooldown(player, type)) {
            long remaining = getRemainingCooldown(player, type);
            player.sendActionBar("§cYou can't use this for another " + ((remaining + 999) / 1000) + " second(s)!");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        useDrug(player, item, type, variant);
    }



    /* ================================================== */
    /* METHCATHINONE (SUGAR) DASH                         */
    /* ================================================== */

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMcatSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        if (!DrugEffect.isMethcathinoneActive(player)) {
            clearMcatCharge(player);
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = mcatCooldownUntil.getOrDefault(player.getUniqueId(), 0L);

        if (event.isSneaking()) {
            if (now < cooldownUntil) return;

            Bukkit.getScheduler().runTaskLater(CrucibleMain.getInstance(), () -> {
                if (!player.isOnline()) return;
                if (!DrugEffect.isMethcathinoneActive(player)) return;
                if (!player.isSneaking()) return;

                long cd = mcatCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
                if (System.currentTimeMillis() < cd) return;

                mcatChargeReadyAt.put(player.getUniqueId(), System.currentTimeMillis());

                Location loc = player.getLocation().add(0, 1.0, 0);
                player.getWorld().spawnParticle(Particle.DUST, loc, 14, 0.25, 0.25, 0.25, 0.01,
                        new Particle.DustOptions(Color.fromRGB(220, 220, 220), 1.2f));
                player.getWorld().playSound(loc, Sound.ITEM_LEAD_TIED, 1.5f, 1.4f);
            }, 4L);

            return;
        }

        if (!isMcatCharged(player)) return;
        if (now < cooldownUntil) {
            clearMcatCharge(player);
            return;
        }

        if (player.isOnGround()) {
            clearMcatCharge(player);
            return;
        }

        clearMcatCharge(player);

        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Vector vel = dir.multiply(1.55).setY(0.25);
        player.setVelocity(vel);

        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.ENTITY_GOAT_LONG_JUMP, 1.0f, 1.1f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                Location l = player.getLocation().add(0, 0.9, 0);
                player.getWorld().spawnParticle(Particle.CLOUD, l, 6, 0.15, 0.15, 0.15, 0.0);
                ticks++;
                if (ticks >= 8) cancel();
            }
        }.runTaskTimer(CrucibleMain.getInstance(), 0L, 1L);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 3, 0, true, true, true));

        mcatCooldownUntil.put(player.getUniqueId(), now + 5000L);
        Bukkit.getScheduler().runTaskLater(CrucibleMain.getInstance(), () -> {
            if (!player.isOnline()) return;
            if (!DrugEffect.isMethcathinoneActive(player)) return;

            Location readyLoc = player.getLocation().add(0, 1.0, 0);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, readyLoc, 10, 0.25, 0.25, 0.25, 0.02);
            player.getWorld().playSound(readyLoc, Sound.ENTITY_CHICKEN_EGG, 0.8f, 1.5f);
        }, 20L * 5);
    }

    /* ================================================== */
    /* PREVENT PLACING SHROOMS                            */
    /* ================================================== */

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null) return;

        if (ItemUtil.getItemId(item) != null && item.getType().isBlock()) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar("§cYou can't place that.");
        }
    }
}
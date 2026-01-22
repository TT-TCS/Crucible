package com.tt_tcs.crucible.drugs;

import com.tt_tcs.crucible.CrucibleMain;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class MushroomHallucinationManager {

    private static final Map<UUID, Session> sessions = new HashMap<>();

    public static void startHallucination(Player player, int quality) {
        startHallucination(player, quality, true, true);
    }

    public static void startHallucination(Player player, int quality, boolean eyeHallucinations, boolean heartbeat) {
        stopHallucination(player);
        Session session = new Session(player, quality, eyeHallucinations, heartbeat);
        sessions.put(player.getUniqueId(), session);
        session.start();
    }

    public static void stopHallucination(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s != null) s.end();
    }

    private static class Session {
        final Player player;
        final float stars;
        final long endTime;
        final boolean eyeHallucinations;
        final boolean heartbeat;
        final Random rand = new Random();

        final List<EyeHallucination> eyes = new ArrayList<>();

        int mainTaskId = -1;
        int ambientSoundTaskId = -1;

        long lastHeartbeatTick = 0;
        boolean heartbeatDubPending = false;

        Session(Player player, int quality, boolean eyeHallucinations, boolean heartbeat) {
            this.player = player;
            this.stars = quality / 2.0f;
            this.eyeHallucinations = eyeHallucinations;
            this.heartbeat = heartbeat;

            int minutes;
            if (stars <= 2.0f) minutes = 3;
            else if (stars <= 3.5f) minutes = 6;
            else minutes = 9;

            this.endTime = System.currentTimeMillis() + minutes * 60_000L;
        }

        void start() {
            EyeHallucination.hideWorld(player);

            mainTaskId = new BukkitRunnable() {
                @Override
                public void run() {
                    if (expired()) {
                        cancel();
                        end();
                        return;
                    }

                    player.addPotionEffect(
                            new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false)
                    );

                    if (stars >= 4.0f && eyeHallucinations) {
                        tickEyes();
                    }
                    if (heartbeat) {
                        tickHeartbeat();
                    }
                }
            }.runTaskTimer(CrucibleMain.getInstance(), 20L, 2L).getTaskId();

            ambientSoundTaskId = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!expired()) playAmbientSound();
                }
            }.runTaskTimer(CrucibleMain.getInstance(), 40L, 400L).getTaskId();
        }

        boolean expired() {
            return !player.isOnline() || System.currentTimeMillis() > endTime;
        }

        void tickEyes() {
            if (eyes.size() < 2 && rand.nextDouble() < 0.02) {
                eyes.add(EyeHallucination.spawnBehind(player));
            }

            Iterator<EyeHallucination> it = eyes.iterator();
            while (it.hasNext()) {
                EyeHallucination e = it.next();
                if (e.update(player)) {
                    it.remove();
                }
            }
        }

        /* ================= HEARTBEAT ================= */

        void tickHeartbeat() {
            long tick = Bukkit.getCurrentTick();

            double nearest = eyes.stream()
                    .mapToDouble(e -> e.location.distance(player.getLocation()))
                    .min()
                    .orElse(20.0);

            int bpm = heartbeatBpmForDistance(nearest);

            double cyclesPerSecond = bpm / 60.0;
            long cycleTicks = Math.round(20.0 / cyclesPerSecond);

            cycleTicks = Math.max(7, Math.min(20, cycleTicks));

            if (!heartbeatDubPending && tick - lastHeartbeatTick >= cycleTicks) {
                // LUB
                player.playSound(
                        player.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
                        0.6f,
                        0.9f
                );

                heartbeatDubPending = true;
                lastHeartbeatTick = tick;

                Bukkit.getScheduler().runTaskLater(
                        CrucibleMain.getInstance(),
                        () -> {
                            // DUB
                            player.playSound(
                                    player.getLocation(),
                                    Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
                                    0.55f,
                                    1.05f
                            );
                            heartbeatDubPending = false;
                        },
                        4L // ~200ms
                );
            }
        }

        int heartbeatBpmForDistance(double d) {
            if (d < 8)  return 180;
            if (d < 12) return 150;
            if (d < 16) return 120;
            if (d < 20) return 90;
            return 70;
        }

        void playAmbientSound() {
            Location s = player.getLocation().clone().add(
                    rand.nextGaussian() * 6,
                    rand.nextDouble() * 2,
                    rand.nextGaussian() * 6
            );

            player.playSound(
                    s,
                    Sound.AMBIENT_CAVE,
                    0.25f,
                    0.6f + rand.nextFloat() * 0.6f
            );
        }

        void end() {
            if (mainTaskId != -1) Bukkit.getScheduler().cancelTask(mainTaskId);
            if (ambientSoundTaskId != -1) Bukkit.getScheduler().cancelTask(ambientSoundTaskId);

            for (EyeHallucination e : eyes) {
                e.forceDespawn(player);
            }
            eyes.clear();

            EyeHallucination.showWorld(player);
        }
    }

    private static class EyeHallucination {
        private Location location;
        private long spawnTime;
        private long lastBreath = 0;

        static final Particle.DustOptions RED =
                new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.3f);

        static EyeHallucination spawnBehind(Player player) {
            Location base = player.getLocation();
            float yaw = base.getYaw();

            double rad = Math.toRadians(yaw + 180 + (Math.random() * 120 - 60));
            double dist = 14 + Math.random() * 8;

            Location loc = base.clone().add(
                    -Math.sin(rad) * dist,
                    1.6,
                    Math.cos(rad) * dist
            );

            EyeHallucination e = new EyeHallucination();
            e.location = loc;
            e.spawnTime = System.currentTimeMillis();
            return e;
        }

        boolean update(Player player) {
            Vector toPlayer = player.getEyeLocation().toVector()
                    .subtract(location.toVector());

            double dist = toPlayer.length();
            if (dist <= 8.0) {
                despawn(player);
                return true;
            }

            location.add(toPlayer.normalize().multiply(0.05));

            renderEyes(location, player);
            playBreathing(player);

            if (System.currentTimeMillis() - spawnTime > 20_000) {
                despawn(player);
                return true;
            }

            return false;
        }

        void playBreathing(Player player) {
            long now = System.currentTimeMillis();
            if (now - lastBreath < 3000) return;
            lastBreath = now;

            player.playSound(
                    location,
                    Sound.ENTITY_PLAYER_BREATH,
                    0.35f,
                    0.6f + (float) Math.random() * 0.4f
            );
        }

        void forceDespawn(Player viewer) {
            despawn(viewer);
        }

        static void renderEyes(Location loc, Player viewer) {
            Vector forward = viewer.getEyeLocation().getDirection().normalize();
            Vector right = forward.clone()
                    .crossProduct(new Vector(0, 1, 0))
                    .normalize()
                    .multiply(0.22);

            Location leftEye = loc.clone().add(right).add(0, 0.05, 0);
            Location rightEye = loc.clone().subtract(right).add(0, 0.05, 0);

            viewer.spawnParticle(Particle.DUST, leftEye, 1, 0, 0, 0, 0, RED);
            viewer.spawnParticle(Particle.DUST, rightEye, 1, 0, 0, 0, 0, RED);
        }

        private void despawn(Player viewer) {
            viewer.spawnParticle(
                    Particle.SMOKE,
                    location.clone().add(0, 1.0, 0),
                    25,
                    0.3, 0.4, 0.3,
                    0.01
            );
        }

        static void hideWorld(Player player) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    player.hidePlayer(CrucibleMain.getInstance(), other);
                }
            }

            player.getWorld().getEntities().forEach(entity ->
                    player.hideEntity(CrucibleMain.getInstance(), entity)
            );
        }

        static void showWorld(Player player) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                player.showPlayer(CrucibleMain.getInstance(), other);
            }

            player.getWorld().getEntities().forEach(entity ->
                    player.showEntity(CrucibleMain.getInstance(), entity)
            );
        }
    }
}

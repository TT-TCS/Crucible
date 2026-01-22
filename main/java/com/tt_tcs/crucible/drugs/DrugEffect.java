package com.tt_tcs.crucible.drugs;

import com.tt_tcs.crucible.CrucibleMain;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DrugEffect {

    public static final NamespacedKey OVERDOSE_KEY =
            new NamespacedKey(CrucibleMain.getInstance(), "overdose_drug");



    // Methcathinone (Sugar) state
    public static final NamespacedKey MCAT_ACTIVE_UNTIL_KEY =
            new NamespacedKey(CrucibleMain.getInstance(), "mcat_active_until");
    public static final NamespacedKey MCAT_CRASH_EXTRA_TICKS_KEY =
            new NamespacedKey(CrucibleMain.getInstance(), "mcat_crash_extra_ticks");

    public static void setOverdose(Player player, DrugType type) {
        player.getPersistentDataContainer().set(OVERDOSE_KEY, PersistentDataType.STRING, type.name());
    }

    public static void clearOverdose(Player player) {
        player.getPersistentDataContainer().remove(OVERDOSE_KEY);
    }

    public static String getOverdoseDrug(Player player) {
        return player.getPersistentDataContainer().get(OVERDOSE_KEY, PersistentDataType.STRING);
    }

    public static NamespacedKey getToleranceKey(DrugType type) {
        return new NamespacedKey(CrucibleMain.getInstance(), type.id + "_tolerance");
    }

    private static NamespacedKey getToleranceStageKey(DrugType type) {
        return new NamespacedKey(CrucibleMain.getInstance(), type.id + "_tolerance_stage");
    }

    private static int getToleranceStage(double tolerance) {
        // 0: <0.6, 1: >=0.6 and <1.0, 2: >=1.0
        if (tolerance >= 1.0) return 2;
        if (tolerance >= 0.6) return 1;
        return 0;
    }

    public static void maybeSendToleranceThresholdMessages(Player player, DrugType type, double oldTolerance, double newTolerance) {
        int oldStage = getToleranceStage(oldTolerance);
        int newStage = getToleranceStage(newTolerance);
        if (newStage <= oldStage) return;

        int announced = player.getPersistentDataContainer()
                .getOrDefault(getToleranceStageKey(type), PersistentDataType.INTEGER, 0);
        if (newStage <= announced) return;

        for (int stage = announced + 1; stage <= newStage; stage++) {
            String msg = toleranceStageMessage(type, stage);
            if (msg != null) player.sendMessage(msg);
        }

        player.getPersistentDataContainer().set(getToleranceStageKey(type), PersistentDataType.INTEGER, newStage);
    }

    private static String toleranceStageMessage(DrugType type, int stage) {
        if (stage == 1) {
            return switch (type) {
                case weed -> "§eYour body feels anxious and on high alert...";
                case cocaine -> "§eYour heart is pounding harder...";
                case meth -> "§eYour body can't stop twitching...";
                case shrooms -> "§eYou're losing touch with reality...";
                case fentanyl -> "§eYou feel so tired...";
            };
        }

        if (stage == 2) {
            return switch (type) {
                case weed -> "§cYou feel like you might collapse...";
                case cocaine -> "§cYour vision is fading out and your body is shaking...";
                case meth -> "§cYour heartbeat feels painful...";
                case shrooms -> "§cYou're barely holding on to what's real and what's fake...";
                case fentanyl -> "§cYou... don't feel anything?";
            };
        }

        return null;
    }

    
    public static boolean isMethcathinoneSugarActive(Player player) {
        Long until = player.getPersistentDataContainer().get(MCAT_ACTIVE_UNTIL_KEY, PersistentDataType.LONG);
        return until != null && until > System.currentTimeMillis();
    }

public static double getTolerance(Player player, DrugType type) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        return data.getOrDefault(getToleranceKey(type), PersistentDataType.DOUBLE, 0.0);
    }

    public static void increaseTolerance(Player player, DrugType type) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        NamespacedKey key = getToleranceKey(type);

        double tolerance = data.getOrDefault(key, PersistentDataType.DOUBLE, 0.0);
        tolerance += type.toleranceRate;
        data.set(key, PersistentDataType.DOUBLE, tolerance);
    }

    public static void resetTolerance(Player player, DrugType type) {
        player.getPersistentDataContainer().set(getToleranceKey(type), PersistentDataType.DOUBLE, 0.0);
    }

    public static void setTolerance(Player player, DrugType type, double value) {
        NamespacedKey key = getToleranceKey(type);
        player.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
    }

    public static void applyWeedEffects(Player player, int quality) {
        double tolerance = getTolerance(player, DrugType.weed);

        float stars = quality / 2.0f;

        if (stars <= 1.5f) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20*120, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20*60, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20*10, 0));
            player.sendMessage("§cThis Weed is terrible quality!");
            return;
        }

        double qualityMultiplier = Math.min(1.0, stars / 5.0);
        int baseDuration = 20 * 90;
        int duration = (int) (baseDuration * qualityMultiplier);

        int regenAmplifier = stars >= 4.0 ? 1 : 0;
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, regenAmplifier));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, duration, 1));

        if (stars >= 3.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration / 2, 0));
        }

        if (tolerance > 0.6) {
            int nauseaDuration = (int) (20 * 45 * (tolerance - 0.6) * 5); // Scales with tolerance
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDuration, 0));
        }

        if (tolerance > 1.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20*60, 0));
        }
    }



    public static void applyCocaineEffects(Player player, int quality) {
        double tolerance = getTolerance(player, DrugType.cocaine);

        float stars = quality / 2.0f;

        if (stars <= 1.5f) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20*120, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20*60, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20*15, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20*120, 1));
            player.sendMessage("§cThis Cocaine is terrible quality!");
            return;
        }

        double qualityMultiplier = Math.min(1.0, stars / 5.0);
        int baseDuration = 20 * 120;
        int duration = (int) (baseDuration * qualityMultiplier);

        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20*10, 0));

        if (tolerance < 0.5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 0));
        }

        if (tolerance >= 0.5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0));
        }

        if (stars >= 4.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, 0));
        }

        int heartRateDuration = (int) (duration * 0.8);

        if (tolerance > 0.6) {
            int nauseaDuration = (int) (20 * 60 * (tolerance - 0.6) * 2.5);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDuration, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, heartRateDuration, 1));
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, heartRateDuration, 0));
        }

        if (tolerance > 1.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20*60, 0));
        }
    }



    
    public static void applyMethcathinoneEffects(Player player, int quality) {
        setTolerance(player, DrugType.meth, getTolerance(player, DrugType.meth) + 0.4);

        float stars = quality / 2.0f; // 0-5
        double qualityMultiplier = Math.min(1.0, Math.max(0.0, stars / 5.0));

        int baseHighTicks = 20 * 60 * 8; // 8 min
        int highTicks = (int) Math.max(20 * 30, baseHighTicks * qualityMultiplier); // min 30s

        int baseCrashTicks = 20 * 60 * 2;
        double crashMultiplier = 1.0 + (1.0 - qualityMultiplier) * 2.0;
        int crashTicks = (int) Math.max(baseCrashTicks, baseCrashTicks * crashMultiplier);

        int remainingCrash = 0;
        PotionEffect slow = player.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slow != null && slow.getAmplifier() >= 2) remainingCrash = Math.max(remainingCrash, slow.getDuration());
        PotionEffect hunger = player.getPotionEffect(PotionEffectType.HUNGER);
        if (hunger != null && hunger.getAmplifier() >= 2) remainingCrash = Math.max(remainingCrash, hunger.getDuration());
        PotionEffect weak = player.getPotionEffect(PotionEffectType.WEAKNESS);
        if (weak != null && weak.getAmplifier() >= 1) remainingCrash = Math.max(remainingCrash, weak.getDuration());

        if (remainingCrash > 0) {
            Integer extra = player.getPersistentDataContainer().get(MCAT_CRASH_EXTRA_TICKS_KEY, PersistentDataType.INTEGER);
            int newExtra = (extra == null ? 0 : extra) + remainingCrash;
            player.getPersistentDataContainer().set(MCAT_CRASH_EXTRA_TICKS_KEY, PersistentDataType.INTEGER, newExtra);

            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.HUNGER);
            player.removePotionEffect(PotionEffectType.WEAKNESS);
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, highTicks, 0, true, true, true));  // Speed I
        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, highTicks, 1, true, true, true)); // Hunger II

        long expectedUntil = System.currentTimeMillis() + (highTicks * 50L);
        player.getPersistentDataContainer().set(MCAT_ACTIVE_UNTIL_KEY, PersistentDataType.LONG, expectedUntil);

        Bukkit.getScheduler().runTaskLater(CrucibleMain.getInstance(), () -> {
            if (!player.isOnline()) return;

            Long until = player.getPersistentDataContainer().get(MCAT_ACTIVE_UNTIL_KEY, PersistentDataType.LONG);
            if (until == null || until != expectedUntil) return; // refreshed/overwritten

            player.getPersistentDataContainer().remove(MCAT_ACTIVE_UNTIL_KEY);

            Integer extra = player.getPersistentDataContainer().get(MCAT_CRASH_EXTRA_TICKS_KEY, PersistentDataType.INTEGER);
            int extraTicks = (extra == null ? 0 : extra);
            if (extraTicks > 0) player.getPersistentDataContainer().set(MCAT_CRASH_EXTRA_TICKS_KEY, PersistentDataType.INTEGER, 0);

            int totalCrash = crashTicks + extraTicks;

            Location loc = player.getLocation().add(0, 1.0, 0);
            player.getWorld().playSound(loc, Sound.BLOCK_FIREFLY_BUSH_IDLE, 1.0f, 0.6f);

            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, totalCrash, 2, true, true, true)); // Slowness III
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, totalCrash, 2, true, true, true));   // Hunger III
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, totalCrash, 1, true, true, true)); // Weakness II
        }, highTicks);
    }

    public static void applyWhiteMethEffects(Player player, int quality) {
        double tolerance = getTolerance(player, DrugType.meth);

        float stars = quality / 2.0f;

        if (stars <= 1.5f) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20*120, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20*30, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20*120, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 20*90, 2));
            setTolerance(player, DrugType.meth, DrugEffect.getTolerance(player, DrugType.meth) + 0.4);
            player.sendMessage("§cThis White Meth is terrible quality!");
            return;
        }

        double qualityMultiplier = Math.min(1.0, stars / 5.0);
        int baseDuration = 20 * 150;
        int duration = (int) (baseDuration * qualityMultiplier);

        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, 0));

        if (tolerance < 0.5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1));
        }

        if (tolerance >= 0.5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0));
        }

        if (stars >= 4.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1));
        }

        int heartRateDuration = (int) (duration * 1.2);

        if (tolerance > 0.6) {
            int nauseaDuration = (int) (20 * 60 * (tolerance - 0.6) * 2.5);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDuration, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, heartRateDuration, 2));
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, heartRateDuration, 1));
        }

        if (tolerance > 1.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20*60, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20*90, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20*90, 0));
        }
    }



    public static void applyBlueMethEffects(Player player, int quality) {
        double tolerance = getTolerance(player, DrugType.meth);

        float stars = quality / 2.0f;

        if (stars <= 1.5f) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20*120, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20*30, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20*120, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 20*90, 2));
            setTolerance(player, DrugType.meth, DrugEffect.getTolerance(player, DrugType.meth) + 0.4);
            player.sendMessage("§cThis Blue Meth is terrible quality!");
            return;
        }

        double qualityMultiplier = Math.min(1.0, stars / 5.0);
        int baseDuration = 20 * 180;
        int duration = (int) (baseDuration * qualityMultiplier);

        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration*2, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20*15, 0));

        if (tolerance < 0.5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0));
        }

        if (tolerance >= 0.5) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0));
        }

        if (stars >= 4.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int) (duration*1.5), 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1));
        }

        int heartRateDuration = (int) (duration * 1.2);

        if (tolerance > 0.6) {
            int nauseaDuration = (int) (20 * 60 * (tolerance - 0.6) * 2.5);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDuration, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, heartRateDuration, 2));
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, heartRateDuration, 1));
        }

        if (tolerance > 1.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20*150, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20*120, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20*150, 2));
        }
    }


    public static void applyShroomsEffects(Player player, int quality) {
        double tolerance = getTolerance(player, DrugType.shrooms);

        float stars = quality / 2.0f;

        MushroomHallucinationManager.startHallucination(player, quality);


        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 10, 0));

        if (stars <= 2.0f) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, 0));
        }
    }

    public static void applyDriedPsilocybeEffects(Player player, int quality) {
        double tolerance = getTolerance(player, DrugType.shrooms);
        float stars = quality / 2.0f;

        MushroomHallucinationManager.startHallucination(player, quality, false, false);

        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 30, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 8, 0));

        if (stars <= 2.0f) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, 0));
        }

        if (tolerance > 1.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 30, 0));
        }
    }
}
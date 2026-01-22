package com.tt_tcs.crucible.util;

import com.tt_tcs.crucible.CrucibleMain;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;

public class StopwatchManager implements Listener {

    private final BukkitTask ticker;

    public StopwatchManager() {
        this.ticker = Bukkit.getScheduler().runTaskTimer(CrucibleMain.getInstance(), this::tick, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() == null || !event.getAction().isRightClick()) return;

        ItemStack item = event.getItem();
        if (!ItemUtil.isCustomItem(item, "stopwatch")) return;

        event.setCancelled(true);

        ensureIdAndMigrate(item);

        if (event.getPlayer().isSneaking()) {
            setRunning(item, false);
            setElapsedMs(item, 0L);
            clearStartMs(item);
            updateLore(item);
            return;
        }

        long now = System.currentTimeMillis();

        if (isRunning(item)) {
            long start = getStartMs(item);
            long elapsed = getElapsedMs(item);
            if (start > 0L) {
                long add = Math.max(0L, now - start);
                setElapsedMs(item, elapsed + add);
            }
            setRunning(item, false);
            clearStartMs(item);
            updateLore(item);
            return;
        }

        setRunning(item, true);
        setStartMs(item, now);
        updateLore(item);
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            ItemStack[] contents = inv.getContents();
            if (contents != null) {
                for (ItemStack it : contents) {
                    if (!ItemUtil.isCustomItem(it, "stopwatch")) continue;
                    ensureIdAndMigrate(it);
                    if (isRunning(it)) updateLore(it, now);
                }
            }

            ItemStack off = inv.getItemInOffHand();
            if (ItemUtil.isCustomItem(off, "stopwatch")) {
                ensureIdAndMigrate(off);
                if (isRunning(off)) updateLore(off, now);
            }

            ItemStack main = inv.getItemInMainHand();
            if (ItemUtil.isCustomItem(main, "stopwatch")) {
                ensureIdAndMigrate(main);
                if (isRunning(main)) updateLore(main, now);
            }

            boolean mainIs = ItemUtil.isCustomItem(main, "stopwatch");
            boolean offIs = ItemUtil.isCustomItem(off, "stopwatch");
            if (mainIs ^ offIs) {
                ItemStack held = mainIs ? main : off;
                sendActionbar(p, "§eCounting " + getMmss(held, now));
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Item drop : world.getEntitiesByClass(Item.class)) {
                ItemStack st = drop.getItemStack();
                if (!ItemUtil.isCustomItem(st, "stopwatch")) continue;
                ensureIdAndMigrate(st);
                if (isRunning(st)) {
                    updateLore(st, now);
                    drop.setItemStack(st);
                }
            }
        }
    }

    private void ensureIdAndMigrate(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String id = pdc.get(ItemKeys.STOPWATCH_UUID, PersistentDataType.STRING);
        if (id == null) {
            pdc.set(ItemKeys.STOPWATCH_UUID, PersistentDataType.STRING, UUID.randomUUID().toString());
        }

        if (!pdc.has(ItemKeys.STOPWATCH_ELAPSED_MS, PersistentDataType.LONG)) {
            Integer seconds = pdc.get(ItemKeys.STOPWATCH_SECONDS, PersistentDataType.INTEGER);
            if (seconds != null && seconds > 0) {
                pdc.set(ItemKeys.STOPWATCH_ELAPSED_MS, PersistentDataType.LONG, seconds.longValue() * 1000L);
            } else {
                pdc.set(ItemKeys.STOPWATCH_ELAPSED_MS, PersistentDataType.LONG, 0L);
            }
            pdc.remove(ItemKeys.STOPWATCH_SECONDS);
        }

        if (!pdc.has(ItemKeys.STOPWATCH_RUNNING, PersistentDataType.BYTE)) {
            pdc.set(ItemKeys.STOPWATCH_RUNNING, PersistentDataType.BYTE, (byte) 0);
        }

        item.setItemMeta(meta);
        updateLore(item);
    }

    private boolean isRunning(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte b = meta.getPersistentDataContainer().get(ItemKeys.STOPWATCH_RUNNING, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    private void setRunning(ItemStack item, boolean value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(ItemKeys.STOPWATCH_RUNNING, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
        item.setItemMeta(meta);
    }

    private long getElapsedMs(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0L;
        Long v = meta.getPersistentDataContainer().get(ItemKeys.STOPWATCH_ELAPSED_MS, PersistentDataType.LONG);
        return v == null ? 0L : v;
    }

    private void setElapsedMs(ItemStack item, long ms) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(ItemKeys.STOPWATCH_ELAPSED_MS, PersistentDataType.LONG, Math.max(0L, ms));
        item.setItemMeta(meta);
    }

    private long getStartMs(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0L;
        Long v = meta.getPersistentDataContainer().get(ItemKeys.STOPWATCH_START_MS, PersistentDataType.LONG);
        return v == null ? 0L : v;
    }

    private void setStartMs(ItemStack item, long ms) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(ItemKeys.STOPWATCH_START_MS, PersistentDataType.LONG, ms);
        item.setItemMeta(meta);
    }

    private void clearStartMs(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(ItemKeys.STOPWATCH_START_MS);
        item.setItemMeta(meta);
    }

    private void updateLore(ItemStack item) {
        updateLore(item, System.currentTimeMillis());
    }

    private void updateLore(ItemStack item, long now) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        long elapsed = pdc.getOrDefault(ItemKeys.STOPWATCH_ELAPSED_MS, PersistentDataType.LONG, 0L);
        Byte running = pdc.get(ItemKeys.STOPWATCH_RUNNING, PersistentDataType.BYTE);
        if (running != null && running == (byte) 1) {
            Long start = pdc.get(ItemKeys.STOPWATCH_START_MS, PersistentDataType.LONG);
            if (start != null && start > 0L) {
                elapsed += Math.max(0L, now - start);
            }
        }

        long totalSeconds = Math.max(0L, elapsed / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        String mmss = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
        meta.setLore(List.of("§eCounting " + mmss));
        item.setItemMeta(meta);
    }

    private String getMmss(ItemStack item, long now) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "00:00";

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        long elapsed = pdc.getOrDefault(ItemKeys.STOPWATCH_ELAPSED_MS, PersistentDataType.LONG, 0L);
        Byte running = pdc.get(ItemKeys.STOPWATCH_RUNNING, PersistentDataType.BYTE);
        if (running != null && running == (byte) 1) {
            Long start = pdc.get(ItemKeys.STOPWATCH_START_MS, PersistentDataType.LONG);
            if (start != null && start > 0L) {
                elapsed += Math.max(0L, now - start);
            }
        }

        long totalSeconds = Math.max(0L, elapsed / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private void sendActionbar(Player player, String msg) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
    }
}

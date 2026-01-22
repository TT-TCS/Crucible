package com.tt_tcs.crucible.crops;

import com.tt_tcs.crucible.CrucibleMain;
import com.tt_tcs.crucible.drugs.DrugItems;
import com.tt_tcs.crucible.util.ItemUtil;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Weed crop logic.
 *
 * Key idea: weed identity is NOT inferred from purple glass.
 * - Purple stained glass is only a *requirement* to PLANT and to GROW (and to apply bonemeal).
 * - Whether a WHEAT block is weed is tracked persistently per-chunk using PDC when planted from WEED_SEEDS.
 */
public class WeedCropManager implements Listener {

    private static final Material CROP_BLOCK = Material.WHEAT;
    private static final Material MARKER_BLOCK = Material.PURPLE_STAINED_GLASS;
    private static final int MIN_LIGHT = 7;

    private final NamespacedKey weedBlocksKey = new NamespacedKey(CrucibleMain.getInstance(), "weed_blocks");

    // ---- Weed identity registry (stored on the chunk) ----

    private static long packBlockPos(int x, int y, int z) {
        // Matches vanilla BlockPos long packing (26 bits X, 12 bits Y, 26 bits Z).
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    private long[] getWeedArray(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] arr = pdc.get(weedBlocksKey, PersistentDataType.LONG_ARRAY);
        return arr == null ? new long[0] : arr;
    }

    private void setWeedArray(Chunk chunk, long[] arr) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (arr == null || arr.length == 0) {
            pdc.remove(weedBlocksKey);
        } else {
            pdc.set(weedBlocksKey, PersistentDataType.LONG_ARRAY, arr);
        }
    }

    private boolean containsWeedPos(Chunk chunk, long pos) {
        long[] arr = getWeedArray(chunk);
        for (long v : arr) {
            if (v == pos) return true;
        }
        return false;
    }

    private void addWeedPos(Block cropBlock) {
        Chunk chunk = cropBlock.getChunk();
        long pos = packBlockPos(cropBlock.getX(), cropBlock.getY(), cropBlock.getZ());
        if (containsWeedPos(chunk, pos)) return;

        long[] arr = getWeedArray(chunk);
        long[] out = Arrays.copyOf(arr, arr.length + 1);
        out[arr.length] = pos;
        setWeedArray(chunk, out);
    }

    private void removeWeedPos(Block cropBlock) {
        Chunk chunk = cropBlock.getChunk();
        long pos = packBlockPos(cropBlock.getX(), cropBlock.getY(), cropBlock.getZ());

        long[] arr = getWeedArray(chunk);
        if (arr.length == 0) return;

        int idx = -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == pos) {
                idx = i;
                break;
            }
        }
        if (idx == -1) return;

        long[] out = new long[arr.length - 1];
        if (idx > 0) System.arraycopy(arr, 0, out, 0, idx);
        if (idx < arr.length - 1) System.arraycopy(arr, idx + 1, out, idx, arr.length - idx - 1);
        setWeedArray(chunk, out);
    }

    private boolean isWeedCrop(Block block) {
        if (block == null || block.getType() != CROP_BLOCK) return false;
        long pos = packBlockPos(block.getX(), block.getY(), block.getZ());
        return containsWeedPos(block.getChunk(), pos);
    }

    // ---- Marker requirements (plant & grow) ----

    private boolean hasMarker(Block cropBlock) {
        // Marker is 2 blocks above the crop block (same rule as your previous implementation)
        Block glassBlock = cropBlock.getRelative(0, 2, 0);
        return glassBlock.getType() == MARKER_BLOCK;
    }

    private boolean hasEnoughLight(Block cropBlock) {
        return cropBlock.getLightLevel() >= MIN_LIGHT;
    }

    // ---- Growth control ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (block.getType() != CROP_BLOCK) return;

        // Only enforce weed rules on weed crops, never on normal wheat.
        if (!isWeedCrop(block)) return;

        if (!hasEnoughLight(block) || !hasMarker(block)) {
            event.setCancelled(true);
        }
    }

    // ---- Breaking / drops ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != CROP_BLOCK) return;

        // Only override drops for weed crops.
        if (!isWeedCrop(block)) return;

        Ageable ageable = (Ageable) block.getBlockData();

        // Remove from registry immediately (prevents dupes if something else cancels after this).
        removeWeedPos(block);

        event.setDropItems(false);
        event.getPlayer().playSound(block.getLocation(), Sound.BLOCK_CROP_BREAK, 1.0f, 1.0f);

        if (ageable.getAge() < ageable.getMaximumAge()) {
            // Not fully grown -> drop 1 weed seed
            block.getWorld().dropItemNaturally(block.getLocation(), DrugItems.WEED_SEEDS.clone());
        } else {
            // Fully grown -> drop weed leaf + weed seed
            ItemStack weedLeaf = DrugItems.WEED_LEAF.clone();
            ItemUtil.setQuality(weedLeaf, 10); // keep your existing behavior

            block.getWorld().dropItemNaturally(block.getLocation(), weedLeaf);
            block.getWorld().dropItemNaturally(block.getLocation(), DrugItems.WEED_SEEDS.clone());
        }
    }

    // ---- Planting ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (clicked.getType() != Material.FARMLAND) return;

        Block above = clicked.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR) return;

        ItemStack item = event.getItem();
        if (!ItemUtil.isCustomItem(item, "weed_seeds")) return;

        // Purple glass is a requirement to PLANT.
        if (!hasEnoughLight(above) || !hasMarker(above)) {
            event.setCancelled(true);
            return;
        }

        // Place weed crop
        above.setType(CROP_BLOCK);
        Ageable age = (Ageable) above.getBlockData();
        age.setAge(0);
        above.setBlockData(age);

        addWeedPos(above);

        event.getPlayer().playSound(above.getLocation(), Sound.ITEM_CROP_PLANT, 1.0f, 1.0f);

        // Consume 1 seed
        item.setAmount(item.getAmount() - 1);

        event.setCancelled(true);
    }

    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!ItemUtil.isCustomItem(item, "weed_seeds")) return;

        // We want weed to behave like a normal field crop (on farmland), even though the seed item
        // may be based on a placeable vanilla item (e.g. cocoa beans).
        Block against = event.getBlockAgainst();
        if (against != null && against.getType() == Material.FARMLAND) {
            Block above = against.getRelative(BlockFace.UP);
            if (above.getType() != Material.AIR) {
                event.setCancelled(true);
                return;
            }

            // Purple glass is required to PLANT.
            if (!hasMarker(above)) {
                event.setCancelled(true);
                return;
            }

            // Place the actual crop block and register it as weed.
            above.setType(CROP_BLOCK);
            Ageable age = (Ageable) above.getBlockData();
            age.setAge(0);
            above.setBlockData(age);

            addWeedPos(above);

            event.getPlayer().playSound(above.getLocation(), Sound.ITEM_CROP_PLANT, 1.0f, 1.0f);

            // Consume 1 seed from the hand used for this placement attempt
            ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
            if (hand != null && hand.getAmount() > 0) {
                hand.setAmount(hand.getAmount() - 1);
                event.getPlayer().getInventory().setItem(event.getHand(), hand);
            }

            event.setCancelled(true);
            return;
        }

        // Prevent placing weed seeds as their vanilla block form (e.g. cocoa) on other blocks.
        event.setCancelled(true);
    }

// ---- Bonemeal ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBonemeal(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BONE_MEAL) return;

        Block block = event.getClickedBlock();
        if (block.getType() != CROP_BLOCK) return;

        // Only restrict bonemeal on weed crops.
        if (!isWeedCrop(block)) return;

        if (!hasEnoughLight(block) || !hasMarker(block)) {
            event.setCancelled(true);
        }
    }
}

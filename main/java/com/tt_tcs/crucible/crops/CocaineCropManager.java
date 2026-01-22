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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * Cocaine crop manager.
 *
 * IMPORTANT: We do NOT infer "this is coca" from block type + soil anymore.
 * Sweet berry bushes can exist on podzol naturally or via vanilla planting.
 *
 * A bush is coca ONLY if it was planted using the custom coca seeds, and its block
 * location is stored in the owning chunk's PersistentDataContainer.
 */
public class CocaineCropManager implements Listener {

    public static final Material CROP_BLOCK = Material.SWEET_BERRY_BUSH;
    private static final Set<Material> VALID_SOIL = Set.of(Material.PODZOL);

    private static final int AGE_4_STAR = 2;
    private static final int AGE_5_STAR = 3;

    // Chunk-stored coca registry
    private static final NamespacedKey COCA_BLOCKS_KEY =
            new NamespacedKey(CrucibleMain.getInstance(), "coca_blocks");

    // Sweet berry bushes can exist below 0, so offset to pack y as non-negative.
    private static final int Y_OFFSET = 64;

    /* ================================================== */
    /* Registry helpers                                    */
    /* ================================================== */

    private static int pack(Block b) {
        int x = b.getX() & 15;
        int z = b.getZ() & 15;
        int y = b.getY() + Y_OFFSET;
        // pack: y (up to ~383) in high bits, x/z in low bits
        return (y << 8) | (x << 4) | z;
    }

    private static Set<Integer> readSet(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        byte[] data = pdc.get(COCA_BLOCKS_KEY, PersistentDataType.BYTE_ARRAY);
        Set<Integer> out = new HashSet<>();
        if (data == null || data.length == 0) return out;

        ByteBuffer buf = ByteBuffer.wrap(data);
        while (buf.remaining() >= Integer.BYTES) {
            out.add(buf.getInt());
        }
        return out;
    }

    private static void writeSet(Chunk chunk, Set<Integer> set) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (set == null || set.isEmpty()) {
            pdc.remove(COCA_BLOCKS_KEY);
            return;
        }

        ByteBuffer buf = ByteBuffer.allocate(set.size() * Integer.BYTES);
        for (int v : set) buf.putInt(v);
        pdc.set(COCA_BLOCKS_KEY, PersistentDataType.BYTE_ARRAY, buf.array());
    }

    private static boolean isRegisteredCoca(Block block) {
        if (block == null) return false;
        if (block.getType() != CROP_BLOCK) return false;

        Set<Integer> set = readSet(block.getChunk());
        return set.contains(pack(block));
    }

    private static void registerCoca(Block block) {
        Set<Integer> set = readSet(block.getChunk());
        set.add(pack(block));
        writeSet(block.getChunk(), set);
    }

    private static void unregisterCoca(Block block) {
        Set<Integer> set = readSet(block.getChunk());
        if (set.remove(pack(block))) {
            writeSet(block.getChunk(), set);
        }
    }

    /* ================================================== */
    /* Planting                                             */
    /* ================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Coca can only be planted on podzol.
        if (!VALID_SOIL.contains(clicked.getType())) return;

        Block above = clicked.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR) return;

        ItemStack item = event.getItem();
        if (!ItemUtil.isCustomItem(item, "coca_seeds")) return;

        // Place bush + register identity.
        above.setType(CROP_BLOCK, false);

        Ageable ageable = (Ageable) above.getBlockData();
        ageable.setAge(0);
        above.setBlockData(ageable, false);

        registerCoca(above);

        event.getPlayer().playSound(above.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PLACE, 1.0f, 1.0f);

        item.setAmount(item.getAmount() - 1);
        event.setCancelled(true);
    }

    /* ================================================== */
    /* Growth + bonemeal (ONLY for registered coca)         */
    /* ================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBonemeal(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isRegisteredCoca(block)) return;

        // require light level 7+ for coca to grow
        if (block.getLightLevel() < 7) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BONE_MEAL) return;

        Ageable ageable = (Ageable) block.getBlockData();

        if (ageable.getAge() >= ageable.getMaximumAge()) {
            event.setCancelled(true);
            return;
        }

        ageable.setAge(ageable.getAge() + 1);
        block.setBlockData(ageable, false);

        item.setAmount(item.getAmount() - 1);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (!isRegisteredCoca(block)) return;

        // must remain on valid soil (podzol)
        Block soil = block.getRelative(BlockFace.DOWN);
        if (!VALID_SOIL.contains(soil.getType())) {
            // If someone broke the podzol under it, treat it as no longer coca.
            unregisterCoca(block);
            return;
        }

        if (block.getLightLevel() < 7) {
            event.setCancelled(true);
        }
    }

    /* ================================================== */
    /* Harvest + break (ONLY for registered coca)           */
    /* ================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.BONE_MEAL) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isRegisteredCoca(block)) return;

        Ageable ageable = (Ageable) block.getBlockData();
        int age = ageable.getAge();

        if (age == AGE_4_STAR || age == AGE_5_STAR) {
            ItemStack leaf = DrugItems.COCA_LEAF.clone();
            ItemUtil.setQuality(leaf, age == AGE_4_STAR ? 8 : 10);
            block.getWorld().dropItemNaturally(block.getLocation(), leaf);

            event.getPlayer().playSound(block.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.0f);

            ageable.setAge(1);
            block.setBlockData(ageable, false);

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isRegisteredCoca(block)) return;

        // must remain on valid soil (podzol)
        Block soil = block.getRelative(BlockFace.DOWN);
        if (!VALID_SOIL.contains(soil.getType())) {
            unregisterCoca(block);
            return;
        }

        Ageable ageable = (Ageable) block.getBlockData();
        int age = ageable.getAge();

        event.setDropItems(false);

        event.getPlayer().playSound(block.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_BREAK, 1.0f, 1.0f);

        block.getWorld().dropItemNaturally(block.getLocation(), DrugItems.COCA_SEEDS.clone());

        if (age == AGE_4_STAR) {
            ItemStack leaf = DrugItems.COCA_LEAF.clone();
            ItemUtil.setQuality(leaf, 8);
            block.getWorld().dropItemNaturally(block.getLocation(), leaf);
        } else if (age == AGE_5_STAR) {
            ItemStack leaf = DrugItems.COCA_LEAF.clone();
            ItemUtil.setQuality(leaf, 10);
            block.getWorld().dropItemNaturally(block.getLocation(), leaf);
        }

        // Remove identity after breaking (WorldGuard-safe because ignoreCancelled=true).
        unregisterCoca(block);
    }
}

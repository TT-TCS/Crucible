package com.tt_tcs.crucible.crops;

import com.tt_tcs.crucible.drugs.DrugItems;
import com.tt_tcs.crucible.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class EphedraCropManager implements Listener {

    private static final Material CROP_BLOCK = Material.SUGAR_CANE;
    private static final Set<Material> VALID_SOIL = Set.of(Material.SAND, Material.RED_SAND);

    @EventHandler
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (!VALID_SOIL.contains(clicked.getType())) return;

        if (clicked.getRelative(BlockFace.UP).getLightLevel() < 7) {
            event.setCancelled(true);
            return;
        }

        Block above = clicked.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR) return;

        ItemStack item = event.getItem();
        if (!ItemUtil.isCustomItem(item, "ephedra_seeds")) return;

        above.setType(CROP_BLOCK);

        event.getPlayer().playSound(above.getLocation(), Sound.ITEM_CROP_PLANT, 1.0f, 1.0f);

        item.setAmount(item.getAmount() - 1);

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVanillaSugarCanePlacement(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        if (placed.getType() != CROP_BLOCK) return;

        Block below = placed.getRelative(BlockFace.DOWN);
        if (below.getType() != CROP_BLOCK) return;

        Block base = getBase(below);
        if (base == null) return;

        Block soil = base.getRelative(BlockFace.DOWN);
        if (!VALID_SOIL.contains(soil.getType())) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (block.getType() != CROP_BLOCK) return;

        Block base = getBase(block);
        if (base == null) return;

        Block soil = base.getRelative(BlockFace.DOWN);
        if (!VALID_SOIL.contains(soil.getType())) return;

        int height = 0;
        Block scan = base;
        while (scan.getType() == CROP_BLOCK && height < 6) {
            height++;
            scan = scan.getRelative(BlockFace.UP);
        }
        if (height >= 2) {
            event.setCancelled(true);
            return;
        }

        if (block.getLightLevel() < 7) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != CROP_BLOCK) return;

        Block base = getBase(block);
        if (base == null) return;

        Block soil = base.getRelative(BlockFace.DOWN);
        if (!VALID_SOIL.contains(soil.getType())) return;

        event.setCancelled(true);
        event.setDropItems(false);

        event.getPlayer().playSound(block.getLocation(), Sound.BLOCK_CROP_BREAK, 1.0f, 1.0f);

        int quality = 10;

        java.util.List<Block> blocks = new java.util.ArrayList<>();
        Block scan = base;
        while (scan.getType() == CROP_BLOCK) {
            blocks.add(scan);
            scan = scan.getRelative(BlockFace.UP);
        }

        for (int i = blocks.size() - 1; i >= 0; i--) {
            blocks.get(i).setType(Material.AIR, false);
        }

        Block dropLocation = base;
        dropLocation.getWorld().dropItemNaturally(dropLocation.getLocation(), DrugItems.EPHEDRA_SEEDS.clone());

        int ephedraCount = Math.max(0, blocks.size() - 1);
        for (int i = 0; i < ephedraCount; i++) {
            ItemStack ephedra = DrugItems.EPHEDRA.clone();
            ItemUtil.setQuality(ephedra, quality);
            dropLocation.getWorld().dropItemNaturally(dropLocation.getLocation(), ephedra);
        }
    }

    private Block getBase(Block block) {
        Block current = block;
        while (current.getType() == CROP_BLOCK) {
            Block below = current.getRelative(BlockFace.DOWN);
            if (below.getType() != CROP_BLOCK) {
                return current;
            }
            current = below;
        }
        return null;
    }
}
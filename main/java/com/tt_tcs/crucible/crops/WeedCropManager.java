package com.tt_tcs.crucible.crops;

import com.tt_tcs.crucible.drugs.DrugItems;
import com.tt_tcs.crucible.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class WeedCropManager implements Listener {

    private static final Material CROP_BLOCK = Material.WHEAT;

    @EventHandler
    public void onCropGrow(BlockGrowEvent event) {
        Block block = event.getBlock();

        if (block.getType() != CROP_BLOCK) return;

        if (block.getLightLevel() < 7) {
            event.setCancelled(true);
            return;
        }

        Block glassBlock = block.getRelative(0, 2, 0);
        if (glassBlock.getType() != Material.PURPLE_STAINED_GLASS) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != CROP_BLOCK) return;

        Ageable ageable = (Ageable) block.getBlockData();

        event.setDropItems(false);

        event.getPlayer().playSound(block.getLocation(), Sound.BLOCK_CROP_BREAK, 1.0f, 1.0f);

        if (ageable.getAge() < ageable.getMaximumAge()) {
            // not fully grown - drop 1 weed seed
            event.getBlock().getWorld().dropItemNaturally(block.getLocation(), DrugItems.WEED_SEEDS.clone());
        } else {
            // fully grown - drop weed leaf + weed seed
            ItemStack weedLeaf = DrugItems.WEED_LEAF.clone();
            ItemUtil.setQuality(weedLeaf, 10);

            event.getBlock().getWorld().dropItemNaturally(block.getLocation(), weedLeaf);
            event.getBlock().getWorld().dropItemNaturally(block.getLocation(), DrugItems.WEED_SEEDS.clone());
        }
    }

    @EventHandler
    public void onPlant(PlayerInteractEvent event) {

        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (clicked.getType() != Material.FARMLAND) return;

        Block above = clicked.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR) return;

        if (above.getLightLevel() < 7) {
            event.setCancelled(true);
            return;
        }

        ItemStack item = event.getItem();
        if (!ItemUtil.isCustomItem(item, "weed_seeds")) return;

        above.setType(Material.WHEAT);

        Ageable ageable = (Ageable) above.getBlockData();
        ageable.setAge(0);
        above.setBlockData(ageable);

        event.getPlayer().playSound(above.getLocation(), Sound.ITEM_CROP_PLANT, 1.0f, 1.0f);

        item.setAmount(item.getAmount() - 1);

        event.setCancelled(true);
    }

    @EventHandler
    public void onBonemeal(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        if (event.getItem() == null) return;
        if (event.getItem().getType() != Material.BONE_MEAL) return;

        Block block = event.getClickedBlock();
        if (block.getType() != CROP_BLOCK) return;


        if (block.getLightLevel() < 7) {
            event.setCancelled(true);
            return;
        }

        Block glassBlock = block.getRelative(0, 2, 0);
        if (glassBlock.getType() != Material.PURPLE_STAINED_GLASS) {
            event.setCancelled(true);
        }
    }
}
package com.tt_tcs.crucible.crops;

import com.tt_tcs.crucible.drugs.DrugItems;
import com.tt_tcs.crucible.util.ItemUtil;
import org.bukkit.Material;
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
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class PsilocybeCropManager implements Listener {

    private static final Material SOIL = Material.MOSS_BLOCK;
    private static final Material CROP_BLOCK = Material.POTATOES;

    private boolean isPsilocybeCrop(Block crop) {
        if (crop == null || crop.getType() != CROP_BLOCK) return false;
        Block below = crop.getRelative(BlockFace.DOWN);
        return below.getType() == SOIL;
    }

    @EventHandler
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (clicked.getType() != SOIL) return;

        ItemStack item = event.getItem();
        if (!ItemUtil.isCustomItem(item, "psilocybe_seeds")) return;

        Block above = clicked.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR) return;

        if (above.getLightLevel() < 7) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        above.setType(CROP_BLOCK);
        Ageable age = (Ageable) above.getBlockData();
        age.setAge(0);
        above.setBlockData(age);

        event.getPlayer().playSound(above.getLocation(), Sound.ITEM_CROP_PLANT, 1.0f, 1.0f);
        item.setAmount(item.getAmount() - 1);
    }

    @EventHandler
    public void onCropGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (!isPsilocybeCrop(block)) return;

        if (block.getLightLevel() < 7) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!isPsilocybeCrop(block)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onBonemeal(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getItem() == null) return;
        if (event.getItem().getType() != Material.BONE_MEAL) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isPsilocybeCrop(block)) return;

        if (block.getLightLevel() < 7) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isPsilocybeCrop(block)) return;

        if (!(block.getBlockData() instanceof Ageable age)) return;
        event.setDropItems(false);

        event.getPlayer().playSound(block.getLocation(), Sound.BLOCK_CROP_BREAK, 1.0f, 1.0f);

        if (age.getAge() < age.getMaximumAge()) {
            // not fully grown - drop seeds
            block.getWorld().dropItemNaturally(block.getLocation(), DrugItems.PSILOCYBE_SEEDS.clone());
            return;
        }

        // fully grown - drop 1-3 shrooms
        int count = ThreadLocalRandom.current().nextInt(1, 4);
        for (int i = 0; i < count; i++) {
            ItemStack mush = DrugItems.PSILOCYBE_MUSHROOM.clone();
            if (ItemUtil.supportsQuality(mush)) {
                ItemUtil.setQuality(mush, 10);
            }
            block.getWorld().dropItemNaturally(block.getLocation(), mush);
        }
    }
}

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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class CocaineCropManager implements Listener {

    public static final Material CROP_BLOCK = Material.SWEET_BERRY_BUSH;
    private static final Set<Material> VALID_SOIL = Set.of(Material.PODZOL);

    private static final int AGE_4_STAR = 2;
    private static final int AGE_5_STAR = 3;

    @EventHandler
    public void onBonemeal(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != CROP_BLOCK) return;

        // require light level 7+ for coca to grow
        if (block.getLightLevel() < 7) {
            event.setCancelled(true);
            return;
        }

        // check if this is a coca crop
        Block soil = block.getRelative(BlockFace.DOWN);
        if (!VALID_SOIL.contains(soil.getType())) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BONE_MEAL) return;

        Ageable ageable = (Ageable) block.getBlockData();

        if (ageable.getAge() >= ageable.getMaximumAge()) {
            event.setCancelled(true);
            return;
        }

        ageable.setAge(ageable.getAge() + 1);
        block.setBlockData(ageable);

        item.setAmount(item.getAmount() - 1);
        event.setCancelled(true);
    }

    @EventHandler
    public void onGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (block.getType() != CROP_BLOCK) return;

        Block soil = block.getRelative(BlockFace.DOWN);
        if (!VALID_SOIL.contains(soil.getType())) return;

        if (block.getLightLevel() < 7) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Prevent coca seeds from behaving like vanilla wheat seeds on farmland.
        ItemStack item = event.getItem();
        if (ItemUtil.isCustomItem(item, "coca_seeds") && clicked.getType() == Material.FARMLAND) {
            event.setCancelled(true);
            return;
        }

        // Continue with custom planting rules below.

        if (!VALID_SOIL.contains(clicked.getType())) return;

        Block above = clicked.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR) return;
        if (!ItemUtil.isCustomItem(item, "coca_seeds")) return;

        above.setType(CROP_BLOCK);

        Ageable ageable = (Ageable) above.getBlockData();
        ageable.setAge(0);
        above.setBlockData(ageable);

        event.getPlayer().playSound(above.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PLACE, 1.0f, 1.0f);

        item.setAmount(item.getAmount() - 1);

        event.setCancelled(true);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.BONE_MEAL) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        if (block.getType() != CROP_BLOCK) return;

        // check if this is a coca crop
        Block soil = block.getRelative(BlockFace.DOWN);
        if (!VALID_SOIL.contains(soil.getType())) return;

        Ageable ageable = (Ageable) block.getBlockData();
        int age = ageable.getAge();

        if (age == AGE_4_STAR || age == AGE_5_STAR) {
            ItemStack leaf = DrugItems.COCA_LEAF.clone();
            ItemUtil.setQuality(leaf, age == AGE_4_STAR ? 8 : 10);
            block.getWorld().dropItemNaturally(block.getLocation(), leaf);

            event.getPlayer().playSound(block.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.0f);

            ageable.setAge(1);
            block.setBlockData(ageable);

            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != CROP_BLOCK) return;

        // check if this is a coca crop
        Block soil = block.getRelative(BlockFace.DOWN);
        if (!VALID_SOIL.contains(soil.getType())) return;

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
    }
}
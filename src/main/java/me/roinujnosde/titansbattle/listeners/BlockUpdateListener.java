package me.roinujnosde.titansbattle.listeners;

import me.roinujnosde.titansbattle.BaseGame;
import me.roinujnosde.titansbattle.TitansBattle;
import me.roinujnosde.titansbattle.types.Warrior;

import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;

public class BlockUpdateListener extends TBListener {

    public BlockUpdateListener(@NotNull TitansBattle plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BaseGame game = plugin.getBaseGameFrom(player);
        if (game == null) {
            return;
        }

        Warrior warrior = plugin.getDatabaseManager().getWarrior(player);
        if (!game.isInBattle(warrior)) {
            event.setCancelled(true);
            return;
        }

        if (!game.getConfig().isAllowBlockBreak()) {
            event.setCancelled(true);
            return;
        }

        if (game.getConfig().isAllowBreakOnlyPlacedBlocks()) {
            if (!game.isPlacedBlock(event.getBlock().getLocation())) {
                event.setCancelled(true);
                return;
            }
        }

        List<String> whitelist = game.getConfig().getWhitelistedDropMaterials();
        if (whitelist != null && !whitelist.contains(event.getBlock().getType().name())) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
            game.removePlacedBlock(event.getBlock().getLocation());
            return;
        }

        if (!event.isCancelled()) {
            game.removePlacedBlock(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        BaseGame game = plugin.getBaseGameFrom(player);
        if (game == null) {
            return;
        }

        Warrior warrior = plugin.getDatabaseManager().getWarrior(player);
        if (!game.isInBattle(warrior)) {
            event.setCancelled(true);
            return;
        }

        if (!game.getConfig().isAllowBlockPlace()) {
            event.setCancelled(true);
            return;
        }

        if (!event.isCancelled()) {
            game.addPlacedBlock(event.getBlock().getLocation());

            List<String> autoRemove = game.getConfig().getAutoRemoveBlocks();
            if (autoRemove != null && autoRemove.contains(event.getBlock().getType().name())) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (game.isPlacedBlock(event.getBlock().getLocation())) {
                        event.getBlock().setType(Material.AIR);
                        game.removePlacedBlock(event.getBlock().getLocation());
                    }
                }, 1200L); // 1 minute
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        processExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        processExplosion(event.blockList());
    }

    private void processExplosion(List<Block> blockList) {
        me.roinujnosde.titansbattle.games.Game game = plugin.getGameManager().getCurrentGame().orElse(null);
        if (game == null || !game.getConfig().isAllowBlockBreak()) {
            return;
        }

        Iterator<Block> iterator = blockList.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();

            if (game.getConfig().isAllowBreakOnlyPlacedBlocks()) {
                if (!game.isPlacedBlock(block.getLocation())) {
                    iterator.remove();
                    continue;
                }
            }

            List<String> whitelist = game.getConfig().getWhitelistedDropMaterials();
            if (whitelist != null && !whitelist.contains(block.getType().name())) {
                block.setType(org.bukkit.Material.AIR);
                iterator.remove();
                continue;
            }

            game.removePlacedBlock(block.getLocation());
        }
    }

}

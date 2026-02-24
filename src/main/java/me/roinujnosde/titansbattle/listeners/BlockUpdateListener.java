package me.roinujnosde.titansbattle.listeners;

import me.roinujnosde.titansbattle.BaseGame;
import me.roinujnosde.titansbattle.TitansBattle;
import me.roinujnosde.titansbattle.types.Warrior;

import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
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

        if (!game.getConfig().isAllowBlockInteraction()) {
            event.setCancelled(true);
            return;
        }

        List<String> whitelist = game.getConfig().getWhitelistedDropMaterials();
        if (whitelist != null && !whitelist.contains(event.getBlock().getType().name())) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        cancel(event.getPlayer(), event);
    }

    private void cancel(Player player, Cancellable event) {
        BaseGame game = plugin.getBaseGameFrom(player);
        if (game == null) {
            return;
        }
        if (game.getConfig().isAllowBlockInteraction()) {
            return;
        }

        Warrior warrior = plugin.getDatabaseManager().getWarrior(player);
        if (!game.isInBattle(warrior)) {
            event.setCancelled(true);
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
        if (game == null || !game.getConfig().isAllowBlockInteraction()) {
            return;
        }

        List<String> whitelist = game.getConfig().getWhitelistedDropMaterials();
        if (whitelist != null) {
            Iterator<Block> iterator = blockList.iterator();
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (!whitelist.contains(block.getType().name())) {
                    block.setType(org.bukkit.Material.AIR);
                    iterator.remove();
                }
            }
        }
    }

}

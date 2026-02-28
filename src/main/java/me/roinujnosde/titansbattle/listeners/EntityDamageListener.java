package me.roinujnosde.titansbattle.listeners;

import me.roinujnosde.titansbattle.BaseGame;
import me.roinujnosde.titansbattle.BaseGameConfiguration;
import me.roinujnosde.titansbattle.TitansBattle;
import me.roinujnosde.titansbattle.managers.DatabaseManager;
import me.roinujnosde.titansbattle.managers.GroupManager;
import me.roinujnosde.titansbattle.types.Warrior;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

public class EntityDamageListener extends TBListener {

    public EntityDamageListener(@NotNull TitansBattle plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageLowest(EntityDamageEvent event) {
        boolean disableFfMessages = plugin.getConfig().getBoolean("disable-ff-messages", true);
        if (disableFfMessages && isParticipant(event.getEntity())) {
            // Cancelling so other plugins don't display messages such as "can't hit an
            // ally" during the game
            event.setCancelled(true);
        }
    }

    // mcMMO's listener is on HIGHEST and ignoreCancelled = true, this will run
    // before
    // Aurellium / Auraskills is on HIGH
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamage(EntityDamageEvent event) {
        DatabaseManager dm = plugin.getDatabaseManager();

        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player defender = (Player) event.getEntity();
        BaseGame game = plugin.getBaseGameFrom(defender);
        if (game == null) {
            return;
        }

        if (!game.isInBattle(dm.getWarrior(defender))) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
        Player attacker = null;
        if (event.getDamageSource().getCausingEntity() instanceof Player) {
            attacker = (Player) event.getDamageSource().getCausingEntity();
        }
        if (attacker != null || event instanceof EntityDamageByEntityEvent) {
            processEntityDamageEvent(event, defender, attacker, game);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player attacker = null;
        if (event.getDamageSource().getCausingEntity() instanceof Player) {
            attacker = (Player) event.getDamageSource().getCausingEntity();
        }
        if (attacker != null) {
            Player defender = (Player) event.getEntity();
            BaseGame game = plugin.getBaseGameFrom(defender);
            if (game != null) {
                Warrior attackerWarrior = plugin.getDatabaseManager().getWarrior(attacker);
                if (game.isInBattle(attackerWarrior) && isDamageTypeAllowed(event, game)) {
                    game.addDamageDealt(attackerWarrior, event.getFinalDamage());
                }
            }
        }
    }

    private void processEntityDamageEvent(EntityDamageEvent event, Player defender, Player attacker, BaseGame game) {
        DatabaseManager dm = plugin.getDatabaseManager();

        if (attacker != null && !isDamageTypeAllowed(event, game)) {
            event.setCancelled(true);
            return;
        }
        if (attacker != null) {
            Warrior attackerWarrior = dm.getWarrior(attacker);
            Warrior defenderWarrior = dm.getWarrior(defender);
            if (!game.getConfig().isPvP() || !game.isInBattle(attackerWarrior)) {
                event.setCancelled(true);
                return;
            }
            game.updateCombatTime(attackerWarrior);
            game.updateCombatTime(defenderWarrior);
        }
        if (attacker == null || !game.getConfig().isGroupMode()) {
            return;
        }

        GroupManager groupManager = TitansBattle.getInstance().getGroupManager();
        if (groupManager != null) {
            if (defender.equals(attacker)) {
                return;
            }
            event.setCancelled(groupManager.sameGroup(defender.getUniqueId(), attacker.getUniqueId()));
        }
    }

    private boolean isDamageTypeAllowed(EntityDamageEvent event, BaseGame game) {
        BaseGameConfiguration config = game.getConfig();
        if (event.getDamageSource().getDirectEntity() instanceof Projectile) {
            return config.isRangedDamage();
        } else {
            return config.isMeleeDamage();
        }
    }

    private boolean isParticipant(Entity entity) {
        if (entity instanceof Player) {
            return plugin.getBaseGameFrom((Player) entity) != null;
        }
        return false;
    }

}

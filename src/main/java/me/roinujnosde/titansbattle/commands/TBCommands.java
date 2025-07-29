package me.roinujnosde.titansbattle.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import me.roinujnosde.titansbattle.BaseGameConfiguration;
import me.roinujnosde.titansbattle.TitansBattle;
import me.roinujnosde.titansbattle.challenges.ArenaConfiguration;
import me.roinujnosde.titansbattle.dao.ConfigurationDao;
import me.roinujnosde.titansbattle.exceptions.CommandNotSupportedException;
import me.roinujnosde.titansbattle.games.Game;
import me.roinujnosde.titansbattle.managers.ConfigManager;
import me.roinujnosde.titansbattle.managers.DatabaseManager;
import me.roinujnosde.titansbattle.managers.GameManager;
import me.roinujnosde.titansbattle.managers.TaskManager;
import me.roinujnosde.titansbattle.types.GameConfiguration;
import me.roinujnosde.titansbattle.types.Warrior;
import me.roinujnosde.titansbattle.types.Winners;
import me.roinujnosde.titansbattle.utils.Helper;
import me.roinujnosde.titansbattle.utils.SoundUtils;
import me.roinujnosde.titansbattle.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@CommandAlias("%titansbattle|tb")
public class TBCommands extends BaseCommand {

    @Dependency
    private TitansBattle plugin;
    @Dependency
    private GameManager gameManager;
    @Dependency
    private TaskManager taskManager;
    @Dependency
    private ConfigManager configManager;
    @Dependency
    private DatabaseManager databaseManager;
    @Dependency
    private ConfigurationDao configDao;

    // Spectator tracking
    private static final Set<UUID> SPECTATORS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ConcurrentHashMap<UUID, Integer> SPECTATOR_TASKS = new ConcurrentHashMap<>();

    @Subcommand("%start|start")
    @CommandPermission("titansbattle.start")
    @CommandCompletion("@games")
    @Description("{@@command.description.start}")
    public void start(CommandSender sender, @Values("@games") @Conditions("ready") GameConfiguration game) {
        java.util.Optional<Game> currentGame = gameManager.getCurrentGame();
        if (currentGame.isPresent()) {
            sender.sendMessage(plugin.getLang("starting-or-started", currentGame.orElse(null)));
            return;
        }
        gameManager.start(game);
    }

    @Subcommand("%setwinner|setwinner")
    @CommandPermission("titansbattle.setwinner")
    @CommandCompletion("@players")
    @Description("{@@command.description.setwinner}")
    @Conditions("happening")
    public void setWinner(CommandSender sender, Game game, @Conditions("participant") OnlinePlayer winner) {
        Warrior warrior = databaseManager.getWarrior(winner.player);
        try {
            game.setWinner(warrior);
        } catch (CommandNotSupportedException e) {
            sender.sendMessage(plugin.getLang("command-not-supported-by-game", game));
        }
    }

    @Subcommand("%kick|kick")
    @CommandPermission("titansbattle.kick")
    @Conditions("happening")
    @Description("{@@command.description.kick}")
    public void kick(CommandSender sender, Game game, OnlinePlayer player) {
        Warrior warrior = databaseManager.getWarrior(player.getPlayer());
        String wName = warrior.getName();
        if (!game.isParticipant(warrior)) {
            sender.sendMessage(MessageFormat.format(plugin.getLang("player_not_participating", game), wName));
            return;
        }
        game.onKick(warrior);
        sender.sendMessage(MessageFormat.format(plugin.getLang("has_been_kicked"), wName));
    }

    @Subcommand("%cancel|cancel")
    @CommandPermission("titansbattle.cancel")
    @Conditions("happening")
    @Description("{@@command.description.cancel}")
    public void cancel(CommandSender sender, Game game) {
        game.cancel(sender);
    }

    @Subcommand("%reload|reload")
    @CommandPermission("titansbattle.reload")
    @Description("{@@command.description.reload}")
    public void reload(CommandSender sender) {
        gameManager.getCurrentGame().ifPresent(game -> game.cancel(sender));
        plugin.saveDefaultConfig();
        configManager.load();
        plugin.getLanguageManager().reload();
        configDao.loadConfigurations();
        taskManager.setupScheduler();
        sender.sendMessage(plugin.getLang("configuration-reloaded"));
    }

    @Subcommand("%join|join")
    @CommandPermission("titansbattle.join")
    @Conditions("happening")
    @Description("{@@command.description.join}")
    public void join(Player sender) {
        plugin.debug(String.format("%s used /tb join", sender.getName()));
        gameManager.getCurrentGame().ifPresent(g -> g.onJoin(databaseManager.getWarrior(sender)));
    }

    @Subcommand("%exit|exit|leave")
    @CommandPermission("titansbattle.exit")
    @Description("{@@command.description.exit}")
    public void leave(Player sender) {
        UUID uuid = sender.getUniqueId();
        if (SPECTATORS.contains(uuid)) {
            SPECTATORS.remove(uuid);
            Integer taskId = SPECTATOR_TASKS.remove(uuid);
            if (taskId != null) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
            // Teleport to spawn or general exit
            Location exit = plugin.getConfigManager().getGeneralExit();
            if (exit == null) {
                exit = sender.getWorld().getSpawnLocation();
            }
            sender.teleport(exit);
            sender.setGameMode(GameMode.SURVIVAL);
            sender.setAllowFlight(false);
            sender.setFlying(false);
            sender.sendMessage("§aVocê saiu do modo espectador do evento.");
            return;
        }
        Warrior warrior = databaseManager.getWarrior(sender);
        plugin.getBaseGameFrom(sender).onLeave(warrior);
    }

    @Subcommand("%help|help")
    @CatchUnknown
    @Default
    @Description("{@@command.description.help}")
    public void doHelp(CommandHelp help) {
        help.showHelp();
    }

    @Subcommand("%winners|winners")
    @CommandPermission("titansbattle.winners")
    @CommandCompletion("@games @winners_dates")
    @Description("{@@command.description.winners}")
    public void winners(CommandSender sender, @Values("@games") GameConfiguration game, @Optional @Nullable Date date) {
        Winners winners = databaseManager.getLatestWinners();
        if (date != null) {
            winners = databaseManager.getWinners(date);
        }
        date = winners.getDate();

        List<UUID> playerWinners = winners.getPlayerWinners(game.getName());
        String members;
        if (playerWinners == null) {
            members = plugin.getLang("winners-no-player-winners", game);
        } else {
            members = Helper.buildStringFrom(Helper.uuidListToPlayerNameList(playerWinners));
        }
        UUID uuid = winners.getKiller(game.getName());
        String name;
        if (uuid == null) {
            name = plugin.getLang("winners-no-killer", game);
        } else {
            name = databaseManager.getWarrior(uuid).getName();
        }

        String group = winners.getWinnerGroup(game.getName());
        if (group == null) {
            group = plugin.getLang("winners-no-winner-group", game);
        }
        String dateFormat = plugin.getConfigManager().getDateFormat();
        sender.sendMessage(MessageFormat.format(plugin.getLang("winners", game),
                new SimpleDateFormat(dateFormat).format(date), name, group, members));
    }

    @Subcommand("%watch|watch")
    @CommandPermission("titansbattle.watch")
    @CommandCompletion("@arenas:in_use")
    @Description("{@@command.description.watch}")
    public void watch(Player sender, Game game, @Optional ArenaConfiguration arena) {
        BaseGameConfiguration config;
        if (arena == null && game == null) {
            sender.sendMessage(plugin.getLang("not-starting-or-started"));
            return;
        }
        config = (arena == null) ? game.getConfig() : arena;

        Location watchroom = config.getWatchroom();
        sender.teleport(watchroom);

        // Make user in spectator mode and clear inventory
        sender.setGameMode(GameMode.SPECTATOR);
        sender.setAllowFlight(true);
        sender.setFlying(true);
        sender.getInventory().clear();
        SoundUtils.playSound(SoundUtils.Type.WATCH, plugin.getConfig(), sender);

        // Add to spectators and start actionbar
        UUID uuid = sender.getUniqueId();
        SPECTATORS.add(uuid);
        Integer oldTask = SPECTATOR_TASKS.remove(uuid);
        if (oldTask != null) {
            Bukkit.getScheduler().cancelTask(oldTask);
        }
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!SPECTATORS.contains(uuid) || !sender.isOnline() || sender.getGameMode() != GameMode.SPECTATOR) {
                    this.cancel();
                    SPECTATOR_TASKS.remove(uuid);
                    return;
                }
                MessageUtils.sendActionBar(sender, "§cPara sair, use /gladiador sair");
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();
        SPECTATOR_TASKS.put(uuid, taskId);

        // Send list of current spectators
        String list = SPECTATORS.stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .map(Player::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(nenhum)");
        sender.sendMessage("§eEspectadores atuais: " + list);
    }

}

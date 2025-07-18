package me.roinujnosde.titansbattle.hooks.papi;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.roinujnosde.titansbattle.TitansBattle;
import me.roinujnosde.titansbattle.games.Game;
import me.roinujnosde.titansbattle.managers.DatabaseManager;
import me.roinujnosde.titansbattle.types.GameConfiguration;
import me.roinujnosde.titansbattle.types.Group;
import me.roinujnosde.titansbattle.types.Warrior;
import me.roinujnosde.titansbattle.types.Winners;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.valueOf;

public class TBExpansion extends PlaceholderExpansion {

    private final TitansBattle plugin;

    private static final List<String> PLACEHOLDERS;
    private static final Pattern PARTICIPANTS_SIZE;
    private static final Pattern GROUPS_SIZE;
    private static final Pattern IS_STARTING;
    private static final Pattern GAME_NAME;
    private static final Pattern ARENA_IN_USE_PATTERN;
    private static final Pattern LAST_WINNER_GROUP_PATTERN;
    private static final Pattern LAST_WINNER_KILLER_PATTERN;
    private static final Pattern PREFIX_PATTERN;

    static {
        PARTICIPANTS_SIZE = Pattern.compile("participants_size");
        GROUPS_SIZE = Pattern.compile("groups_size");
        IS_STARTING = Pattern.compile("is_starting");
        GAME_NAME = Pattern.compile("game_name");
        ARENA_IN_USE_PATTERN = Pattern.compile("arena_in_use_(?<arena>\\S+)");
        LAST_WINNER_GROUP_PATTERN = Pattern.compile("last_winner_group_(?<game>\\S+)");
        LAST_WINNER_KILLER_PATTERN = Pattern.compile("last_(?<type>winner|killer)_(?<game>\\S+)");
        PREFIX_PATTERN = Pattern.compile("(?<game>^\\S+)_(?<type>winner|killer)_prefix");
        PLACEHOLDERS = Arrays.asList("%titansbattle_groups_size%" ,"%titansbattle_participants_size%" ,"%titansbattle_arena_in_use_<arena>%", "%titansbattle_last_winner_group_<game>%",
                "%titansbattle_last_<killer|winner>_<game>%", "%titansbattle_<game>_<killer|winner>_prefix%",
                "%titansbattle_group_total_victories%", "%titansbattle_total_kills%", "%titansbattle_total_deaths%");
    }

    public TBExpansion(TitansBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull String getIdentifier() {
        return plugin.getName().toLowerCase();
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @NotNull List<String> getPlaceholders() {
        return PLACEHOLDERS;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {

        Matcher participantsSizeMatcher = PARTICIPANTS_SIZE.matcher(params);
        if (participantsSizeMatcher.matches()) {
            Optional<Game> currentGame = plugin.getGameManager().getCurrentGame();
            return currentGame.map(game -> String.valueOf(game.getParticipants().size()))
                    .orElse("0");
        }

        Matcher isStartingMatcher = IS_STARTING.matcher(params);
        if (isStartingMatcher.matches()) {
            Optional<Game> currentGame = plugin.getGameManager().getCurrentGame();
            return currentGame.map(game -> game.isLobby() ? "true" : "false").orElse("false");
        }

        Matcher gameNameMatcher = GAME_NAME.matcher(params);
        if (gameNameMatcher.matches()) {
            Optional<Game> currentGame = plugin.getGameManager().getCurrentGame();
            String gameName = currentGame.map(game -> game.getConfig().getName()).orElse(null);
            if (gameName == null) {
                return "Nenhum";
            }
            switch (gameName) {
                case "MiniGlad-Nethpot":
                    return "&c&lMINIGLAD ➜ &eNethpot";
                case "MiniGlad-Maces":
                    return "&c&lMINIGLAD ➜ &eMaces";
                case "MiniGlad-Projetil":
                    return "&c&lMINIGLAD ➜ &eProjetil";
                case "MiniGlad-SMP":
                    return "&c&lMINIGLAD ➜ &eSMP";
                case "MiniGlad-Dima":
                    return "&c&lMINIGLAD ➜ &eDima";
                case "Gladiador":
                    return "&4&lGLADIADOR";
                default:
                    return "Nenhum";
            }
        }

        Matcher groupsSizeMatcher = GROUPS_SIZE.matcher(params);
        if (groupsSizeMatcher.matches()) {
            Optional<Game> currentGame = plugin.getGameManager().getCurrentGame();
            return currentGame.map(game -> String.valueOf(game.getGroupParticipants().size()))
                    .orElse("0");
        }

        Matcher arenaInUse = ARENA_IN_USE_PATTERN.matcher(params);
        if (arenaInUse.find()) {
            String arenaName = arenaInUse.group("arena");
            return toString(plugin.getChallengeManager().isArenaInUse(arenaName));
        }
        Matcher lastWinnerGroup = LAST_WINNER_GROUP_PATTERN.matcher(params);
        if (lastWinnerGroup.find()) {
            return getLastWinnerGroup(lastWinnerGroup.group("game"));
        }
        Matcher lastWinnerKiller = LAST_WINNER_KILLER_PATTERN.matcher(params);
        if (lastWinnerKiller.find()) {
            String game = lastWinnerKiller.group("game");
            String type = lastWinnerKiller.group("type");
            switch (type) {
                case "killer":
                    return getLastKiller(game);
                case "winner":
                    return getLastWinner(game);
            }
        }

        if (player == null) {
            return "";
        }
        Matcher prefix = PREFIX_PATTERN.matcher(params);
        if (prefix.find()) {
            String game = prefix.group("game");
            String type = prefix.group("type");
            switch (type) {
                case "killer":
                    return getKillerPrefix(player, game);
                case "winner":
                    return getWinnerPrefix(player, game);
            }
        }
        Warrior warrior = plugin.getDatabaseManager().getWarrior(player);
        switch (params) {
            case "group_total_victories":
                Group group = warrior.getGroup();
                return group != null ? valueOf(group.getData().getTotalVictories()) : "0";
            case "total_victories":
                return valueOf(warrior.getTotalVictories());
            case "total_kills":
                return valueOf(warrior.getTotalKills());
            case "total_deaths":
                return valueOf(warrior.getTotalDeaths());
        }
        return null;
    }

    @NotNull
    private String getWinnerPrefix(@NotNull OfflinePlayer player, @NotNull String game) {
        Optional<GameConfiguration> config = plugin.getConfigurationDao().getConfiguration(game, GameConfiguration.class);
        if (!config.isPresent()) {
            plugin.debug(String.format("game %s not found", game));
            return "";
        }
        Winners latestWinners = plugin.getDatabaseManager().getLatestWinners();
        List<UUID> playerWinners = latestWinners.getPlayerWinners(game);
        if (playerWinners == null || !playerWinners.contains(player.getUniqueId())) {
            plugin.debug(String.format("player winners: %s", playerWinners));
            return "";
        }
        String prefix = config.get().getWinnerPrefix();
        plugin.debug("prefix: " + prefix);
        return prefix != null ? prefix : "";
    }

    @NotNull
    private String getKillerPrefix(@NotNull OfflinePlayer player, @NotNull String game) {
        Optional<GameConfiguration> config = plugin.getConfigurationDao().getConfiguration(game, GameConfiguration.class);
        if (!config.isPresent()) {
            return "";
        }
        Winners latestWinners = plugin.getDatabaseManager().getLatestWinners();
        UUID killerUuid = latestWinners.getKiller(game);
        if (killerUuid == null || !killerUuid.equals(player.getUniqueId())) {
            return "";
        }
        String prefix = config.get().getKillerPrefix();
        return prefix != null ? prefix : "";
    }

    private @NotNull String getLastWinner(String game) {
        Optional<Winners> winners = getLastWinnersMatching(w -> {
            List<UUID> list = w.getPlayerWinners(game);
            return list != null && !list.isEmpty();
        });
        DatabaseManager db = plugin.getDatabaseManager();

        return winners.map(value -> value.getPlayerWinners(game).stream().map(db::getWarrior)
                .map(Warrior::getName).collect(Collectors.joining(", "))).orElse("");
    }

    private @NotNull String getLastKiller(String game) {
        Optional<Winners> winners = getLastWinnersMatching(w -> w.getKiller(game) != null);
        if (!winners.isPresent()) {
            return "";
        }
        UUID killer = winners.get().getKiller(game);
        return plugin.getDatabaseManager().getWarrior(killer).getName();
    }

    private @NotNull String getLastWinnerGroup(String game) {
        Optional<Winners> winner = getLastWinnersMatching(w -> w.getWinnerGroup(game) != null);
        if (!winner.isPresent()) {
            return "";
        }
        return winner.get().getWinnerGroup(game);
    }

    private Optional<Winners> getLastWinnersMatching(Predicate<Winners> filter) {
        return plugin.getDatabaseManager().getWinners().stream().sorted(Comparator.reverseOrder())
                .filter(filter).findFirst();
    }

    private String toString(boolean bool) {
        return bool ? PlaceholderAPIPlugin.booleanTrue() : PlaceholderAPIPlugin.booleanFalse();
    }
}

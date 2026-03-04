package me.roinujnosde.titansbattle.hooks.papi;

import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.roinujnosde.titansbattle.BaseGame;
import me.roinujnosde.titansbattle.TitansBattle;
import me.roinujnosde.titansbattle.games.Game;
import me.roinujnosde.titansbattle.managers.DatabaseManager;
import me.roinujnosde.titansbattle.types.GameConfiguration;
import me.roinujnosde.titansbattle.types.Group;
import me.roinujnosde.titansbattle.types.Warrior;
import me.roinujnosde.titansbattle.types.Winners;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    private static final double WATCHROOM_RADIUS = 100.0;

    static {
        PARTICIPANTS_SIZE = Pattern.compile("participants_size");
        GROUPS_SIZE = Pattern.compile("groups_size");
        IS_STARTING = Pattern.compile("is_starting");
        GAME_NAME = Pattern.compile("game_name");
        ARENA_IN_USE_PATTERN = Pattern.compile("arena_in_use_(?<arena>\\S+)");
        LAST_WINNER_GROUP_PATTERN = Pattern.compile("last_winner_group_(?<game>\\S+)");
        LAST_WINNER_KILLER_PATTERN = Pattern.compile("last_(?<type>winner|killer)_(?<game>\\S+)");
        PREFIX_PATTERN = Pattern.compile("(?<game>^\\S+)_(?<type>winner|killer)_prefix");
        PLACEHOLDERS = Arrays.asList(
                // Existing placeholders
                "%titansbattle_groups_size%", "%titansbattle_participants_size%",
                "%titansbattle_arena_in_use_<arena>%", "%titansbattle_last_winner_group_<game>%",
                "%titansbattle_last_<killer|winner>_<game>%", "%titansbattle_<game>_<killer|winner>_prefix%",
                "%titansbattle_group_total_victories%", "%titansbattle_total_kills%", "%titansbattle_total_deaths%",
                // New: per-player placeholders
                "%titansbattle_player_is_in_game%",
                "%titansbattle_player_is_spectating%",
                "%titansbattle_player_game_name%",
                "%titansbattle_player_kills%",
                "%titansbattle_player_deaths%",
                "%titansbattle_player_group_name%",
                "%titansbattle_player_group_remaining%",
                "%titansbattle_player_group_kills%",
                "%titansbattle_player_damage_dealt%",
                "%titansbattle_player_is_in_lobby%",
                "%titansbattle_player_is_in_battle%",
                // New: game-global placeholders
                "%titansbattle_game_is_active%",
                "%titansbattle_game_is_lobby%",
                "%titansbattle_game_killer%",
                "%titansbattle_game_killer_kills%",
                "%titansbattle_game_alive_count%",
                "%titansbattle_game_groups_remaining%",
                "%titansbattle_game_type%",
                "%titansbattle_game_max_players%",
                "%titansbattle_game_casualties_count%");
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
        Optional<Game> currentGame = plugin.getGameManager().getCurrentGame();

        // ─── Existing legacy placeholders ──────────────────────────────

        Matcher participantsSizeMatcher = PARTICIPANTS_SIZE.matcher(params);
        if (participantsSizeMatcher.matches()) {
            return currentGame.map(game -> String.valueOf(game.getParticipants().size()))
                    .orElse("0");
        }

        Matcher isStartingMatcher = IS_STARTING.matcher(params);
        if (isStartingMatcher.matches()) {
            return currentGame.map(game -> game.isLobby() ? "true" : "false").orElse("false");
        }

        Matcher gameNameMatcher = GAME_NAME.matcher(params);
        if (gameNameMatcher.matches()) {
            String gameName = currentGame.map(game -> game.getConfig().getName()).orElse(null);
            if (gameName == null) {
                return "Nenhum";
            }
            switch (gameName) {
                case "MiniGlad-Nethpot":
                    return "&c&lMINIGLAD ➜ &eNethpot";
                case "MiniGlad-Maces":
                    return "&c&lMINIGLAD ➜ &eMaces";
                case "MiniGlad-Projeteis":
                    return "&c&lMINIGLAD ➜ &eProjetéis";
                case "MiniGlad-SMP":
                    return "&c&lMINIGLAD ➜ &eSMP";
                case "MiniGlad-Dima":
                    return "&c&lMINIGLAD ➜ &eDima";
                case "MiniGlad-DimaSMP":
                    return "&c&lMINIGLAD ➜ &eDimaSMP";
                case "MiniGlad-Machadinha":
                    return "&c&lMINIGLAD ➜ &eMachadinha";
                case "MiniGlad-CPvP":
                    return "&c&lMINIGLAD ➜ &eCrystal PvP";
                case "Gladiador-Nethpot":
                    return "&4&lGLADIADOR ➜ &eNethpot";
                case "Gladiador-SMP":
                    return "&4&lGLADIADOR ➜ &eSMP";
                case "Gladiador-CPvP":
                    return "&4&lGLADIADOR ➜ &eCrystal PvP";
                default:
                    return "Nenhum";
            }
        }

        Matcher groupsSizeMatcher = GROUPS_SIZE.matcher(params);
        if (groupsSizeMatcher.matches()) {
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

        // ─── New: Game-global placeholders (no player required) ────────

        switch (params) {
            case "game_is_active":
                return toString(currentGame.isPresent());
            case "game_is_lobby":
                return currentGame.map(game -> toString(game.isLobby())).orElse(toString(false));
            case "game_killer": {
                return currentGame.map(game -> {
                    Warrior killer = game.findKiller();
                    return killer != null ? killer.getName() : "";
                }).orElse("");
            }
            case "game_killer_kills": {
                return currentGame.map(game -> {
                    Warrior killer = game.findKiller();
                    if (killer == null)
                        return "0";
                    return valueOf(game.getKillsCount().getOrDefault(killer, 0));
                }).orElse("0");
            }
            case "game_alive_count": {
                return currentGame.map(game -> valueOf(game.getParticipants().size())).orElse("0");
            }
            case "game_groups_remaining":
                return currentGame.map(game -> valueOf(game.getGroupParticipants().size())).orElse("0");
            case "game_type": {
                return currentGame.map(game -> {
                    if (game.getConfig() instanceof GameConfiguration) {
                        return ((GameConfiguration) game.getConfig()).getType();
                    }
                    return game.getClass().getSimpleName();
                }).orElse("");
            }
            case "game_max_players":
                return currentGame.map(game -> valueOf(game.getConfig().getMaximumPlayers())).orElse("0");
            case "game_casualties_count":
                return currentGame.map(game -> valueOf(game.getCasualties().size())).orElse("0");
        }

        // ─── Player-required placeholders ──────────────────────────────

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

        // ─── New: Per-player placeholders ──────────────────────────────

        switch (params) {
            case "player_is_in_game":
                return currentGame.map(game -> toString(game.isParticipant(warrior))).orElse(toString(false));

            case "player_is_spectating":
                return toString(isSpectating(player, currentGame.orElse(null)));

            case "player_game_name":
                return currentGame
                        .filter(game -> game.isParticipant(warrior) || game.getCasualties().contains(warrior)
                                || isSpectating(player, game))
                        .map(game -> game.getConfig().getName())
                        .orElse("");

            case "player_kills":
                return currentGame
                        .filter(game -> game.isParticipant(warrior) || game.getCasualties().contains(warrior)
                                || isSpectating(player, game))
                        .map(game -> valueOf(game.getKillsCount().getOrDefault(warrior, 0)))
                        .orElse("0");

            case "player_deaths":
                return currentGame
                        .filter(game -> game.isParticipant(warrior) || game.getCasualties().contains(warrior)
                                || isSpectating(player, game))
                        .map(game -> game.getCasualties().contains(warrior) ? "1" : "0")
                        .orElse("0");

            case "player_group_name":
                return currentGame
                        .filter(game -> game.isParticipant(warrior) || game.getCasualties().contains(warrior)
                                || isSpectating(player, game))
                        .map(game -> {
                            Group group = warrior.getGroup();
                            return group != null ? group.getName() : "";
                        })
                        .orElse("");

            case "player_group_remaining":
                return currentGame
                        .filter(game -> game.isParticipant(warrior) || game.getCasualties().contains(warrior)
                                || isSpectating(player, game))
                        .map(game -> {
                            Group group = warrior.getGroup();
                            if (group == null)
                                return "0";
                            Integer count = game.getGroupParticipants().get(group);
                            return valueOf(count != null ? count : 0);
                        })
                        .orElse("0");

            case "player_group_kills":
                return currentGame
                        .filter(game -> game.isParticipant(warrior) || game.getCasualties().contains(warrior)
                                || isSpectating(player, game))
                        .map(game -> {
                            Group group = warrior.getGroup();
                            if (group == null)
                                return "0";
                            int totalKills = 0;
                            for (Map.Entry<Warrior, Integer> entry : game.getKillsCount().entrySet()) {
                                Group entryGroup = entry.getKey().getGroup();
                                if (group.equals(entryGroup)) {
                                    totalKills += entry.getValue();
                                }
                            }
                            return valueOf(totalKills);
                        })
                        .orElse("0");

            case "player_damage_dealt":
                return currentGame
                        .filter(game -> game.isParticipant(warrior) || game.getCasualties().contains(warrior)
                                || isSpectating(player, game))
                        .map(game -> valueOf((int) game.getDamageDealt(warrior)))
                        .orElse("0");

            case "player_is_in_lobby":
                return currentGame
                        .filter(game -> game.isParticipant(warrior) || game.getCasualties().contains(warrior)
                                || isSpectating(player, game))
                        .map(game -> toString(game.isLobby()))
                        .orElse(toString(false));

            case "player_is_in_battle":
                return currentGame
                        .map(game -> toString(game.isBattle() && game.isParticipant(warrior)))
                        .orElse(toString(false));

            // ─── Existing per-player placeholders ──────────────────────
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

    /**
     * Checks if a player is spectating the current game.
     * A player is spectating if:
     * 1. They died in the game and are in the watchroom (casualtiesWatching), OR
     * 2. They are NOT a participant but are physically near the watchroom location.
     */
    private boolean isSpectating(@NotNull OfflinePlayer player, @Nullable Game game) {
        if (game == null) {
            return false;
        }
        Warrior warrior = plugin.getDatabaseManager().getWarrior(player);

        // Case 1: died in game and watching from watchroom
        if (game.getCasualtiesWatching().contains(warrior)) {
            return true;
        }

        // Case 2: non-participant physically in the watchroom area (camarote)
        if (!game.isParticipant(warrior)) {
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer == null) {
                return false;
            }
            Location watchroom = game.getConfig().getWatchroom();
            if (watchroom != null && watchroom.getWorld() != null
                    && watchroom.getWorld().equals(onlinePlayer.getWorld())) {
                return onlinePlayer.getLocation().distanceSquared(watchroom) <= (WATCHROOM_RADIUS * WATCHROOM_RADIUS);
            }
        }
        return false;
    }

    @NotNull
    private String getWinnerPrefix(@NotNull OfflinePlayer player, @NotNull String game) {
        Optional<GameConfiguration> config = plugin.getConfigurationDao().getConfiguration(game,
                GameConfiguration.class);
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
        Optional<GameConfiguration> config = plugin.getConfigurationDao().getConfiguration(game,
                GameConfiguration.class);
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

package me.roinujnosde.titansbattle.games;

import me.roinujnosde.titansbattle.TitansBattle;
import me.roinujnosde.titansbattle.events.GroupWinEvent;
import me.roinujnosde.titansbattle.events.PlayerWinEvent;
import me.roinujnosde.titansbattle.hooks.discord.DiscordAnnounces;
import me.roinujnosde.titansbattle.managers.GroupManager;
import me.roinujnosde.titansbattle.types.*;
import me.roinujnosde.titansbattle.utils.Helper;
import me.roinujnosde.titansbattle.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static me.roinujnosde.titansbattle.BaseGameConfiguration.Prize.FIRST;
import static me.roinujnosde.titansbattle.BaseGameConfiguration.Prize.SECOND;
import static me.roinujnosde.titansbattle.BaseGameConfiguration.Prize.THIRD;
import static me.roinujnosde.titansbattle.BaseGameConfiguration.Prize.KILLER;
import static me.roinujnosde.titansbattle.utils.SoundUtils.Type.VICTORY;

public class FreeForAllGame extends Game {

    private @Nullable Group winnerGroup;
    private @Nullable Group secondPlaceGroup;
    private @Nullable Group thirdPlaceGroup;
    private @Nullable Warrior killer;
    private @NotNull List<Warrior> winners = new ArrayList<>();
    private @Nullable List<Warrior> secondPlaceWinners;
    private @Nullable List<Warrior> thirdPlaceWinners;
    private final List<EliminationRecord> eliminationOrder = new ArrayList<>();
    private long startTime;
    private @Nullable BukkitTask deathmatchTask;
    private boolean deathmatchStarted;

    // Saved inventories for readd functionality
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, Integer> savedKillsBackup = new HashMap<>();

    private @Nullable BossBar deathmatchBossBar;
    private final Map<Warrior, Integer> earlyKillsCount = new HashMap<>();

    private static class EliminationRecord {
        final Group group;
        final List<Warrior> members;
        final long timestamp;

        EliminationRecord(Group group, List<Warrior> members) {
            this.group = group;
            this.members = new ArrayList<>(members);
            this.timestamp = System.currentTimeMillis();
        }
    }

    public FreeForAllGame(TitansBattle plugin, GameConfiguration config) {
        super(plugin, config);
    }

    @Override
    public void onDeath(@NotNull Warrior victim, @Nullable Warrior killer) {
        // Save inventory before processing death
        saveInventoryAsync(victim);
        saveKillCount(victim);

        if (!deathmatchStarted && killer != null) {
            earlyKillsCount.put(killer, earlyKillsCount.getOrDefault(killer, 0) + 1);
        }

        super.onDeath(victim, killer);
    }

    private void saveInventoryAsync(@NotNull Warrior warrior) {
        Player player = warrior.toOnlinePlayer();
        if (player == null)
            return;

        // Copy inventory on main thread (Bukkit requires this)
        ItemStack[] inventory = player.getInventory().getContents().clone();
        ItemStack[] armor = player.getInventory().getArmorContents().clone();
        UUID uuid = warrior.getUniqueId();

        // Clone items asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ItemStack[] invCopy = Arrays.stream(inventory)
                    .map(item -> item != null ? item.clone() : null)
                    .toArray(ItemStack[]::new);
            ItemStack[] armorCopy = Arrays.stream(armor)
                    .map(item -> item != null ? item.clone() : null)
                    .toArray(ItemStack[]::new);

            // Store on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                savedInventories.put(uuid, invCopy);
                savedArmor.put(uuid, armorCopy);
            });
        });
    }

    private void saveKillCount(@NotNull Warrior warrior) {
        int kills = killsCount.getOrDefault(warrior, 0);
        savedKillsBackup.put(warrior.getUniqueId(), kills);
    }

    /**
     * Re-adds a player to the game with their saved inventory.
     * 
     * @param warrior The warrior to re-add
     * @return true if the player was successfully re-added, false otherwise
     */
    public boolean readd(@NotNull Warrior warrior) {
        Player player = warrior.toOnlinePlayer();
        if (player == null)
            return false;

        UUID uuid = warrior.getUniqueId();
        if (!savedInventories.containsKey(uuid)) {
            return false;
        }

        // Remove from casualties and casualtiesWatching
        casualties.remove(warrior);
        casualtiesWatching.remove(warrior);

        // Re-add to participants
        if (!participants.contains(warrior)) {
            participants.add(warrior);
        }

        // Restore group
        if (!groups.containsKey(warrior)) {
            groups.put(warrior, warrior.getGroup());
        }

        // Restore kills
        Integer savedKills = savedKillsBackup.get(uuid);
        if (savedKills != null) {
            killsCount.put(warrior, savedKills);
        }

        // Teleport to arena center
        Location destination = getReaddDestination();
        if (destination != null) {
            teleport(warrior, destination);
        }

        // Restore inventory
        ItemStack[] inv = savedInventories.get(uuid);
        ItemStack[] armor = savedArmor.get(uuid);
        if (inv != null) {
            player.getInventory().setContents(inv);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }

        // Heal and set game mode
        healAndClearEffects(warrior);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);

        if (deathmatchStarted) {
            applyDeathmatchEffects(Collections.singletonList(warrior));
            if (deathmatchBossBar != null && player != null) {
                deathmatchBossBar.addPlayer(player);
            }
        }

        return true;
    }

    /**
     * Checks if a player can be re-added to the game.
     * 
     * @param warrior The warrior to check
     * @return true if the player has a saved inventory and can be re-added
     */
    public boolean canReadd(@NotNull Warrior warrior) {
        return savedInventories.containsKey(warrior.getUniqueId());
    }

    @Override
    public boolean isInBattle(@NotNull Warrior warrior) {
        return battle && participants.contains(warrior);
    }

    @Override
    public @NotNull Collection<Warrior> getCurrentFighters() {
        return participants;
    }

    @Override
    protected void processRemainingPlayers(@NotNull Warrior warrior) {
        if (cancelled) {
            return;
        }
        // Track elimination BEFORE checking remaining players
        trackEliminationIfNecessary(warrior);

        if (getConfig().isGroupMode()) {
            if (getGroupParticipants().size() == 1) {
                determinePodiumFromEliminations();
                killer = findKiller();
                getGroupParticipants().keySet().stream().findAny().ifPresent(g -> {
                    winnerGroup = g;
                    getParticipants().stream().filter(p -> g.isMember(p.getUniqueId())).forEach(winners::add);
                });
                finish(false);
            }
        } else if (participants.size() == 1) {
            determinePodiumFromEliminations();
            killer = findKiller();
            winners = getParticipants();
            finish(false);
        }
    }

    private void trackEliminationIfNecessary(@NotNull Warrior warrior) {
        if (getConfig().isGroupMode()) {
            Group group = getGroup(warrior);
            // Check if this group will be eliminated after this warrior dies
            if (group != null) {
                // Count remaining alive members in this group after this warrior is removed
                long remainingMembers = getParticipants().stream()
                        .filter(p -> group.isMember(p.getUniqueId()) && !p.equals(warrior))
                        .count();

                // If no members left, this group is being eliminated
                if (remainingMembers == 0) {
                    List<Warrior> allGroupMembers = getCasualties().stream()
                            .filter(p -> group.isMember(p.getUniqueId()))
                            .collect(Collectors.toList());
                    allGroupMembers.add(warrior); // Include the current dying warrior
                    eliminationOrder.add(new EliminationRecord(group, allGroupMembers));
                }
            }
        } else {
            // In individual mode, each warrior elimination is tracked individually
            eliminationOrder.add(new EliminationRecord(null, Collections.singletonList(warrior)));
        }
    }

    @Override
    protected void onLobbyEnd() {
        super.onLobbyEnd();
        this.startTime = System.currentTimeMillis();
        String clans = getParticipantClans();
        String players = getParticipantPlayers();
        int clanCount = getParticipantClanCount();
        int playerCount = getParticipants().size();
        broadcastKey("game_started", getConfig().getPreparationTime(), clans, clanCount, playerCount);

        DiscordAnnounces.announceStarted(getConfig().getName(), clans, players);

        teleportToArena(getParticipants());
        startPreparation();
        scheduleDeathmatch();
    }

    private void determinePodiumFromEliminations() {
        // Second place: last eliminated team (lost in finals)
        if (eliminationOrder.size() >= 1) {
            EliminationRecord secondPlace = eliminationOrder.get(eliminationOrder.size() - 1);
            secondPlaceGroup = secondPlace.group;
            secondPlaceWinners = new ArrayList<>(secondPlace.members);
        }

        // Third place: second-to-last eliminated team (lost in semifinals)
        if (eliminationOrder.size() >= 2) {
            EliminationRecord thirdPlace = eliminationOrder.get(eliminationOrder.size() - 2);
            thirdPlaceGroup = thirdPlace.group;
            thirdPlaceWinners = new ArrayList<>(thirdPlace.members);
        }
    }

    @Override
    protected void processWinners(boolean awardAllPrizes) {
        String gameName = getConfig().getName();
        Winners today = databaseManager.getTodaysWinners();
        if (getConfig().isUseKits()) {
            winners.forEach(Kit::clearInventory);
            if (secondPlaceWinners != null) {
                secondPlaceWinners.forEach(Kit::clearInventory);
            }
            if (thirdPlaceWinners != null) {
                thirdPlaceWinners.forEach(Kit::clearInventory);
            }
        }
        if (winnerGroup != null) {
            Bukkit.getPluginManager().callEvent(new GroupWinEvent(winnerGroup));
            winnerGroup.getData().increaseVictories(gameName);
            today.setWinnerGroup(gameName, winnerGroup.getName());
            getCasualties().stream().filter(p -> winnerGroup.isMember(p.getUniqueId())).forEach(winners::add);
        }

        SoundUtils.playSound(VICTORY, plugin.getConfig(), winners, secondPlaceWinners, thirdPlaceWinners);
        PlayerWinEvent event = new PlayerWinEvent(this, winners);
        Bukkit.getPluginManager().callEvent(event);

        if (killer != null) {
            plugin.getGameManager().setKiller(getConfig(), killer, null);
            SoundUtils.playSound(VICTORY, plugin.getConfig(), killer.toOnlinePlayer());
            discordAnnounce("discord_who_won_killer", killer.getName(), killsCount.get(killer));
            givePrizes(KILLER, null, Collections.singletonList(killer));
            today.setKiller(gameName, killer.getUniqueId());
        }

        today.setWinners(gameName, Helper.warriorListToUuidList(winners));
        if (secondPlaceWinners != null) {
            today.setSecondPlaceWinners(gameName, Helper.warriorListToUuidList(secondPlaceWinners));
            if (secondPlaceGroup != null) {
                today.setSecondPlaceGroup(gameName, secondPlaceGroup.getName());
            }
        }
        if (thirdPlaceWinners != null) {
            today.setThirdPlaceWinners(gameName, Helper.warriorListToUuidList(thirdPlaceWinners));
            if (thirdPlaceGroup != null) {
                today.setThirdPlaceGroup(gameName, thirdPlaceGroup.getName());
            }
        }

        String winnerName = getWinnerName(winnerGroup, winners);
        String secondPlaceName = getWinnerName(secondPlaceGroup, secondPlaceWinners);
        String thirdPlaceName = getWinnerName(thirdPlaceGroup, thirdPlaceWinners);
        long duration = getEventDurationMinutes();

        if (secondPlaceWinners != null || thirdPlaceWinners != null) {
            int winnerPlayersCount = winners != null ? winners.size() : 0;
            int winnerKillsCount = getWinnerGroupTotalKills();
            int secondPlacePlayersCount = secondPlaceWinners != null ? secondPlaceWinners.size() : 0;
            int secondPlaceKillsCount = getGroupKills(secondPlaceGroup, secondPlaceWinners);
            int thirdPlacePlayersCount = thirdPlaceWinners != null ? thirdPlaceWinners.size() : 0;
            int thirdPlaceKillsCount = getGroupKills(thirdPlaceGroup, thirdPlaceWinners);

            int winnerPoints = getConfig().getLeaguePointsFirst();
            int winnerKillsPoints = winnerKillsCount * getConfig().getLeaguePointsKill();
            int secondPlacePoints = getConfig().getLeaguePointsSecond();
            int secondPlaceKillsPoints = secondPlaceKillsCount * getConfig().getLeaguePointsKill();
            int thirdPlacePoints = getConfig().getLeaguePointsThird();
            int thirdPlaceKillsPoints = thirdPlaceKillsCount * getConfig().getLeaguePointsKill();

            killer = findKiller();
            int killerKillsCount = killer != null && killsCount.containsKey(killer) ? killsCount.get(killer) : 0;
            broadcastKey("who_won_freeforall_podium",
                    winnerName, winnerPoints, winnerPlayersCount, winnerKillsCount, winnerKillsPoints,
                    secondPlaceName, secondPlacePoints, secondPlacePlayersCount, secondPlaceKillsCount,
                    secondPlaceKillsPoints,
                    thirdPlaceName, thirdPlacePoints, thirdPlacePlayersCount, thirdPlaceKillsCount,
                    thirdPlaceKillsPoints,
                    duration,
                    killer.getName(), killerKillsCount);
            DiscordAnnounces.announceEnded(
                    gameName, winnerName, winnerPoints, winnerPlayersCount, winnerKillsCount, winnerKillsPoints,
                    secondPlaceName, secondPlacePoints, secondPlacePlayersCount, secondPlaceKillsCount,
                    secondPlaceKillsPoints,
                    thirdPlaceName, thirdPlacePoints, thirdPlacePlayersCount, thirdPlaceKillsCount,
                    thirdPlaceKillsPoints,
                    duration,
                    killer.getName(), killerKillsCount,
                    gameLogger != null ? gameLogger.getLogFile() : null);

            if (gameLogger != null) {
                gameLogger.logLine("==== CLASSIFICACAO FINAL ====");

                int killPoints = getConfig().getLeaguePointsKill();
                int earlyBonus = getConfig().getEarlyKillPointsBonus();

                int winnerEarlyKills = getGroupEarlyKills(winnerGroup, winners);
                int winnerDMKills = winnerKillsCount - winnerEarlyKills;
                gameLogger.logLine("🏆 1º LUGAR: " + winnerName + " (" + winnerPoints + " pts)");
                gameLogger.logLine("   | Participantes: " + winnerPlayersCount);
                gameLogger.logLine("   | Kills antes Deathmatch: " + winnerEarlyKills + " ("
                        + (winnerEarlyKills * (killPoints + earlyBonus)) + " pts)");
                gameLogger.logLine("   | Kills depois Deathmatch: " + winnerDMKills + " ("
                        + (winnerDMKills * killPoints) + " pts)");
                gameLogger.logLine("   | Kills Totais: " + winnerKillsCount + " ("
                        + ((winnerEarlyKills * (killPoints + earlyBonus)) + (winnerDMKills * killPoints)) + " pts)");

                int secondEarlyKills = getGroupEarlyKills(secondPlaceGroup, secondPlaceWinners);
                int secondDMKills = secondPlaceKillsCount - secondEarlyKills;
                gameLogger.logLine("🥈 2º LUGAR: " + secondPlaceName + " (" + secondPlacePoints + " pts)");
                gameLogger.logLine("   | Participantes: " + secondPlacePlayersCount);
                gameLogger.logLine("   | Kills antes Deathmatch: " + secondEarlyKills + " ("
                        + (secondEarlyKills * (killPoints + earlyBonus)) + " pts)");
                gameLogger.logLine("   | Kills depois Deathmatch: " + secondDMKills + " ("
                        + (secondDMKills * killPoints) + " pts)");
                gameLogger.logLine("   | Kills Totais: " + secondPlaceKillsCount + " ("
                        + ((secondEarlyKills * (killPoints + earlyBonus)) + (secondDMKills * killPoints)) + " pts)");

                int thirdEarlyKills = getGroupEarlyKills(thirdPlaceGroup, thirdPlaceWinners);
                int thirdDMKills = thirdPlaceKillsCount - thirdEarlyKills;
                gameLogger.logLine("🥉 3º LUGAR: " + thirdPlaceName + " (" + thirdPlacePoints + " pts)");
                gameLogger.logLine("   | Participantes: " + thirdPlacePlayersCount);
                gameLogger.logLine("   | Kills antes Deathmatch: " + thirdEarlyKills + " ("
                        + (thirdEarlyKills * (killPoints + earlyBonus)) + " pts)");
                gameLogger.logLine(
                        "   | Kills depois Deathmatch: " + thirdDMKills + " (" + (thirdDMKills * killPoints) + " pts)");
                gameLogger.logLine("   | Kills Totais: " + thirdPlaceKillsCount + " ("
                        + ((thirdEarlyKills * (killPoints + earlyBonus)) + (thirdDMKills * killPoints)) + " pts)");

                gameLogger.logLine("==================================");
                gameLogger.logLine("⭐ MVP (Mais Abates): " + (killer != null ? killer.getName() : "N/A") + " com "
                        + killerKillsCount + " eliminações");
                gameLogger.logLine("⏱️ Tempo de Evento: " + duration + " minutos");

                gameLogger.logLine("==== Outros Participantes ====");
                List<Group> allGroups = getParticipants().stream()
                        .map(this::getGroup)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());

                for (Group g : allGroups) {
                    if (g.equals(winnerGroup) || g.equals(secondPlaceGroup) || g.equals(thirdPlaceGroup))
                        continue;
                    List<Warrior> groupWarriors = getParticipants().stream().filter(w -> g.isMember(w.getUniqueId()))
                            .collect(Collectors.toList());
                    int gTotalKills = getGroupKills(g, groupWarriors);
                    int gEarlyKills = getGroupEarlyKills(g, groupWarriors);
                    int gDMKills = gTotalKills - gEarlyKills;
                    gameLogger.logLine("🔸 " + g.getName());
                    gameLogger.logLine("   | Participantes: " + groupWarriors.size());
                    gameLogger.logLine("   | Kills antes Deathmatch: " + gEarlyKills + " ("
                            + (gEarlyKills * (killPoints + earlyBonus)) + " pts)");
                    gameLogger.logLine(
                            "   | Kills depois Deathmatch: " + gDMKills + " (" + (gDMKills * killPoints) + " pts)");
                    gameLogger.logLine("   | Kills Totais: " + gTotalKills + " ("
                            + ((gEarlyKills * (killPoints + earlyBonus)) + (gDMKills * killPoints)) + " pts)");
                }

                gameLogger.logLine("==================================");
                gameLogger.logLine("==== RELATÓRIO DE DANO ====");

                List<Map.Entry<Warrior, Double>> dmgList = new ArrayList<>(getDamageDealtMap().entrySet());
                dmgList.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                for (Map.Entry<Warrior, Double> entry : dmgList) {
                    Warrior w = entry.getKey();
                    int kills = killsCount.getOrDefault(w, 0);
                    gameLogger.logLine("⚔️ " + w.getName() + " -> Dano Causado: "
                            + String.format(Locale.US, "%.2f", entry.getValue()) + " | Eliminações: " + kills);
                }
            }
            discordAnnounce("discord_who_won_freeforall_podium",
                    winnerName, winnerPoints, winnerPlayersCount, winnerKillsCount, winnerKillsPoints,
                    secondPlaceName, secondPlacePoints, secondPlacePlayersCount, secondPlaceKillsCount,
                    secondPlaceKillsPoints,
                    thirdPlaceName, thirdPlacePoints, thirdPlacePlayersCount, thirdPlaceKillsCount,
                    thirdPlaceKillsPoints,
                    duration,
                    killer.getName(), killerKillsCount);
        } else {
            int totalKills = getWinnerGroupTotalKills();
            int totalPlayers = getWinnerGroupTotalPlayers();
            broadcastKey("who_won", winnerName, totalKills, totalPlayers, duration);
            discordAnnounce("discord_who_won", winnerName);
        }

        winners.forEach(w -> w.increaseVictories(gameName));
        givePrizes(FIRST, winnerGroup, winners);
        givePrizes(SECOND, secondPlaceGroup, secondPlaceWinners);
        givePrizes(THIRD, thirdPlaceGroup, thirdPlaceWinners);
        // Schedule sync command after 5 seconds
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "sync all uclan reload addons"),
                100L);

        // League points integration
        GameConfiguration config = getConfig();
        String eventName = getEventNameForLeague();
        if (awardAllPrizes) {
            if (winnerGroup != null && config.getLeaguePointsFirst() > 0) {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                        String.format("clanleague addevent %s %d %s", winnerGroup.getName(),
                                config.getLeaguePointsFirst(), "1º Lugar - " + eventName));
            }
            if (secondPlaceGroup != null && config.getLeaguePointsSecond() > 0) {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                        String.format("clanleague addevent %s %d %s", secondPlaceGroup.getName(),
                                config.getLeaguePointsSecond(), "2º Lugar - " + eventName));
            }
            if (thirdPlaceGroup != null && config.getLeaguePointsThird() > 0) {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                        String.format("clanleague addevent %s %d %s", thirdPlaceGroup.getName(),
                                config.getLeaguePointsThird(), "3º Lugar - " + eventName));
            }
        }

        // Award kill points to all participants who got kills
        int killPoints = config.getLeaguePointsKill();
        int earlyBonus = config.getEarlyKillPointsBonus();
        if (killPoints > 0 || earlyBonus > 0) {
            for (Map.Entry<Warrior, Integer> entry : getKillsCount().entrySet()) {
                Warrior killerPlayer = entry.getKey();
                int kills = entry.getValue();
                if (kills > 0) {
                    Group killerGroup = getGroup(killerPlayer);
                    if (killerGroup != null) {
                        int earlyKills = earlyKillsCount.getOrDefault(killerPlayer, 0);
                        int totalPoints = (killPoints * kills) + (earlyBonus * earlyKills);

                        String reason = "Kills (" + kills + "x)";
                        if (earlyKills > 0) {
                            reason += " + Early Game Bonus (" + earlyKills + "x)";
                        }
                        reason += " - " + eventName;

                        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                                String.format("clanleague addevent %s %d %s", killerGroup.getName(), totalPoints,
                                        reason));

                        if (gameLogger != null) {
                            int dmKills = kills - earlyKills;
                            String logMsg = String.format("%d Eliminações [%d na Fase Inicial (+%d pts)", kills,
                                    earlyKills,
                                    earlyKills * earlyBonus);
                            if (dmKills > 0) {
                                logMsg += String.format(" e %d no DM", dmKills);
                            }
                            logMsg += "]";
                            gameLogger.logLine("📊 Pontuação: " + killerPlayer.getName() + " (Clã "
                                    + killerGroup.getName() + ") gerou " + totalPoints + " pts -> " + logMsg);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setWinner(@NotNull Warrior warrior) {
        if (!isParticipant(warrior)) {
            return;
        }
        determinePodiumFromEliminations();
        killer = findKiller();
        if (getConfig().isGroupMode()) {
            winnerGroup = getGroup(warrior);
            if (winnerGroup != null) {
                winners = getParticipants().stream().filter(p -> winnerGroup.isMember(p.getUniqueId()))
                        .collect(Collectors.toList());
            } else {
                winners.add(warrior);
            }
        } else {
            winners.add(warrior);
        }
        finish(false);
    }

    @Override
    protected @NotNull String getGameInfoMessage() {
        String groupsText = "";
        GroupManager groupManager = plugin.getGroupManager();
        if (groupManager != null && getConfig().isGroupMode()) {
            groupsText = groupManager.buildStringFrom(getGroupParticipants().keySet());
        }
        return MessageFormat.format(getLang("game_info"),
                getParticipants().size(), getGroupParticipants().size(), groupsText, getEventDurationMinutes());
    }

    @NotNull
    private String getWinnerName(@Nullable Group group, @Nullable List<Warrior> warriors) {
        String name = getLang("no_winner_tournament");
        if (getConfig().isGroupMode()) {
            if (group != null) {
                name = group.getName();
            }
        } else if (warriors != null && !warriors.isEmpty()) {
            name = warriors.get(0).getName();
        }
        return name;
    }

    private int getWinnerGroupTotalKills() {
        if (winnerGroup == null) {
            return 0;
        }
        return killsCount.entrySet().stream()
                .filter(entry -> winnerGroup.isMember(entry.getKey().getUniqueId()))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    private int getWinnerGroupTotalPlayers() {
        if (winnerGroup == null) {
            return 0;
        }
        // Count participants who are members of the winnerGroup
        return (int) getParticipants().stream().filter(p -> winnerGroup.isMember(p.getUniqueId())).count();
    }

    private long getEventDurationMinutes() {
        if (startTime == 0) {
            return 0;
        }
        return (System.currentTimeMillis() - startTime) / 60000;
    }

    private String getParticipantClans() {
        return getParticipants().stream()
                .map(this::getGroup)
                .filter(g -> g != null)
                .map(Group::getName)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String getParticipantPlayers() {
        return getParticipants().stream()
                .map(warrior -> warrior.getName())
                .filter(g -> g != null)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private int getParticipantClanCount() {
        return (int) getParticipants().stream()
                .map(this::getGroup)
                .filter(g -> g != null)
                .map(Group::getName)
                .distinct()
                .count();
    }

    private int getGroupEarlyKills(@Nullable Group group, @Nullable List<Warrior> warriors) {
        if (group == null || warriors == null) {
            return 0;
        }
        return earlyKillsCount.entrySet().stream()
                .filter(entry -> group.isMember(entry.getKey().getUniqueId()))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    private int getGroupKills(@Nullable Group group, @Nullable List<Warrior> warriors) {
        if (group == null || warriors == null) {
            return 0;
        }
        return killsCount.entrySet().stream()
                .filter(entry -> group.isMember(entry.getKey().getUniqueId()))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    @Override
    public void finish(boolean cancelled) {
        // Clear saved inventories when game ends
        savedInventories.clear();
        savedArmor.clear();
        savedKillsBackup.clear();
        cancelDeathmatchTask();
        clearDeathmatchEffects();
        deathmatchStarted = false;

        if (deathmatchBossBar != null) {
            deathmatchBossBar.removeAll();
            deathmatchBossBar = null;
        }

        super.finish(cancelled);
        earlyKillsCount.clear();
    }

    @Override
    public void finish(boolean cancelled, boolean awardKillPoints) {
        // Clear saved inventories when game ends
        savedInventories.clear();
        savedArmor.clear();
        savedKillsBackup.clear();
        cancelDeathmatchTask();
        clearDeathmatchEffects();
        deathmatchStarted = false;

        if (deathmatchBossBar != null) {
            deathmatchBossBar.removeAll();
            deathmatchBossBar = null;
        }

        super.finish(cancelled, awardKillPoints);
        earlyKillsCount.clear();
    }

    private void scheduleDeathmatch() {
        Integer minutes = getConfig().getDeathmatchAfterMinutes();
        if (minutes == null || minutes <= 0) {
            return;
        }

        long delayTicks = (getConfig().getPreparationTime() + (minutes * 60L)) * 20L;
        deathmatchTask = Bukkit.getScheduler().runTaskLater(plugin, this::startDeathmatch, delayTicks);
        addTask(deathmatchTask);
    }

    private void startDeathmatch() {
        if (cancelled || deathmatchStarted || !battle) {
            return;
        }
        deathmatchStarted = true;

        List<Warrior> targets = new ArrayList<>(getParticipants());
        if (targets.isEmpty()) {
            return;
        }

        applyDeathmatchEffects(targets);

        String title = getConfig().getDeathmatchBossbarTitle();
        if (title != null && !title.isEmpty()) {
            deathmatchBossBar = Bukkit.createBossBar(
                    ChatColor.translateAlternateColorCodes('&', title),
                    BarColor.RED,
                    BarStyle.SOLID);
            for (Warrior warrior : targets) {
                Player p = warrior.toOnlinePlayer();
                if (p != null) {
                    deathmatchBossBar.addPlayer(p);
                }
            }
        }
        broadcastKey("deathmatch_started", title != null ? title : "&c&lDEATHMATCH INICIOU!");
    }

    private void applyDeathmatchEffects(@NotNull Collection<Warrior> warriors) {
        for (Warrior warrior : warriors) {
            Player player = warrior.toOnlinePlayer();
            if (player == null) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, true));
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, true));
        }
    }

    private void clearDeathmatchEffects() {
        Collection<Warrior> warriors = new ArrayList<>(getParticipants());
        warriors.addAll(getCasualties());
        for (Warrior warrior : warriors) {
            Player player = warrior.toOnlinePlayer();
            if (player == null) {
                continue;
            }
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.STRENGTH);
        }
    }

    private void cancelDeathmatchTask() {
        if (deathmatchTask != null) {
            deathmatchTask.cancel();
            deathmatchTask = null;
        }
    }

    private @Nullable Location getReaddDestination() {
        if (deathmatchStarted) {
            Location entrance = getRandomDeathmatchEntrance();
            if (entrance != null) {
                return entrance;
            }
        }

        Location center = getConfig().getBorderCenter();
        if (center != null) {
            return center;
        }

        Map<Integer, Location> entrances = getConfig().getArenaEntrances();
        if (!entrances.isEmpty()) {
            return entrances.values().iterator().next();
        }
        return null;
    }

    private @Nullable Location getRandomDeathmatchEntrance() {
        Map<Integer, Location> entrances = getConfig().getDeathmatchEntrances();
        if (entrances.isEmpty()) {
            return null;
        }
        List<Location> locations = new ArrayList<>(entrances.values());
        return locations.get(ThreadLocalRandom.current().nextInt(locations.size()));
    }
}

package me.roinujnosde.titansbattle.games;

import me.roinujnosde.titansbattle.TitansBattle;
import me.roinujnosde.titansbattle.events.GroupWinEvent;
import me.roinujnosde.titansbattle.events.PlayerWinEvent;
import me.roinujnosde.titansbattle.managers.GroupManager;
import me.roinujnosde.titansbattle.types.*;
import me.roinujnosde.titansbattle.utils.Helper;
import me.roinujnosde.titansbattle.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.roinujnosde.titansbattle.BaseGameConfiguration.Prize.FIRST;
import static me.roinujnosde.titansbattle.BaseGameConfiguration.Prize.KILLER;
import static me.roinujnosde.titansbattle.utils.SoundUtils.Type.VICTORY;

public class FreeForAllGame extends Game {

    private @Nullable Group winnerGroup;
    private @Nullable Warrior killer;
    private @NotNull List<Warrior> winners = new ArrayList<>();
    private long startTime;

    public FreeForAllGame(TitansBattle plugin, GameConfiguration config) {
        super(plugin, config);
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
        if (getConfig().isGroupMode()) {
            if (getGroupParticipants().size() == 1) {
                killer = findKiller();
                getGroupParticipants().keySet().stream().findAny().ifPresent(g -> {
                    winnerGroup = g;
                    getParticipants().stream().filter(p -> g.isMember(p.getUniqueId())).forEach(winners::add);
                });
                finish(false);
            }
        } else if (participants.size() == 1) {
            killer = findKiller();
            winners = getParticipants();
            finish(false);
        }
    }

    @Override
    protected void onLobbyEnd() {
        super.onLobbyEnd();
        this.startTime = System.currentTimeMillis();
        String clans = getParticipantClans();
        int clanCount = getParticipantClanCount();
        int playerCount = getParticipants().size();
        broadcastKey("game_started", getConfig().getPreparationTime(), clans, clanCount, playerCount);
        teleportToArena(getParticipants());
        startPreparation();
    }

    @Override
    protected void processWinners() {
        String gameName = getConfig().getName();
        Winners today = databaseManager.getTodaysWinners();
        if (getConfig().isUseKits()) {
            winners.forEach(Kit::clearInventory);
        }
        if (winnerGroup != null) {
            Bukkit.getPluginManager().callEvent(new GroupWinEvent(winnerGroup));
            winnerGroup.getData().increaseVictories(gameName);
            today.setWinnerGroup(gameName, winnerGroup.getName());
            getCasualties().stream().filter(p -> winnerGroup.isMember(p.getUniqueId())).forEach(winners::add);
        }
        SoundUtils.playSound(VICTORY, plugin.getConfig(), winners);
        PlayerWinEvent event = new PlayerWinEvent(this, winners);
        Bukkit.getPluginManager().callEvent(event);
        if (killer != null) {
            plugin.getGameManager().setKiller(getConfig(), killer, null);
            SoundUtils.playSound(VICTORY, plugin.getConfig(), killer.toOnlinePlayer());
            discordAnnounce("discord_who_won_killer", killer.getName(), killsCount.get(killer));
            givePrizes(KILLER, null, Collections.singletonList(killer));
        }
        today.setWinners(gameName, Helper.warriorListToUuidList(winners));
        String winnerName = getConfig().isGroupMode() ? winnerGroup.getName() : winners.get(0).getName();
        int totalKills = getWinnerGroupTotalKills();
        int totalPlayers = getWinnerGroupTotalPlayers();
        long duration = getEventDurationMinutes();
        broadcastKey("who_won", winnerName, totalKills, totalPlayers, duration);
        discordAnnounce("discord_who_won", winnerName);
        winners.forEach(w -> w.increaseVictories(gameName));
        givePrizes(FIRST, winnerGroup, winners);
    }

    @Override
    public void setWinner(@NotNull Warrior warrior) {
        if (!isParticipant(warrior)) {
            return;
        }
        killer = findKiller();
        if (getConfig().isGroupMode()) {
            winnerGroup = getGroup(warrior);
            //noinspection ConstantConditions
            winners = getParticipants().stream().filter(p -> winnerGroup.isMember(p.getUniqueId())).collect(Collectors.toList());
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

    private int getParticipantClanCount() {
        return (int) getParticipants().stream()
                .map(this::getGroup)
                .filter(g -> g != null)
                .map(Group::getName)
                .distinct()
                .count();
    }
}

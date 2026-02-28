package me.roinujnosde.titansbattle;

import me.roinujnosde.titansbattle.BaseGameConfiguration.Prize;
import me.roinujnosde.titansbattle.events.*;
import me.roinujnosde.titansbattle.exceptions.CommandNotSupportedException;
import me.roinujnosde.titansbattle.hooks.papi.PlaceholderHook;
import me.roinujnosde.titansbattle.managers.CommandManager;
import me.roinujnosde.titansbattle.managers.GameManager;
import me.roinujnosde.titansbattle.managers.GroupManager;
import me.roinujnosde.titansbattle.types.GameConfiguration;
import me.roinujnosde.titansbattle.types.Group;
import me.roinujnosde.titansbattle.types.Kit;
import me.roinujnosde.titansbattle.types.Warrior;
import me.roinujnosde.titansbattle.utils.MessageUtils;
import me.roinujnosde.titansbattle.utils.SoundUtils;
import me.roinujnosde.titansbattle.utils.GameLogger;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.roinujnosde.titansbattle.utils.SoundUtils.Type.*;
import static org.bukkit.ChatColor.*;

public abstract class BaseGame {

    protected final TitansBattle plugin;
    protected final GroupManager groupManager;
    protected final GameManager gameManager;

    protected BaseGameConfiguration config;
    protected boolean lobby;
    protected boolean battle;
    protected boolean cancelled = false;
    protected final List<Warrior> participants = new ArrayList<>();
    protected final Map<Warrior, Group> groups = new HashMap<>();
    protected final HashMap<Warrior, Integer> killsCount = new HashMap<>();
    protected final Set<Warrior> casualties = new HashSet<>();
    protected final Set<Warrior> casualtiesWatching = new HashSet<>();
    protected final Map<Warrior, Long> lastCombatTime = new HashMap<>();
    protected final Set<Warrior> loggedCampers = new HashSet<>();
    protected final HashMap<Warrior, Double> damageDealt = new HashMap<>();
    protected final Set<Location> placedBlocks = new HashSet<>();

    private final List<BukkitTask> tasks = new ArrayList<>();
    private LobbyAnnouncementTask lobbyTask;

    protected GameLogger gameLogger;

    private BukkitTask killTheKillerTask;
    private BukkitTask killTheKillerTimerTask;
    private Warrior killTheKillerTarget;
    private int killTheKillerTimeLeft;
    private org.bukkit.boss.BossBar killTheKillerBossBar;

    public BaseGame(TitansBattle plugin, BaseGameConfiguration config) {
        this.plugin = plugin;
        this.groupManager = plugin.getGroupManager();
        this.gameManager = plugin.getGameManager();
        this.config = config;
        if (getConfig().isGroupMode() && groupManager == null) {
            throw new IllegalStateException("groupManager cannot be null in a group mode game");
        }
    }

    public void start() {
        if (getConfig().isGroupMode() && plugin.getGroupManager() == null) {
            throw new IllegalStateException("You cannot start a group based game without a supported Groups plugin!");
        }
        if (!getConfig().locationsSet()) {
            throw new IllegalStateException("You didn't set all locations!");
        }
        this.cancelled = false;
        LobbyStartEvent event = new LobbyStartEvent(this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        lobby = true;
        Integer interval = getConfig().getAnnouncementStartingInterval();
        lobbyTask = new LobbyAnnouncementTask(getConfig().getAnnouncementStartingTimes(), interval);

        gameLogger = new GameLogger(getConfig().getName(), plugin);
        gameLogger.logLine("▶️ Lobby iniciado, aguardando jogadores...");

        // Control holograms for npcs
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh on glad_iniciando");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh on iniciangoagora");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh off eventoagora");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh off eventonenhum");

        // Execute on_start commands if configured
        List<String> commandsOnStart = getConfig().getCommandsOnStart();
        if (commandsOnStart != null && !commandsOnStart.isEmpty()) {
            for (String command : commandsOnStart) {
                CommandManager.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }

        addTask(lobbyTask.runTaskTimer(plugin, 0, interval * 20));

        if (getConfig().isWorldBorder()) {
            WorldBorder worldBorder = getConfig().getBorderCenter().getWorld().getWorldBorder();
            Location center = getConfig().getBorderCenter();
            worldBorder.setCenter(center.getBlockX() + 0.5, center.getBlockZ() + 0.5);

            int initialSize = getConfig().getBorderInitialSize();
            if (initialSize % 2 == 0)
                initialSize++;

            worldBorder.setSize(initialSize);
            worldBorder.setDamageAmount(getConfig().getBorderDamage());
            worldBorder.setDamageBuffer(0);
        }
    }

    public void finish(boolean cancelled) {
        finish(cancelled, false);
    }

    public void finish(boolean cancelled, boolean awardKillPoints) {
        lastCombatTime.clear();
        loggedCampers.clear();
        for (Location loc : placedBlocks) {
            loc.getBlock().setType(org.bukkit.Material.AIR);
        }
        placedBlocks.clear();
        // Control holograms for npcs
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh off glad_iniciando");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh off iniciangoagora");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh on eventonenhum");

        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh off glad_camarote");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "npc sel 438");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "npc despawn");

        teleportAll(getConfig().getExit());
        endKillTheKiller(false);
        killTasks();
        runCommandsAfterBattle(getParticipants());
        if (getConfig().isUseKits()) {
            getPlayerParticipantsStream().forEach(Kit::clearInventory);
        }
        if (getConfig().isWorldBorder()) {
            getConfig().getBorderCenter().getWorld().getWorldBorder().reset();
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getDatabaseManager().saveAll());
        if (!cancelled) {
            processWinners(true);
        } else if (awardKillPoints) {
            processWinners(false);
        }

        if (gameLogger != null) {
            gameLogger.close();
        }
    }

    public abstract void setWinner(@NotNull Warrior warrior) throws CommandNotSupportedException;

    public void cancel(@NotNull CommandSender sender) {
        cancel(sender, false);
    }

    public void cancel(@NotNull CommandSender sender, boolean awardKillPoints) {
        this.cancelled = true;
        broadcastKey("cancelled", sender.getName());
        finish(true, awardKillPoints);
    }

    public void onJoin(@NotNull Warrior warrior) {
        if (!canJoin(warrior)) {
            plugin.debug(String.format("Warrior %s can't join", warrior.getName()));
            return;
        }
        Player player = warrior.toOnlinePlayer();
        if (player == null) {
            plugin.debug(String.format("onJoin() -> player %s %s == null", warrior.getName(), warrior.getUniqueId()));
            return;
        }
        if (!teleport(warrior, getConfig().getLobby())) {
            plugin.debug(String.format("Player %s is dead: %s", player, player.isDead()), false);
            player.sendMessage(getLang("teleport.error"));
            return;
        }
        SoundUtils.playSound(JOIN_GAME, plugin.getConfig(), player);
        participants.add(warrior);
        groups.put(warrior, warrior.getGroup());
        lastCombatTime.put(warrior, System.currentTimeMillis());
        setKit(warrior);
        healAndClearEffects(warrior);
        broadcastKey("player_joined", warrior.getName());
        player.sendMessage(getLang("objective"));

        if (gameLogger != null) {
            gameLogger.logLine("[+] " + warrior.getName() + " entrou no evento.");
        }

        // Set player in survival mode and clear inventory
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);

        if (participants.size() == getConfig().getMaximumPlayers() && lobbyTask != null) {
            lobbyTask.processEnd();
        }
    }

    public void onDeath(@NotNull Warrior victim, @Nullable Warrior killer) {
        if (!isParticipant(victim)) {
            return;
        }
        if (!isLobby()) {
            ParticipantDeathEvent event = new ParticipantDeathEvent(victim, killer);
            Bukkit.getPluginManager().callEvent(event);
            String gameName = getConfig().getName();
            casualties.add(victim);
            if (getConfig().isGroupMode()) {
                victim.sendMessage(getLang("watch_to_the_end"));
            }
            if (killer != null) {
                killer.increaseKills(gameName);
                increaseKills(killer);
                // Notify killer that points will be awarded at game end
                if (getConfig() instanceof GameConfiguration) {
                    GameConfiguration config = (GameConfiguration) getConfig();
                    int points = config.getLeaguePointsKill();
                    if (points > 0) {
                        Group killerGroup = getGroup(killer);
                        if (killerGroup != null) {
                            killer.sendMessage(
                                    "&aSeu clan receberá &f" + points + " pontos &apela sua kill ao final do evento!");
                        }
                    }
                }
            }
            victim.increaseDeaths(gameName);
            playDeathSound(victim);

            if (gameLogger != null) {
                String killerStr = killer != null
                        ? killer.getName() + "(" + killsCount.getOrDefault(killer, 0) + " kills)"
                        : "Si mesmo / Natureza";
                String victimStr = victim.getName() + "(" + killsCount.getOrDefault(victim, 0) + " kills)";
                gameLogger.logLine("⚔️ " + killerStr + " eliminou " + victimStr);
            }
            processKillTheKillerDeath(victim, killer);
        }
        broadcastDeathMessage(victim, killer);
        processPlayerExit(victim);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLobby() {
        return lobby;
    }

    public void addDamageDealt(Warrior warrior, double damage) {
        damageDealt.put(warrior, damageDealt.getOrDefault(warrior, 0.0) + damage);
    }

    public double getDamageDealt(Warrior warrior) {
        return damageDealt.getOrDefault(warrior, 0.0);
    }

    public Map<Warrior, Double> getDamageDealtMap() {
        return damageDealt;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public abstract boolean isInBattle(@NotNull Warrior warrior);

    public @NotNull BaseGameConfiguration getConfig() {
        return config;
    }

    public boolean isParticipant(@NotNull Warrior warrior) {
        return participants.contains(warrior);
    }

    public void onDisconnect(@NotNull Warrior warrior) {
        if (!isParticipant(warrior)) {
            return;
        }
        if (getConfig().isUseKits()) {
            plugin.getConfigManager().getClearInventory().add(warrior.getUniqueId());
        }
        if (!isLobby() && getCurrentFighters().contains(warrior)) {
            // processInventoryOnExit(warrior);
            // onDeath(warrior, getLastAttacker(warrior));
            Player player = warrior.toOnlinePlayer();
            if (player != null) {
                player.setHealth(0);
            }
            return;
        }
        casualties.add(warrior);
        casualtiesWatching.add(warrior); // adding to this Collection, so they are not teleported on respawn
        plugin.getConfigManager().getRespawn().add(warrior.getUniqueId());
        plugin.getConfigManager().save();
        processPlayerExit(warrior);
    }

    public void onLeave(@NotNull Warrior warrior) {
        if (!isParticipant(warrior)) {
            return;
        }
        if (getConfig().isUseKits()) {
            Kit.clearInventory(warrior.toOnlinePlayer());
        }
        Player player = Objects.requireNonNull(warrior.toOnlinePlayer());
        if (!isLobby() && getCurrentFighters().contains(warrior)) {
            // processInventoryOnExit(warrior);
            // onDeath(warrior, getLastAttacker(warrior));
            player.setHealth(0);
            return;
        }
        player.sendMessage(getLang("you-have-left"));
        SoundUtils.playSound(LEAVE_GAME, plugin.getConfig(), player);
        processPlayerExit(warrior);

        if (gameLogger != null) {
            gameLogger.logLine("[-] " + warrior.getName() + " saiu do evento.");
        }
    }

    protected @Nullable Warrior getLastAttacker(@NotNull Warrior victim) {
        Player player = victim.toOnlinePlayer();
        EntityDamageEvent event = player != null ? player.getLastDamageCause() : null;
        if (event instanceof EntityDamageByEntityEvent) {
            Entity attacker = ((EntityDamageByEntityEvent) event).getDamager();
            if (attacker instanceof Player) {
                return plugin.getDatabaseManager().getWarrior((Player) attacker);
            }
            if (attacker instanceof Projectile) {
                return plugin.getDatabaseManager().getWarrior((Player) ((Projectile) attacker).getShooter());
            }
        }
        return null;
    }

    protected void processInventoryOnExit(@NotNull Warrior warrior) {
        Player player = warrior.toOnlinePlayer();
        if (player == null) {
            plugin.debug("processInventoryOnExit() -> null player");
            return;
        }
        World world = player.getWorld();
        if (shouldKeepInventoryOnDeath(warrior) || Boolean.parseBoolean(world.getGameRuleValue("keepInventory"))) {
            return;
        }
        if (shouldClearDropsOnDeath(warrior)) {
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null)
                continue;
            world.dropItemNaturally(player.getLocation(), item.clone());
        }
        Kit.clearInventory(player);
    }

    public void onRespawn(@NotNull Warrior warrior) {
        if (casualties.contains(warrior) && !casualtiesWatching.contains(warrior)) {
            teleport(warrior, getConfig().getWatchroom());
            casualtiesWatching.add(warrior);
        }
    }

    public abstract boolean shouldClearDropsOnDeath(@NotNull Warrior warrior);

    public abstract boolean shouldKeepInventoryOnDeath(@NotNull Warrior warrior);

    public @NotNull List<Warrior> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    @NotNull
    protected Stream<Player> getPlayerParticipantsStream() {
        return getParticipants().stream().map(Warrior::toOnlinePlayer).filter(Objects::nonNull);
    }

    public Map<Group, Integer> getGroupParticipants() {
        if (!getConfig().isGroupMode()) {
            return Collections.emptyMap();
        }
        Map<Group, Integer> groups = new HashMap<>();
        for (Warrior w : participants) {
            groups.compute(getGroup(w), (g, i) -> i == null ? 1 : i + 1);
        }
        return groups;
    }

    protected @Nullable Group getGroup(@NotNull Warrior warrior) {
        return groups.get(warrior);
    }

    public Collection<Warrior> getCasualties() {
        return casualties;
    }

    public abstract @NotNull Collection<Warrior> getCurrentFighters();

    public HashMap<Warrior, Integer> getKillsCount() {
        return killsCount;
    }

    public void broadcastKey(@NotNull String key, Object... args) {
        broadcast(getLang(key), args);
    }

    public void discordAnnounce(@NotNull String key, Object... args) {
        plugin.sendDiscordMessage(getLang(key, args));
    }

    public void broadcast(@Nullable String message, Object... args) {
        if (message == null || message.isEmpty()) {
            return;
        }
        message = MessageFormat.format(message, args);
        if (message.startsWith("!!broadcast")) {
            // Remove !!broadcast prefix
            String cleanMessage = message.replace("!!broadcast", "").trim();

            // Convert \n to ;; for pbc command
            String pbcMessage = cleanMessage.replace("\n", ";;");

            // Sync message to all gamemodes, except lobby and events
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "pbc msg all-gamemodes " + pbcMessage);

            Bukkit.broadcastMessage(cleanMessage);
        } else {
            for (Warrior warrior : getParticipants()) {
                warrior.sendMessage(message);
            }
        }
    }

    protected void healAndClearEffects(@NotNull Collection<Warrior> warriors) {
        warriors.forEach(this::healAndClearEffects);
    }

    protected void healAndClearEffects(@NotNull Warrior warrior) {
        Player player = warrior.toOnlinePlayer();
        if (player == null)
            return;

        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    @Override
    public int hashCode() {
        return getConfig().getName().hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BaseGame)) {
            return false;
        }
        BaseGame otherGame = (BaseGame) other;
        return otherGame.getConfig().getName().equals(getConfig().getName());
    }

    public @NotNull String getLang(@NotNull String key, Object... args) {
        return plugin.getLang(key, this, args);
    }

    protected String getEventNameForLeague() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        String date = sdf.format(new Date());
        return config.getName() + " - " + date;
    }

    protected boolean teleport(@Nullable Warrior warrior, @NotNull Location destination) {
        plugin.debug(String.format("teleport() -> destination %s", destination));
        Player player = warrior != null ? warrior.toOnlinePlayer() : null;
        if (player == null) {
            plugin.debug(String.format("teleport() -> warrior %s", warrior));
            return false;
        }
        SoundUtils.playSound(TELEPORT, plugin.getConfig(), player);
        return player.teleport(destination);
    }

    protected void addTask(@NotNull BukkitTask task) {
        tasks.add(task);
    }

    protected void killTasks() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    protected void increaseKills(Warrior warrior) {
        killsCount.compute(warrior, (p, i) -> i == null ? 1 : i + 1);
    }

    protected abstract void onLobbyEnd();

    protected abstract void processWinners(boolean awardAllPrizes);

    protected void givePrizes(Prize prize, @Nullable Group group, @Nullable List<Warrior> warriors) {
        List<Player> leaders = new ArrayList<>();
        List<Player> members = new ArrayList<>();
        if (warriors == null) {
            return;
        }
        List<Player> players = warriors.stream().map(Warrior::toOnlinePlayer).filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (group != null) {
            for (Player p : players) {
                if (group.isLeaderOrOfficer(p.getUniqueId())) {
                    leaders.add(p);
                } else {
                    members.add(p);
                }
            }
        } else {
            members = players;
        }
        getConfig().getPrizes(prize).give(plugin, leaders, members);
    }

    protected boolean canStartBattle() {
        GameStartEvent event = new GameStartEvent(this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            broadcastKey("cancelled", "CONSOLE");
            return false;
        }
        if (getParticipants().size() < getConfig().getMinimumPlayers()) {
            broadcastKey("not_enough_participants");
            return false;
        }
        if (getConfig().isGroupMode()) {
            if (getGroupParticipants().size() < getConfig().getMinimumGroups()) {
                broadcastKey("not_enough_participants");
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({ "BooleanMethodIsAlwaysInverted" })
    protected boolean canJoin(@NotNull Warrior warrior) {
        Player player = warrior.toOnlinePlayer();
        if (player == null) {
            plugin.getLogger().log(Level.WARNING, "Joining player {0} ({1}) is null",
                    new Object[] { warrior.getName(), warrior.getUniqueId() });
            return false;
        }

        PlayerJoinGameEvent event = new PlayerJoinGameEvent(warrior, player, this);
        Bukkit.getPluginManager().callEvent(event);
        plugin.debug("cancel: " + event.isCancelled());

        return !event.isCancelled();
    }

    protected void processPlayerExit(@NotNull Warrior warrior) {
        if (!isParticipant(warrior)) {
            return;
        }
        Player player = warrior.toOnlinePlayer();
        if (player != null) {
            teleport(warrior, getConfig().getExit());
            PlayerExitGameEvent event = new PlayerExitGameEvent(player, this);
            Bukkit.getPluginManager().callEvent(event);
        }
        participants.remove(warrior);
        Group group = getGroup(warrior);
        if (isLobby() || cancelled) {
            groups.remove(warrior);
            killsCount.remove(warrior);
            casualties.remove(warrior);
            casualtiesWatching.remove(warrior);
            damageDealt.remove(warrior);
        }
        lastCombatTime.remove(warrior);
        loggedCampers.remove(warrior);
        if (!isLobby() && !cancelled) {
            runCommandsAfterBattle(Collections.singletonList(warrior));
            processRemainingPlayers(warrior);
            // last participant
            if (getConfig().isGroupMode() && group != null && !getGroupParticipants().containsKey(group)) {
                // Only broadcast if more than one group remains after this group is eliminated
                if (getGroupParticipants().size() > 1) {
                    broadcastKey("group_defeated", group.getName());
                    Bukkit.getPluginManager().callEvent(new GroupDefeatedEvent(group, warrior.toOnlinePlayer()));
                }
                group.getData().increaseDefeats(getConfig().getName());
            }
            sendRemainingOpponentsCount();
        }
    }

    protected abstract void processRemainingPlayers(@NotNull Warrior warrior);

    protected void setKit(@NotNull Warrior warrior) {
        Player player = warrior.toOnlinePlayer();
        Kit kit = getConfig().getKit();
        if (getConfig().isUseKits() && kit != null && player != null) {
            Kit.clearInventory(player);
            kit.set(player);
        }
    }

    protected void playDeathSound(@NotNull Warrior victim) {
        Stream<Player> players = getPlayerParticipantsStream();
        if (!getConfig().isGroupMode()) {
            players.forEach(p -> SoundUtils.playSound(ENEMY_DEATH, plugin.getConfig(), p));
            return;
        }
        GroupManager groupManager = plugin.getGroupManager();
        if (groupManager == null) {
            return;
        }
        Group victimGroup = groupManager.getGroup(victim.getUniqueId());
        players.forEach(participant -> {
            Group group = groupManager.getGroup(participant.getUniqueId());
            if (group == null) {
                return;
            }
            if (group.equals(victimGroup)) {
                SoundUtils.playSound(ALLY_DEATH, plugin.getConfig(), participant);
            } else {
                SoundUtils.playSound(ENEMY_DEATH, plugin.getConfig(), participant);
            }
        });
    }

    protected void sendRemainingOpponentsCount() {
        getPlayerParticipantsStream().forEach(p -> {
            int remainingPlayers = getRemainingOpponents();
            int remainingGroups = getRemainingOpponentGroups(p);
            if (Math.min(remainingPlayers, remainingGroups) <= 0) {
                return;
            }
            MessageUtils.sendActionBar(p,
                    MessageFormat.format(getLang("action-bar-remaining-opponents"), remainingPlayers, remainingGroups));
        });
    }

    protected int getRemainingOpponentGroups(@NotNull Player player) {
        int opponents = 0;
        Warrior warrior = plugin.getDatabaseManager().getWarrior(player);
        for (Map.Entry<Group, Integer> entry : getGroupParticipants().entrySet()) {
            Group group = entry.getKey();
            if (group.equals(getGroup(warrior))) {
                continue;
            }
            opponents += entry.getValue();
        }
        return opponents;
    }

    protected int getRemainingOpponents() {
        return getParticipants().size() - 1;
    }

    protected void runCommandsBeforeBattle(@NotNull Collection<Warrior> warriors) {
        runCommands(warriors, getConfig().getCommandsBeforeBattle());
    }

    protected void runCommandsAfterBattle(@NotNull Collection<Warrior> warriors) {
        runCommands(warriors, getConfig().getCommandsAfterBattle());
    }

    protected void runCommands(@NotNull Collection<Warrior> warriors, @Nullable Collection<String> commands) {
        if (commands == null)
            return;
        PlaceholderHook hook = plugin.getPlaceholderHook();

        for (String command : commands) {
            for (Warrior warrior : warriors) {
                Player player = warrior.toOnlinePlayer();
                if (player == null) {
                    continue;
                }
                if (!command.contains("%player%")) { // Runs the command once when %player% is not used
                    CommandManager.dispatchCommand(Bukkit.getConsoleSender(),
                            hook.parse((OfflinePlayer) null, command));
                    break;
                }
                CommandManager.dispatchCommand(Bukkit.getConsoleSender(), hook.parse(warrior, command,
                        "%player%", warrior.getName()));
            }
        }
    }

    protected void teleport(@NotNull Collection<Warrior> warriors, @NotNull Location destination) {
        warriors.forEach(warrior -> teleport(warrior, destination));
    }

    protected void teleportAll(Location destination) {
        getParticipants().forEach(player -> teleport(player, destination));
    }

    protected void teleportToArena(List<Warrior> warriors) {
        if (!getConfig().isTeleportToArenaEntrances()) {
            return;
        }

        List<Location> arenaEntrances = new ArrayList<>(getConfig().getArenaEntrances().values());
        if (arenaEntrances.isEmpty()) {
            return;
        }
        if (arenaEntrances.size() == 1) {
            teleport(warriors, arenaEntrances.get(0));
            return;
        }

        Random random = new Random();

        if (config.isGroupMode()) {
            // Map each group to its warriors from the provided list
            Map<Group, List<Warrior>> groupToWarriors = new LinkedHashMap<>();
            for (Warrior warrior : warriors) {
                Group group = getGroup(warrior);
                if (group == null)
                    continue;
                groupToWarriors.computeIfAbsent(group, k -> new ArrayList<>()).add(warrior);
            }

            for (List<Warrior> groupWarriors : groupToWarriors.values()) {
                if (!groupWarriors.isEmpty()) {
                    Location entrance = arenaEntrances.get(random.nextInt(arenaEntrances.size()));
                    teleport(groupWarriors, entrance);
                }
            }
        } else {
            for (Warrior warrior : warriors) {
                Location entrance = arenaEntrances.get(random.nextInt(arenaEntrances.size()));
                teleport(warrior, entrance);
            }
        }
    }

    @SuppressWarnings("deprecation")
    protected void broadcastDeathMessage(@NotNull Warrior victim, @Nullable Warrior killer) {
        if (killer == null) {
            broadcastKey("died_by_himself", victim.getName());
        } else {
            ItemStack itemInHand = Objects.requireNonNull(killer.toOnlinePlayer()).getItemInHand();
            String weaponName = getLang("fist");
            if (itemInHand != null && itemInHand.getType() != Material.AIR) {
                ItemMeta itemMeta = itemInHand.getItemMeta();
                if (itemMeta != null && itemMeta.hasDisplayName()) {
                    weaponName = itemMeta.getDisplayName();
                } else {
                    weaponName = itemInHand.getType().name().replace("_", " ").toLowerCase();
                }
            }
            broadcastKey("killed_by", victim.getName(), killsCount.getOrDefault(victim, 0), killer.getName(),
                    killsCount.get(killer), weaponName);
        }
    }

    protected void startPreparation() {
        // Control holograms for npcs
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh off glad_iniciando");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh off iniciangoagora");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh on eventonenhum");

        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "dh on glad_camarote");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "npc sel 438");
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "npc spawn");

        addTask(new PreparationTimeTask().runTaskLater(plugin, getConfig().getPreparationTime() * 20));
        addTask(new CountdownTitleTask(getCurrentFighters(), getConfig().getPreparationTime()).runTaskTimer(plugin, 0L,
                20L));
    }

    public class LobbyAnnouncementTask extends BukkitRunnable {
        private int times;
        private final long interval;

        public LobbyAnnouncementTask(int times, long interval) {
            this.times = times + 1;
            this.interval = interval;
        }

        @Override
        public void run() {
            long seconds = times * interval;
            if (times > 0) {
                int lp1 = 0, lp2 = 0, lp3 = 0;
                if (getConfig() instanceof me.roinujnosde.titansbattle.types.GameConfiguration) {
                    me.roinujnosde.titansbattle.types.GameConfiguration gc = (me.roinujnosde.titansbattle.types.GameConfiguration) getConfig();
                    lp1 = gc.getLeaguePointsFirst();
                    lp2 = gc.getLeaguePointsSecond();
                    lp3 = gc.getLeaguePointsThird();
                }
                broadcastKey("starting_game", seconds, getConfig().getMinimumGroups(), getConfig().getMinimumPlayers(),
                        getGroupParticipants().size(), getParticipants().size(),
                        lp1, lp2, lp3);
                times--;
            } else {
                processEnd();
            }
        }

        public void processEnd() {
            if (canStartBattle()) {
                lobby = false;
                onLobbyEnd();
                addTask(new GameExpirationTask().runTaskLater(plugin, getConfig().getExpirationTime() * 20));
            } else {
                finish(true);
            }
            this.cancel();
            lobbyTask = null;
        }
    }

    public class BorderTask extends BukkitRunnable {

        private final WorldBorder worldBorder;
        private int currentSize;

        public BorderTask(WorldBorder worldBorder) {
            this.worldBorder = worldBorder;
            this.currentSize = (int) worldBorder.getSize();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void run() {
            int shrinkSize = getConfig().getBorderShrinkSize();
            if (shrinkSize % 2 != 0)
                shrinkSize++;

            int newSize = currentSize - shrinkSize;

            int finalSize = getConfig().getBorderFinalSize();
            if (finalSize % 2 == 0)
                finalSize++;

            if (finalSize > newSize) {
                this.cancel();
                shrinkSize = currentSize - finalSize;
                if (shrinkSize <= 0) {
                    return;
                }
                newSize = finalSize;
            }

            getPlayerParticipantsStream().forEach(player -> {
                player.sendTitle(getLang("border.title"), getLang("border.subtitle"));
                SoundUtils.playSound(BORDER, getConfig().getFileConfiguration(), player);
            });

            worldBorder.setSize(newSize, shrinkSize);
            currentSize = newSize;
        }

    }

    public class PreparationTimeTask extends BukkitRunnable {

        @Override
        public void run() {
            int clancount = getGroupParticipants().size();
            int minclans = getConfig().getMinimumGroups();
            int playerscount = getParticipants().size();
            int minplayers = getConfig().getMinimumPlayers();
            broadcastKey("preparation_over", clancount, minclans, playerscount, minplayers);
            runCommandsBeforeBattle(getCurrentFighters());
            battle = true;

            if (getConfig().isAntiCampEnabled() && getConfig().getAntiCampMinutesInactive() > 0) {
                // Reset map before starting counting.
                for (Warrior warrior : getCurrentFighters()) {
                    lastCombatTime.put(warrior, System.currentTimeMillis());
                }
                addTask(new AntiCampTask().runTaskTimer(plugin, 600L, 600L));
            }

            if (getConfig().isWorldBorder()) {
                long borderInterval = getConfig().getBorderInterval() * 20L;
                WorldBorder worldBorder = getConfig().getBorderCenter().getWorld().getWorldBorder();
                addTask(new BorderTask(worldBorder).runTaskTimer(plugin, borderInterval, borderInterval));
            }

            if (getConfig().isMinigameKillTheKillerEnabled()) {
                long interval = getConfig().getMinigameKillTheKillerInterval() * 20L;
                killTheKillerTask = Bukkit.getScheduler().runTaskTimer(plugin,
                        BaseGame.this::processKillTheKillerChance, interval, interval);
                addTask(killTheKillerTask);
            }
        }
    }

    public class CountdownTitleTask extends BukkitRunnable {

        private final Collection<Warrior> warriors;
        private int timer;

        public CountdownTitleTask(Collection<Warrior> warriors, int timer) {
            this.warriors = warriors;
            if (timer < 0) {
                timer = 0;
            }
            this.timer = timer;
        }

        @SuppressWarnings("deprecation")
        @Override
        public void run() {
            List<Player> players = warriors.stream().map(Warrior::toOnlinePlayer).filter(Objects::nonNull)
                    .collect(Collectors.toList());
            String title;
            if (timer > 0) {
                title = getColor() + "" + timer;
            } else {
                title = RED + getLang("title.fight");
                this.cancel();
                Bukkit.getScheduler().runTaskLater(plugin, () -> players.forEach(Player::resetTitle), 20L);
            }
            players.forEach(player -> player.sendTitle(title, ""));
            timer--;
        }

        private ChatColor getColor() {
            ChatColor color = GREEN;
            if (timer <= 3) {
                color = RED;
            } else if (timer <= 7) {
                color = YELLOW;
            }
            return color;
        }
    }

    public class GameExpirationTask extends BukkitRunnable {

        @Override
        public void run() {
            gameManager.getCurrentGame().ifPresent(game -> {
                game.finish(true);
                broadcastKey("game_expired");
            });
        }
    }

    public class AntiCampTask extends BukkitRunnable {
        @Override
        public void run() {
            if (!battle || cancelled || casualties.size() >= participants.size()) {
                this.cancel();
                return;
            }
            long maxIdleTime = getConfig().getAntiCampMinutesInactive() * 60000L;
            long now = System.currentTimeMillis();
            for (Warrior warrior : getCurrentFighters()) {
                long lastTime = lastCombatTime.getOrDefault(warrior, now);
                if (now - lastTime > maxIdleTime) {
                    Player p = warrior.toOnlinePlayer();
                    if (p != null) {
                        try {
                            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.GLOWING, 200, 0));
                        } catch (Error | Exception ignored) {
                        } // PotionEffectType.GLOWING is 1.9+, fail silently
                        p.sendMessage(
                                "§cVocê está sendo revelado por ficar ausente do combate! Dê dano em alguém para remover o efeito.");
                        if (gameLogger != null && !loggedCampers.contains(warrior)) {
                            gameLogger.logLine("Tracker: " + warrior.getName() + " ficou inativo por "
                                    + getConfig().getAntiCampMinutesInactive() + " min e sofreu revelação.");
                            loggedCampers.add(warrior);
                        }
                    }
                }
            }
        }
    }

    public void updateCombatTime(@NotNull Warrior warrior) {
        if (battle && isParticipant(warrior) && !casualties.contains(warrior)) {
            lastCombatTime.put(warrior, System.currentTimeMillis());
            Player player = warrior.toOnlinePlayer();
            // Don't remove glow if they are the kill the killer target
            if (player != null && player.hasPotionEffect(org.bukkit.potion.PotionEffectType.GLOWING)
                    && !warrior.equals(killTheKillerTarget)) {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.GLOWING);
            }
        }
    }

    private void processKillTheKillerChance() {
        if (!battle || cancelled || killTheKillerTarget != null)
            return;
        if (Math.random() * 100 > getConfig().getMinigameKillTheKillerChance())
            return;

        Warrior target = null;
        int maxKills = -1;
        List<Warrior> eligible = new ArrayList<>(getParticipants());
        eligible.removeAll(getCasualties());
        Collections.shuffle(eligible);

        for (Warrior w : eligible) {
            int kills = getKillsCount().getOrDefault(w, 0);
            if (kills > maxKills) {
                maxKills = kills;
                target = w;
            }
        }

        if (target == null)
            return;
        startKillTheKiller(target);
    }

    private void startKillTheKiller(Warrior target) {
        this.killTheKillerTarget = target;
        this.killTheKillerTimeLeft = getConfig().getMinigameKillTheKillerDuration();

        Player p = target.toOnlinePlayer();
        if (p != null) {
            target.sendMessage("§c§lVocê se tornou o Alvo do minigame! Sobreviva!");
            p.setGlowing(true); // Paper 1.21 supports this
        }

        String bossbarText = getLang("minigame_kill_the_killer_bossbar", target.getName(),
                getConfig().getMinigameKillTheKillerPoints(), formatTime(killTheKillerTimeLeft));
        this.killTheKillerBossBar = Bukkit.createBossBar(ChatColor.translateAlternateColorCodes('&', bossbarText),
                org.bukkit.boss.BarColor.RED, org.bukkit.boss.BarStyle.SOLID);

        getPlayerParticipantsStream().forEach(participant -> killTheKillerBossBar.addPlayer(participant));

        broadcastKey("minigame_kill_the_killer_started", target.getName());

        if (gameLogger != null) {
            gameLogger.logLine("🔴 [MINIGAME] " + target.getName() + " foi selecionado como Killer (alvo) com "
                    + getKillsCount().getOrDefault(target, 0) + " kills!");
        }

        this.killTheKillerTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (killTheKillerTarget == null || cancelled || !battle || casualties.contains(killTheKillerTarget)) {
                endKillTheKiller(false);
                return;
            }
            killTheKillerTimeLeft--;
            if (killTheKillerTimeLeft <= 0) {
                endKillTheKiller(false);
                return;
            }
            killTheKillerBossBar.setTitle(ChatColor.translateAlternateColorCodes('&',
                    getLang("minigame_kill_the_killer_bossbar", killTheKillerTarget.getName(),
                            getConfig().getMinigameKillTheKillerPoints(), formatTime(killTheKillerTimeLeft))));
        }, 20L, 20L);
        addTask(this.killTheKillerTimerTask);
    }

    private void endKillTheKiller(boolean killed) {
        if (killTheKillerTarget != null) {
            Player p = killTheKillerTarget.toOnlinePlayer();
            if (p != null)
                p.setGlowing(false);
            if (!killed && p != null && !casualties.contains(killTheKillerTarget)) {
                killTheKillerTarget.sendMessage("§a§lVocê sobreviveu ao minigame!");
            }
        }
        this.killTheKillerTarget = null;
        if (this.killTheKillerBossBar != null) {
            this.killTheKillerBossBar.removeAll();
            this.killTheKillerBossBar = null;
        }
        if (this.killTheKillerTimerTask != null) {
            this.killTheKillerTimerTask.cancel();
            this.killTheKillerTimerTask = null;
        }
        if (!killed && gameLogger != null) {
            gameLogger.logLine(
                    "⚪ [MINIGAME] O tempo esgotou ou o Killer caiu por outros meios. Minigame finalizado sem recompensas a caçadores.");
        }
    }

    private void processKillTheKillerDeath(Warrior victim, Warrior killer) {
        if (killTheKillerTarget != null && victim.equals(killTheKillerTarget)) {
            if (killer != null) {
                Group killerGroup = getGroup(killer);
                int points = getConfig().getMinigameKillTheKillerPoints();
                if (killerGroup != null && points > 0) {
                    String reason = "Minigame Mate o Killer";
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(),
                            String.format("clanleague addevent %s %d %s", killerGroup.getName(), points, reason));
                    if (gameLogger != null) {
                        gameLogger.logLine("🟢 [MINIGAME] " + killer.getName() + " matou o Killer e rendeu " + points
                                + " pts ao clã " + killerGroup.getName() + "!");
                    }
                    broadcastKey("minigame_kill_the_killer_ended", killer.getName(), victim.getName(), points,
                            killerGroup.getName());
                } else if (points > 0) {
                    if (gameLogger != null) {
                        gameLogger.logLine("🟢 [MINIGAME] " + killer.getName()
                                + " matou o Killer mas não está num clã para receber os pontos.");
                    }
                }
            }
            endKillTheKiller(true);
        }
    }

    private String formatTime(int seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public void addPlacedBlock(Location location) {
        placedBlocks.add(location);
    }

    public void removePlacedBlock(Location location) {
        placedBlocks.remove(location);
    }

    public boolean isPlacedBlock(Location location) {
        return placedBlocks.contains(location);
    }

}

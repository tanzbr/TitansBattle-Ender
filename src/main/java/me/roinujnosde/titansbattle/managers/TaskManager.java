/*
 * The MIT License
 *
 * Copyright 2017 Edson Passos - edsonpassosjr@outlook.com.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.roinujnosde.titansbattle.managers;

import me.roinujnosde.titansbattle.TitansBattle;
import me.roinujnosde.titansbattle.hooks.discord.DiscordAnnounces;
import me.roinujnosde.titansbattle.types.GameConfiguration;
import me.roinujnosde.titansbattle.types.Prizes;
import me.roinujnosde.titansbattle.types.Event;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author RoinujNosde
 */
public class TaskManager {

    private final TitansBattle plugin = TitansBattle.getInstance();

    private Timer schedulerTimer;
    BukkitTask giveItemsTask;

    public void setupScheduler() {
        if (schedulerTimer != null) {
            schedulerTimer.cancel();
        }
        if (!plugin.getConfigManager().isScheduler()) {
            return;
        }
        schedulerTimer = new Timer("TitansBattle Scheduler", true);

        boolean loggedMonthly = false;
        for (Event event : plugin.getConfigManager().getEvents()) {
            // Create Discord announcement task (30 minutes before)
            TimerTask discordTask = createDiscordAnnouncementTask(event);
            
            // Create game start task
            TimerTask gameStartTask = createTimerTask(event);
            
            if (event.getFrequency() == Event.Frequency.MONTHLY) {
                schedulerTimer.schedule(discordTask, event.getDiscordAnnouncementDelay());
                schedulerTimer.schedule(gameStartTask, event.getDelay());
                if (!loggedMonthly) {
                    plugin.getLogger().info("Scheduled a monthly event. This event will be repeated only after a restart.");
                    loggedMonthly = true;
                }
                continue;
            }
            
            schedulerTimer.scheduleAtFixedRate(discordTask, event.getDiscordAnnouncementDelay(), event.getFrequency().getPeriod());
            schedulerTimer.scheduleAtFixedRate(gameStartTask, event.getDelay(), event.getFrequency().getPeriod());
        }
    }

    public void startGiveItemsTask(long interval) {
        interval = interval * 20;
        if (giveItemsTask != null) {
            giveItemsTask.cancel();
        }
        giveItemsTask = new GiveItemsTask().runTaskTimer(plugin, interval, interval);
    }

    private TimerTask createTimerTask(Event event) {
        return new TimerTask() {
            @Override
            public void run() {
                Optional<GameConfiguration> config = plugin.getConfigurationDao()
                        .getConfiguration(event.getGameName(), GameConfiguration.class);
                if (!config.isPresent()) {
                    plugin.getLogger().warning(String.format("Game %s not found!", event.getGameName()));
                    return;
                }
                if (plugin.getGameManager().getCurrentGame().isPresent()) {
                    plugin.getLogger().info("There is a game running. Skipping event.");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getGameManager().start(config.get()));
            }
        };
    }

    private TimerTask createDiscordAnnouncementTask(Event event) {
        return new TimerTask() {
            @Override
            public void run() {
                Optional<GameConfiguration> config = plugin.getConfigurationDao()
                        .getConfiguration(event.getGameName(), GameConfiguration.class);
                if (!config.isPresent()) {
                    plugin.getLogger().warning(String.format("Discord announcement: Game %s not found!", event.getGameName()));
                    return;
                }
                
                // Announce 30 minutes before the game starts
                Bukkit.getScheduler().runTask(plugin, () -> {
                    DiscordAnnounces.announceStart(
                        config.get().getName(), // Game Name
                        30 // 30 minutes before start
                    );
                    plugin.getLogger().info(String.format("Discord announcement sent for %s - starting in 30 minutes", config.get().getName()));
                });
            }
        };
    }

    private class GiveItemsTask extends BukkitRunnable {

        @Override
        public void run() {
            if (!Prizes.getPlayersWithItemsToReceive().isEmpty()) {
                Iterator<Entry<Player, Collection<ItemStack>>> iterator = Prizes.getPlayersWithItemsToReceive()
                        .entrySet().iterator();
                while (iterator.hasNext()) {
                    Entry<Player, Collection<ItemStack>> entry = iterator.next();
                    Player player = entry.getKey();
                    Collection<ItemStack> remainingItems = player.getInventory().addItem(entry.getValue()
                            .toArray(new ItemStack[0])).values();
                    if (remainingItems.isEmpty()) {
                        iterator.remove();
                    } else {
                        entry.setValue(remainingItems);
                        player.sendMessage(MessageFormat.format(plugin.getLang("items_to_receive"),
                                remainingItems.size()));
                    }
                }
            } else {
                giveItemsTask.cancel();
            }
        }
    }

}

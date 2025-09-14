package me.roinujnosde.titansbattle.hooks.discord;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class DiscordAnnounces {
    
    public static void announceStart(String name, int minutes) {
        if (DiscordBot.eventChannel == null) {
            return; // Discord bot not initialized, skip announcement
        }
        
        DiscordBot.eventChannel.sendMessage("<@&879085101592502273>")
            .addEmbeds(DiscordEmbeds.getStartingEmbed(name, minutes))
            .addActionRow(
                Button.primary("events:addRole", Emoji.fromUnicode("\ud83d\udd14")).withLabel("Receber notificações"),
                Button.secondary("events:removeRole", Emoji.fromUnicode("⛔")).withLabel("Deixar de receber")
            ).queue();
    }

    public static void announceStarted(String name, String clans, String players) {
        if (DiscordBot.eventChannel == null) {
            return; // Discord bot not initialized, skip announcement
        }
        
        DiscordBot.eventChannel.sendMessage("<@&879085101592502273>")
            .addEmbeds(DiscordEmbeds.getStartedEmbed(name, clans, players))
            .addActionRow(
                Button.primary("events:addRole", Emoji.fromUnicode("\ud83d\udd14")).withLabel("Receber notificações"),
                Button.secondary("events:removeRole", Emoji.fromUnicode("⛔")).withLabel("Deixar de receber")
            ).queue();
    }

    public static void announceEnded(String name, String winnerName, int winnerPoints, int winnerPlayersCount, int winnerKillsCount, int winnerKillsPoints, String secondPlaceName, int secondPlacePoints, int secondPlacePlayersCount, int secondPlaceKillsCount, int secondPlaceKillsPoints, String thirdPlaceName, int thirdPlacePoints, int thirdPlacePlayersCount, int thirdPlaceKillsCount, int thirdPlaceKillsPoints, long duration, String killerName, int killerKillsCount) {
        if (DiscordBot.eventChannel == null) {
            return; // Discord bot not initialized, skip announcement
        }
        
        DiscordBot.eventChannel.sendMessage("<@&879085101592502273>") //<@&879085101592502273>
            .addEmbeds(DiscordEmbeds.getEndedEmbed(name, winnerName, winnerPoints, winnerPlayersCount, winnerKillsCount, winnerKillsPoints, secondPlaceName, secondPlacePoints, secondPlacePlayersCount, secondPlaceKillsCount, secondPlaceKillsPoints, thirdPlaceName, thirdPlacePoints, thirdPlacePlayersCount, thirdPlaceKillsCount, thirdPlaceKillsPoints, duration, killerName, killerKillsCount))
            .addActionRow(
                Button.primary("events:addRole", Emoji.fromUnicode("\ud83d\udd14")).withLabel("Receber notificações"),
                Button.secondary("events:removeRole", Emoji.fromUnicode("⛔")).withLabel("Deixar de receber")
            ).queue(message -> {
                message.createThreadChannel("%s %s".formatted(name, DateTimeFormatter.ofPattern("dd/MM").format(LocalDateTime.now()))).queue((threadChannel) -> {
                    threadChannel.sendMessage("Compartilha aqui como foi o evento ☺️").queue();
                });
            });
    }

}

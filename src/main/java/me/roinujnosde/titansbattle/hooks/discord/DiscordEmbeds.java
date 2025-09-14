package me.roinujnosde.titansbattle.hooks.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.time.LocalDateTime;

public class DiscordEmbeds {
    
    public static MessageEmbed getStartingEmbed(String name, int minutes) {
        String type = name.startsWith("Gladiador") ? "gladiador" : "miniglad";
        String kit = name.split("-")[1];

        Color color = null;
        String emoji = null;
        String image = null;
        switch (type) {
            case "gladiador": {
                color = Color.decode("#b50000");
                emoji = "<:_d_espada:1276562038482665473>";
                image = "https://i.imgur.com/qzNg42q.png";
                break;
            }
            case "miniglad": {
                color = Color.decode("#ff3838");
                emoji = "<:_d_espadinha:1228222279746588682>";
                image = "https://i.imgur.com/qzNg42q.png";
                break;
            }
        }
        
        EmbedBuilder eb = new EmbedBuilder();
                    eb.setColor(color);
                    eb.setFooter("EnderCraft - Todos os direitos reservados.", "https://i.imgur.com/hAGHv6R.png");
                    eb.setTimestamp(LocalDateTime.now().plusHours(3L));

                    eb.setDescription("""
                            ## %s %s em %s minutos!
                            Junte seu clan e prepare-se para a batalha!

                            **Informações:**
                            - **Kit:** `%s`
                            - **Horário:** `%s`

                            **Premiações:**
                            %s

                            **<:_d_arrow_right:1262068792775934072> Como participar:**
                            Entre no servidor e use o comando **/evento** para participar.
                            
                            **<a:_d_tempo:1228223730334040145> Iniciando em:** %s minutos

                            -# Confira detalhes sobre os gladiadores e liga de clans em nossa [WIKI](https://wiki.endercraft.com.br/liga-de-clans-e-gladiadores).
                            
                            """.formatted(
                                emoji, 
                                type.toUpperCase(), 
                                minutes,
                                kit,
                                getHorario(name),
                                getPremiacoes(type),
                                minutes
                            ));
                    eb.setImage(image);

                    
        return eb.build();
    }

    public static MessageEmbed getStartedEmbed(String name, String clans, String players) {
        String type = name.startsWith("Gladiador") ? "gladiador" : "miniglad";
        String kit = name.split("-")[1];

        Color color = null;
        String emoji = null;
        String image = null;
        switch (type) {
            case "gladiador": {
                color = Color.decode("#b50000");
                emoji = "<:_d_espada:1276562038482665473>";
                image = "https://i.imgur.com/qzNg42q.png";
                break;
            }
            case "miniglad": {
                color = Color.decode("#ff3838");
                emoji = "<:_d_espadinha:1228222279746588682>";
                image = "https://i.imgur.com/qzNg42q.png";
                break;
            }
        }
        
        EmbedBuilder eb = new EmbedBuilder();
                    eb.setColor(color);
                    eb.setFooter("EnderCraft - Todos os direitos reservados.", "https://i.imgur.com/hAGHv6R.png");
                    eb.setTimestamp(LocalDateTime.now().plusHours(3L));

                    eb.setDescription("""
                            ## %s %s em andamento!
                            O %s com kit `%s` começou. Confira os detalhes dos clans participantes.

                            **🔰 Clans participantes:**
                            ```%s```

                            **⭐ Jogadores participantes:**
                            ```%s```
                            
                            **<:_d_arrow_right:1262068792775934072> Como assistir:**
                            Entre no servidor e use o comando **/evento** para assistir.
                            
                            """.formatted(
                                emoji, 
                                type.toUpperCase(),
                                name.split("-")[0],
                                kit,
                                clans,
                                players
                            ));
                    eb.setImage(image);

                    
        return eb.build();
    }

    public static MessageEmbed getEndedEmbed(String name, String winnerName, int winnerPoints, int winnerPlayersCount, int winnerKillsCount, int winnerKillsPoints, String secondPlaceName, int secondPlacePoints, int secondPlacePlayersCount, int secondPlaceKillsCount, int secondPlaceKillsPoints, String thirdPlaceName, int thirdPlacePoints, int thirdPlacePlayersCount, int thirdPlaceKillsCount, int thirdPlaceKillsPoints, long duration, String killerName, int killerKillsCount) {
        String type = name.startsWith("Gladiador") ? "gladiador" : "miniglad";

        Color color = null;
        String emoji = null;
        String image = null;
        switch (type) {
            case "gladiador": {
                color = Color.decode("#b50000");
                emoji = "<:_d_espada:1276562038482665473>";
                image = "https://i.imgur.com/qzNg42q.png";
                break;
            }
            case "miniglad": {
                color = Color.decode("#ff3838");
                emoji = "<:_d_espadinha:1228222279746588682>";
                image = "https://i.imgur.com/qzNg42q.png";
                break;
            }
        }
        
        EmbedBuilder eb = new EmbedBuilder();
                    eb.setColor(color);
                    eb.setFooter("EnderCraft - Todos os direitos reservados.", "https://i.imgur.com/hAGHv6R.png");
                    eb.setTimestamp(LocalDateTime.now().plusHours(3L));

                    eb.setDescription("""
                            ## %s %s finalizado!
                            Confira os detalhes dos vencedores.

                            **🔰 Clans vencedores:**
                            
                            **<a:_d_oro:1228432790614315079> 1º lugar:** `%s` *(+%s pontos)*
                            > Jogadores: %s
                            > Kills: %s *(+%s pontos)*

                            **<a:_d_prata:1228432747350069258> 2º lugar:** `%s` *(+%s pontos)*
                            > Jogadores: %s
                            > Kills: %s *(+%s pontos)*

                            **<a:_d_bronze:1228432679112937583> 3º lugar:** `%s` *(+%s pontos)*
                            > Jogadores: %s
                            > Kills: %s *(+%s pontos)*

                            **<:_d_esqueleto:1276560844104273921> Killer:** `%s` *(+%s kills)*
                            
                            **Duração do evento:** %s minutos
                            
                            -# Veja mais informações e horários dos Gladiadores em nossa [WIKI](https://wiki.endercraft.com.br/liga-de-clans-e-gladiadores).
                            
                            """.formatted(
                                emoji, 
                                name,
                                winnerName, winnerPoints, winnerPlayersCount, winnerKillsCount, winnerKillsPoints,
                                secondPlaceName, secondPlacePoints, secondPlacePlayersCount, secondPlaceKillsCount, secondPlaceKillsPoints,
                                thirdPlaceName, thirdPlacePoints, thirdPlacePlayersCount, thirdPlaceKillsCount, thirdPlaceKillsPoints,
                                killerName, killerKillsCount,
                                duration
                            ));
                    eb.setImage(image);

                    
        return eb.build();
    }

    public static String getHorario(String name) {
        switch (name) {
            case "Gladiador-Nethpot": {
                return "Domingo às 18:30";
            }
            case "Gladiador-SMP": {
                return "Sábado às 18:30";
            }
            case "MiniGlad-Nethpot": {
                return "Quarta às 19:20";
            }
            case "MiniGlad-SMP": {
                return "Terça às 19:20";
            }
            case "MiniGlad-Maces": {
                return "Segunda às 19:20";
            }
            case "MiniGlad-Projeteis": {
                return "Quinta às 19:20";
            }
            case "MiniGlad-Dima": {
                return "Sexta às 19:20";
            }
        }
        return "-/-";
    }

    public static String getPremiacoes(String type) {
        switch (type) {
            case "gladiador": {
                return """
                - <a:_d_oro:1228432790614315079> **1º lugar:** 500.000 Coins + 5.000 EnderGolds + 20 Pontos de Liga
                - <a:_d_prata:1228432747350069258> **2º lugar:** 15 Pontos de Liga
                - <a:_d_bronze:1228432679112937583> **3º lugar:** 10 Pontos de Liga
                - <:_d_esqueleto:1276560844104273921> **Killer:** 5.000 EnderGolds
                """;
            }
            case "miniglad": {
                return """
                - <a:_d_oro:1228432790614315079> **1º lugar:** 100.000 Coins + 3.000 EnderGolds + 20 Pontos de Liga
                - <a:_d_prata:1228432747350069258> **2º lugar:** 15 Pontos de Liga
                - <a:_d_bronze:1228432679112937583> **3º lugar:** 10 Pontos de Liga
                - <:_d_esqueleto:1276560844104273921> **Killer:** 3.000 EnderGolds
                """;
            }
        }
        return "";
    }


}

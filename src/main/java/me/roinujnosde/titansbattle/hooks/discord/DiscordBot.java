package me.roinujnosde.titansbattle.hooks.discord;

import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.NotNull;
import org.bukkit.Bukkit;

import me.roinujnosde.titansbattle.TitansBattle;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class DiscordBot extends ListenerAdapter {
    
    public static JDA jda;
    public static Guild guild;
    public static NewsChannel eventChannel;

    public static final String EVENT_CHANNEL_ID = "1394736975113748500";
    public static final String EVENT_ROLE_ID = "1455703005080060189";

    public static void setupDiscordBot() throws InterruptedException {
        Bukkit.getLogger().info("[TitansBattle] Configurando Discord Bot...");
        
        try {
            jda = JDABuilder.createDefault(
                    TitansBattle.getInstance().getConfig().getString("discord_bot_token")
                    )
                    .setEnabledIntents(EnumSet.allOf(GatewayIntent.class))
                    .build();

            jda.awaitReady();
            jda.addEventListener(new DiscordBot());

            guild = jda.getGuildById("873654816491044864");
            eventChannel = guild.getNewsChannelById(EVENT_CHANNEL_ID);
            
            Bukkit.getLogger().info("[TitansBattle] Discord Bot configurado com sucesso!");
            Bukkit.getLogger().info("[TitansBattle] Conectado ao servidor: " + (guild != null ? guild.getName() : "Unknown"));
            Bukkit.getLogger().info("[TitansBattle] Canal de eventos: " + (eventChannel != null ? eventChannel.getName() : "Not found"));
        } catch (Exception e) {
            Bukkit.getLogger().severe("[TitansBattle] Erro ao configurar Discord Bot: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent e) {
        String userId = e.getUser().getId();
        String username = e.getUser().getName();
        
        if (e.getComponentId().equals("events:addRole")) {
            Bukkit.getLogger().info("[TitansBattle] Usuário " + username + " (" + userId + ") solicitou adicionar role de eventos");
            addRole(e);
        } else if (e.getComponentId().equals("events:removeRole")) {
            Bukkit.getLogger().info("[TitansBattle] Usuário " + username + " (" + userId + ") solicitou remover role de eventos");
            removeRole(e);
        }
    }

    public static void addRole(ButtonInteractionEvent e) {
        if (e.getMember() == null) {
            Bukkit.getLogger().warning("[TitansBattle] Erro: Membro nulo ao tentar adicionar role para " + e.getUser().getName());
            e.reply("Ocorreu um erro interno. Tente novamente mais tarde.").setEphemeral(true).queue();
        } else {
            AtomicBoolean hasRole = new AtomicBoolean(false);
            e.getMember().getRoles().forEach((role) -> {
                if (role.getId().equals(EVENT_ROLE_ID)) {
                    hasRole.set(true);
                }

            });
            if (hasRole.get()) {
                Bukkit.getLogger().info("[TitansBattle] Usuário " + e.getUser().getName() + " já possui a role de eventos");
                e.reply("Você já está recebendo as notificações de novas atualizações! \ud83d\ude0a").setEphemeral(true).queue();
            } else if (e.getGuild() != null) {
                e.getGuild().addRoleToMember(e.getMember(), Objects.requireNonNull(e.getGuild().getRoleById(EVENT_ROLE_ID))).queue();
                Bukkit.getLogger().info("[TitansBattle] Role de eventos adicionada com sucesso para " + e.getUser().getName());
                e.reply("✅ Você agora irá receber as notificações de novos eventos! \ud83d\ude0a").setEphemeral(true).queue();
            }
        }
    }

    public static void removeRole(ButtonInteractionEvent e) {
        if (e.getMember() == null) {
            Bukkit.getLogger().warning("[TitansBattle] Erro: Membro nulo ao tentar remover role para " + e.getUser().getName());
            e.reply("Ocorreu um erro interno. Tente novamente mais tarde.").setEphemeral(true).queue();
        } else {
            AtomicBoolean hasRole = new AtomicBoolean(false);
            e.getMember().getRoles().forEach((role) -> {
                if (role.getId().equals(EVENT_ROLE_ID)) {
                    hasRole.set(true);
                }

            });
            if (!hasRole.get()) {
                Bukkit.getLogger().info("[TitansBattle] Usuário " + e.getUser().getName() + " já não possui a role de eventos");
                e.reply("Você já não está recebendo as notificações de atualização! \ud83d\ude09").setEphemeral(true).queue();
            } else if (e.getGuild() != null) {
                e.getGuild().removeRoleFromMember(e.getMember(), Objects.requireNonNull(e.getGuild().getRoleById(EVENT_ROLE_ID))).queue();
                Bukkit.getLogger().info("[TitansBattle] Role de eventos removida com sucesso para " + e.getUser().getName());
                e.reply("⛔ Você agora não irá mais receber as notificações de novos eventos! \ud83d\ude0a").setEphemeral(true).queue();
            }
        }
    }


}

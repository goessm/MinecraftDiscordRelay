package me.me.discordrelay;

import me.me.discordrelay.discord.Discord;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;

public final class DiscordRelay extends JavaPlugin implements Listener {

    boolean announceServerStatus = true;
    @Override
    public void onEnable() {

        getLogger().info("DiscordRelay enabled!");

        getServer().getPluginManager().registerEvents(this, this);

        saveDefaultConfig();

        if (announceServerStatus) {
            sendDiscordEmbed(Color.GREEN, "Server started");
        }
    }

    @Override
    public void onDisable() {

        reloadConfig();

        getLogger().info("DiscordRelay disabled!");

        if (announceServerStatus) {
            sendDiscordEmbed(Color.RED, "Server shut down");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        String message = e.getMessage().replaceAll("[\"§]", "");
        // Only relay messages starting with !
        if ( message.isEmpty() || !message.startsWith("!")) {
            return;
        }
        // Remove leading !
        StringBuilder sb = new StringBuilder(message);
        sb.deleteCharAt(0);
        message = sb.toString();

        sendDiscordEmbed(Color.BLUE, ":speech_balloon: %s : %s".formatted(e.getPlayer().getName(), message));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        sendDiscordEmbed(Color.YELLOW, e.getDeathMessage());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String joinMessage = e.getJoinMessage().replaceAll("§e", "");
        sendDiscordEmbed(Color.GREEN, joinMessage);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        String quitMessage = e.getQuitMessage().replaceAll("§e", "");
        sendDiscordEmbed(Color.RED, quitMessage);
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        String kickMessage = e.getLeaveMessage().replaceAll("§e", "");
        sendDiscordEmbed(Color.RED, kickMessage);
    }

    @EventHandler
    public void onCreeperPowered(CreeperPowerEvent e) {
        if (e.getCause() == CreeperPowerEvent.PowerCause.LIGHTNING) {
            sendDiscordEmbed(Color.YELLOW, "A creeper was struck by lightning :scream: :cloud_lightning: ");
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        ItemStack items = e.getRecipe().getResult();
        HumanEntity player = e.getWhoClicked();

        if (items.getType() == Material.DIAMOND_HOE) {
            sendDiscordEmbed(Color.CYAN, player.getName() + " crafted a diamond hoe :gem: Why?");
        }
    }

    private void sendDiscordEmbed(Color color, String text) {
        Discord webhook = new Discord(getConfig().getString("webhookURL"));
        webhook.addEmbed(new Discord.EmbedObject()
                .setColor(color)
                .setDescription(text)
        );
        try {
            webhook.execute();
        } catch (java.io.IOException event) {
            getLogger().severe(event.toString());
        }
    }
}

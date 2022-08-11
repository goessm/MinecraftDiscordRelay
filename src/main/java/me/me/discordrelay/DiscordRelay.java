package me.me.discordrelay;

import me.me.discordrelay.discord.DiscordMessage;
import me.me.discordrelay.discord.JoinMessageHandler;
import me.me.discordrelay.discord.WebhookHandler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class DiscordRelay extends JavaPlugin implements Listener {


    private static DiscordRelay plugin;
    private static final Map<String, Instant> lastEventCall = new HashMap<>();

    boolean announceServerStatus = true;

    public static DiscordRelay getPlugin() {
        return plugin;
    }

    @Override
    public void onEnable() {

        plugin = this;

        getCommand("shhhhh").setExecutor(new ShushCommand());

        getLogger().info("DiscordRelay enabled!");

        getServer().getPluginManager().registerEvents(this, this);

        saveDefaultConfig();

        startTasks();

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
        if (!message.startsWith("!")) {
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
        if (!(e.getEntity() instanceof Player)) {
            return;
        }
        if (isShushed(e.getEntity())) {
            return; // Do not relay if player is shushed
        }
        sendDiscordEmbed(Color.YELLOW, e.getDeathMessage());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        JoinMessageHandler.playerJoined(e);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        if (isShushed(e.getPlayer())) {
            removeShushed(e.getPlayer());
            return; // Do not relay if player is shushed
        }
        // Relay leave message
        JoinMessageHandler.playerLeft(e);
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        if (isShushed(e.getPlayer())) {
            return; // Do not relay if player is shushed
        }
        String kickMessage = e.getLeaveMessage().replaceAll("§e", "");
        sendDiscordEmbed(Color.RED, kickMessage);
    }

    @EventHandler
    public void onCreeperPowered(CreeperPowerEvent e) {
        long cooldownSecs = 10;
        if (e.getCause() != CreeperPowerEvent.PowerCause.LIGHTNING) {
            return;
        }
        Instant lastCall = lastEventCall.get("creeper struck by lightning");
        if (lastCall != null) {
            Duration timeSinceLastCall = Duration.between(lastCall, Instant.now());
            if (timeSinceLastCall.toSeconds() < cooldownSecs) {
                // Event on cooldown
                return;
            }
        }
        lastEventCall.put("creeper struck by lightning", Instant.now());
        sendDiscordEmbed(Color.YELLOW, "A creeper was struck by lightning :scream: :cloud_lightning: ");
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntityType() == EntityType.ENDER_DRAGON) {
            Player killer = e.getEntity().getKiller();
            sendDiscordEmbed(Color.CYAN, "%s slayed the ender dragon!"
                    .formatted(killer != null ? killer.getName() : "Someone"));
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

    private void startTasks() {
//        new JoinMessageHandler().startTask();
    }

    /**
     * Send a discord embed message
     *
     * @param color Embed color
     * @param text  Embed text
     * @return The message id of the webhook message, or null on error
     */
    private String sendDiscordEmbed(Color color, String text) {
        DiscordMessage message = new DiscordMessage();
        message.addEmbed(new DiscordMessage.EmbedObject()
                .setColor(color)
                .setDescription(text)
        );
        try {
            WebhookHandler.sendDiscordMessage(message);
        } catch (java.io.IOException event) {
            getLogger().severe(event.toString());
        }
        return message.getMessageId();
    }

    private boolean isShushed(Player player) {
        NamespacedKey shushKey = new NamespacedKey(getPlugin(), "shhhhh");
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        return playerData.has(shushKey, PersistentDataType.STRING);
    }

    private void removeShushed(Player player) {
        NamespacedKey shushKey = new NamespacedKey(getPlugin(), "shhhhh");
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        playerData.remove(shushKey);
    }
}

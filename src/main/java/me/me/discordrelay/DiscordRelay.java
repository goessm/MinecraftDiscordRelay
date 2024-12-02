package me.me.discordrelay;

import me.me.discordrelay.discord.DiscordMessage;
import me.me.discordrelay.discord.BatchMessageHandler;
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
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
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
import java.util.LinkedList;
import java.util.Map;
import java.util.List;

public final class DiscordRelay extends JavaPlugin implements Listener {


    private static DiscordRelay plugin;
    private static final Map<String, Instant> lastEventCall = new HashMap<>();

    private static List<String> webhookUrls;
    private static final List<BatchMessageHandler> batchMessageHandlers = new LinkedList<BatchMessageHandler>();
    private static boolean relayChatOnlyToFirstWebhook = true;
    private static boolean announceServerStatus = false;
    private static boolean permaShush = false;
    private static boolean relayAdvancements = true;
    private static boolean easterEggDiamondHoe = false;
    private static boolean easterEggDragonKill = false;
    private static long easterEggCreeperPowered = 600L;
    private static List<String> ignoredPlayers;

    public static DiscordRelay getPlugin() {
        return plugin;
    }

    @Override
    public void onEnable() {
        webhookUrls = getConfig().getStringList("webhookURLs");
        if (webhookUrls == null || webhookUrls.isEmpty()) {
            getLogger().severe("No webhook URL given! Cannot start Discord Relay");
            return;
        }

        for (String webhookUrl : webhookUrls) {
            BatchMessageHandler handler = new BatchMessageHandler(webhookUrl);
            batchMessageHandlers.add(handler);
        }

        plugin = this;

        getCommand("shhhhh").setExecutor(new ShushCommand());

        getLogger().info("DiscordRelay enabled!");

        getServer().getPluginManager().registerEvents(this, this);

        saveDefaultConfig();

        relayChatOnlyToFirstWebhook = getConfig().getBoolean("relayChatOnlyToFirstWebhook", relayChatOnlyToFirstWebhook);
        announceServerStatus = getConfig().getBoolean("announceServerStatus", announceServerStatus);
        permaShush = getConfig().getBoolean("permaShush", permaShush);
        relayAdvancements = getConfig().getBoolean("relayAdvancements", relayAdvancements);
        easterEggDiamondHoe = getConfig().getBoolean("easterEggDiamondHoe", easterEggDiamondHoe);
        easterEggDragonKill = getConfig().getBoolean("easterEggDragonKill", easterEggDragonKill);
        easterEggCreeperPowered = getConfig().getLong("easterEggCreeperPowered", easterEggCreeperPowered);
        ignoredPlayers = getConfig().getStringList("ignoredPlayers");

        BatchMessageHandler.DiscordBatchMessage.expirationTimeSeconds = getConfig().getInt("batchWindowSeconds");

        startTasks();

        if (announceServerStatus) {
            sendEmbedAllWebhooks(Color.GREEN, "Server started");
        }
    }

    @Override
    public void onDisable() {

        reloadConfig();

        getLogger().info("DiscordRelay disabled!");

        if (announceServerStatus) {
            sendEmbedAllWebhooks(Color.RED, "Server shut down");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        String message = e.getMessage();
        if (!message.startsWith("!") || message.length() <= 1) {
            return; // Only relay messages starting with prefix
        }

        message = message.replaceAll("[\"§]", "");
        // Remove leading !
        StringBuilder sb = new StringBuilder(message);
        sb.deleteCharAt(0);
        message = sb.toString();

        Color color = Color.BLUE;
        String text = ":speech_balloon: %s : %s".formatted(e.getPlayer().getName(), message);
        if (relayChatOnlyToFirstWebhook) {
            batchMessageHandlers.get(0).sendDiscordEmbed(color, text);
        } else {
            sendEmbedAllWebhooks(color, text);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (isShushed(e.getEntity())) {
            return; // Do not relay if player is shushed
        }

        batchMessageHandlers.forEach(handler -> handler.playerDied(e));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (isShushed(e.getPlayer())) {
            return; // Do not relay if player is shushed
        }

        batchMessageHandlers.forEach(handler -> handler.playerJoined(e));
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        if (isShushed(e.getPlayer())) {
            removeShushedOnLeave(e.getPlayer());
            return; // Do not relay if player is shushed
        }
        // Relay leave message
        batchMessageHandlers.forEach(handler -> handler.playerLeft(e));
    }

    @EventHandler
    public void onPlayerAdvancementDoneEvent(PlayerAdvancementDoneEvent e) {
        if (!relayAdvancements) {
            return; // Advancements disabled
        }

        if (e.getAdvancement().getDisplay() == null || !e.getAdvancement().getDisplay().shouldAnnounceChat()) {
            return;
        }

        if (isShushed(e.getPlayer())) {
            return; // Do not relay if player is shushed
        }

        batchMessageHandlers.forEach(handler -> handler.playerAdvancement(e));
    }

    @EventHandler
    public void onCreeperPowered(CreeperPowerEvent e) {
        if (easterEggCreeperPowered < 0) {
            return;
        }
        long cooldownSecs = easterEggCreeperPowered;
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
        sendEmbedAllWebhooks(Color.YELLOW, "A creeper was struck by lightning :scream: :cloud_lightning: ");
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (easterEggDragonKill && e.getEntityType() == EntityType.ENDER_DRAGON) {
            Player killer = e.getEntity().getKiller();
            String text = "%s slayed the ender dragon!".formatted(killer != null ? killer.getName() : "Someone");
            sendEmbedAllWebhooks(Color.CYAN, text);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!easterEggDiamondHoe) {
            return;
        }
        ItemStack items = e.getRecipe().getResult();
        HumanEntity player = e.getWhoClicked();

        if (items.getType() == Material.DIAMOND_HOE) {
            sendEmbedAllWebhooks(Color.CYAN, player.getName() + " crafted a diamond hoe :gem: Why?");
        }
    }

    public void removeLastJoinMessage(Player player) {
        batchMessageHandlers.forEach(handler -> handler.hideLastPlayerJoin(player.getName()));
    }

    private void startTasks() {
//        new JoinMessageHandler().startTask();
    }

    private boolean isShushed(Player player) {
        if (ignoredPlayers != null && ignoredPlayers.contains(player.getName())) {
            return true; // On ignore list
        }

        NamespacedKey shushKey = new NamespacedKey(getPlugin(), "shhhhh");
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        return playerData.has(shushKey, PersistentDataType.STRING);
    }

    private void removeShushedOnLeave(Player player) {
        if (permaShush) {
            return; // Keep player shushed
        }

        NamespacedKey shushKey = new NamespacedKey(getPlugin(), "shhhhh");
        PersistentDataContainer playerData = player.getPersistentDataContainer();
        playerData.remove(shushKey);
    }

    private void sendEmbedAllWebhooks(Color color, String text) {
        batchMessageHandlers.forEach(handler -> handler.sendDiscordEmbed(color, text));
    }
}

package me.me.discordrelay;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;

import static me.me.discordrelay.discord.Discord.deleteMessage;
public class CleanOldMessages {

    private static ArrayList<JoinMessage> joinMessages = new ArrayList<JoinMessage>();

    private static Instant lastCleanup = Instant.now();

    private static final long cleanCooldown = 10;

    public void startTask() {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTaskTimerAsynchronously(DiscordRelay.getPlugin(), () -> {
            Bukkit.broadcastMessage("Mooooo!");
        }, 20L * 10L, 20L * 5L);
    }

    public static void cleanOldJoinMessages() {
        if (Math.abs(Duration.between(lastCleanup, Instant.now()).toSeconds()) < cleanCooldown) {
            return; // On cooldown
        }
        lastCleanup = Instant.now();
        Iterator<JoinMessage> iter = joinMessages.iterator();
        while(iter.hasNext()) {
            JoinMessage joinMessage = iter.next();
            if (isOld(joinMessage.timestamp)) {
                Boolean success = deleteMessage(DiscordRelay.getPlugin().getConfig().getString("webhookURL"), joinMessage.messageId);
                System.out.println(success);
                if (success) {
                    iter.remove();
                }
            }
        }
    }

    public static void registerJoinMessage(String messageId, Instant timestamp, String playerName) {
        JoinMessage joinMessage = new JoinMessage(messageId, timestamp, playerName);
        joinMessages.add(joinMessage);
        System.out.println(joinMessages);
        cleanOldJoinMessages();
    }

    private static boolean isOld(Instant timestamp) {
        Instant now = Instant.now();
        Duration duration = Duration.between(timestamp, now);
        return Math.abs(duration.toSeconds()) > 20;
    }

    private static class JoinMessage {
        public String messageId;
        public Instant timestamp;
        public String playerName;


        public JoinMessage(String messageId, Instant timestamp, String playerName) {
            this.messageId = messageId;
            this.timestamp = timestamp;
            this.playerName = playerName;
        }
    }
}

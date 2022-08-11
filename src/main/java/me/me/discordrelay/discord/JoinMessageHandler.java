package me.me.discordrelay.discord;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
public class JoinMessageHandler {

    private static DiscordJoinMessage lastJoinMessage;

    public static void playerJoined(PlayerJoinEvent e) {
        String emojiGreenCircle = ":green_circle:";
        String playerName = e.getPlayer().getName();
        String joinMessage = emojiGreenCircle + " " + e.getJoinMessage().replaceAll("§e", "");
        JoinMessageEntry newJoinMsg = new JoinMessageEntry(true, Instant.now(), playerName, joinMessage);

        addJoinMessageEntry(newJoinMsg);
    }

    public static void playerLeft(PlayerQuitEvent e) {
        String emojiRedCircle = ":red_circle:";
        String playerName = e.getPlayer().getName();
        String quitMessage = emojiRedCircle + " " + e.getQuitMessage().replaceAll("§e", "");
        JoinMessageEntry newJoinMsg = new JoinMessageEntry(false, Instant.now(), playerName, quitMessage);
        addJoinMessageEntry(newJoinMsg);
    }

    public static void hideLastPlayerJoin(String playerName) {
        if (lastJoinMessage == null) {
            return;
        }
        Iterator<JoinMessageEntry> iter = lastJoinMessage.joinMessageEntries.descendingIterator();
        while (iter.hasNext()) {
            JoinMessageEntry entry = iter.next();
            if (entry.playerName == playerName) {
                iter.remove();
                break;
            }
        }

        if (lastJoinMessage.joinMessageEntries.size() > 0) {
            patchJoinMessage();
        } else {
            WebhookHandler.deleteDiscordMessage(lastJoinMessage.discordMessage);
            lastJoinMessage = null;
        }
    }

    public static void addJoinMessageEntry(JoinMessageEntry entry) {
        if (lastJoinMessage == null || lastJoinMessage.isOld()) {
            DiscordJoinMessage discordJoinMessage = new DiscordJoinMessage(entry);
            sendJoinMessage(discordJoinMessage);
        } else {
            lastJoinMessage.joinMessageEntries.add(entry);
            patchJoinMessage();
        }
    }


    public static void sendJoinMessage(DiscordJoinMessage discordJoinMessage) {
        discordJoinMessage.buildMessageContent();
        try {
            WebhookHandler.sendDiscordMessage(discordJoinMessage.discordMessage);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        lastJoinMessage = discordJoinMessage;

    }
    public static void patchJoinMessage() {
        lastJoinMessage.buildMessageContent();
        WebhookHandler.patchDiscordMessage(lastJoinMessage.discordMessage);
    }


    /* Scheduled Task Reference

    public void startTask() {
        System.out.println("starting summarize task");
        int runAtHour = 13;
        Calendar c = Calendar.getInstance();
        if (c.get(Calendar.HOUR_OF_DAY) >= runAtHour) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime nextRun = now.withHour(runAtHour).withMinute(11).withSecond(0);
        if(now.compareTo(nextRun) > 0) {
            nextRun = nextRun.plusDays(1);
        }

        Duration duration = Duration.between(now, nextRun);
        long initialDelay = duration.getSeconds();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(JoinMessageHandler::summarizeJoinMessages,
                initialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS);
    }

     */




    public static class DiscordJoinMessage {
        public int expirationTimeSeconds = 60 * 60 * 5;
        public LinkedList<JoinMessageEntry> joinMessageEntries;
        public DiscordMessage discordMessage = new DiscordMessage();
        public Instant createdTimestamp;
        public Instant updatedTimestamp;

        public DiscordJoinMessage(LinkedList<JoinMessageEntry> joinMessageEntries) {
            this.joinMessageEntries = joinMessageEntries;
            this.createdTimestamp = Instant.now();
            this.updatedTimestamp = Instant.now();
        }

        public DiscordJoinMessage(JoinMessageEntry joinMessageEntry) {
            this.joinMessageEntries = new LinkedList<>();
            joinMessageEntries.add(joinMessageEntry);
            this.createdTimestamp = Instant.now();
            this.updatedTimestamp = Instant.now();
        }

        public void buildMessageContent() {
            StringBuilder sb = new StringBuilder();
            for (JoinMessageEntry joinMessageEntry : joinMessageEntries) {
                sb.append(joinMessageEntry.messageContent).append("\n");
            }
            DiscordMessage.EmbedObject embed = new DiscordMessage.EmbedObject()
                    .setColor(3553599)
                    .setDescription(sb.toString());
            discordMessage.clearEmbeds();
            discordMessage.addEmbed(embed);
        }

        public boolean isOld() {
            Instant now = Instant.now();
            Duration duration = Duration.between(createdTimestamp, now);
            return(Math.abs(duration.toSeconds()) >= expirationTimeSeconds);
        }
    }
    public static class JoinMessageEntry {
        // True if player joins, false if player leaves
        public boolean isJoin;
        public Instant timestamp;
        public String playerName;
        public String messageContent;


        public JoinMessageEntry(boolean isJoin, Instant timestamp, String playerName, String messageContent) {
            this.isJoin = isJoin;
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.messageContent = messageContent;
        }
    }
}

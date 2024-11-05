package me.me.discordrelay.discord;

import me.me.discordrelay.DiscordRelay;

import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.awt.*;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class BatchMessageHandler {

    private static DiscordBatchMessage lastBatchMessage;
    private static final HashMap<String, BatchMessageEntry> lastJoinMessages = new HashMap<>();

    public static void playerJoined(PlayerJoinEvent e) {
        String playerName = e.getPlayer().getName();
        String joinMessage = e.getJoinMessage();
        joinMessage = joinMessage == null ? "" : joinMessage;
        joinMessage = joinMessage.replaceAll("§e", "");
        BatchMessageEntry joinMsg = new BatchMessageEntry(Instant.now(), playerName, ":green_circle:", joinMessage, Color.green);
        lastJoinMessages.put(playerName, joinMsg);
        addBatchMessageEntry(joinMsg);
    }

    public static void playerLeft(PlayerQuitEvent e) {
        String playerName = e.getPlayer().getName();
        String quitMessage = e.getQuitMessage();
        quitMessage = quitMessage == null ? "" : quitMessage;
        quitMessage = quitMessage.replaceAll("§e", "");
        BatchMessageEntry leaveMsg = new BatchMessageEntry(Instant.now(), playerName, ":red_circle:", quitMessage, Color.red);
        addBatchMessageEntry(leaveMsg);
    }

    public static void playerDied(PlayerDeathEvent e) {
        String deathMessage = e.getDeathMessage();
        BatchMessageEntry deathMsg = new BatchMessageEntry(Instant.now(), e.getEntity().getName(), ":skull_crossbones:", deathMessage, Color.yellow);
        addBatchMessageEntry(deathMsg);
    }

    public static void playerAdvancement(PlayerAdvancementDoneEvent e) {
        String playerName = e.getPlayer().getName();
        Advancement advancement = e.getAdvancement();
        if (advancement == null) {
            return;
        }
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null) {
            return;
        }
        String advancementName = display.getTitle();
        String msgText = String.format("%s has achieved: %s", playerName, advancementName);
        Color goldColor = new Color(12745742);
        BatchMessageEntry msg = new BatchMessageEntry(Instant.now(), playerName, ":trophy:", msgText, goldColor);
        msg.iconOverflowOnly = false; // Always show icon
        addBatchMessageEntry(msg);
    }

    public static void hideLastPlayerJoin(String playerName) {
        if (lastBatchMessage == null) {
            return;
        }
        if (!lastJoinMessages.containsKey(playerName)) {
            return;
        }
        lastBatchMessage.batchMessageEntries.remove(lastJoinMessages.get(playerName));
        lastJoinMessages.remove(playerName);

        if (lastBatchMessage.batchMessageEntries.size() > 0) {
            patchBatchMessage();
        } else {
            WebhookHandler.deleteDiscordMessage(lastBatchMessage.discordMessage);
            lastBatchMessage = null;
        }
    }

    public static void addBatchMessageEntry(BatchMessageEntry entry) {
        if (lastBatchMessage == null || lastBatchMessage.isOld()) {
            DiscordBatchMessage discordBatchMessage = new DiscordBatchMessage(entry);
            sendBatchMessage(discordBatchMessage);
        } else {
            lastBatchMessage.batchMessageEntries.add(entry);
            patchBatchMessage();
        }
    }


    public static void sendBatchMessage(DiscordBatchMessage discordBatchMessage) {
        discordBatchMessage.buildMessageContent();
        try {
            WebhookHandler.sendDiscordMessage(discordBatchMessage.discordMessage);
        } catch (IOException e) {
            DiscordRelay.getPlugin().getLogger().severe(e.getMessage());
        }
        lastBatchMessage = discordBatchMessage;

    }

    public static void patchBatchMessage() {
        lastBatchMessage.buildMessageContent();
        WebhookHandler.patchDiscordMessage(lastBatchMessage.discordMessage);
    }

    public static class DiscordBatchMessage {
        public static int expirationTimeSeconds = 60 * 60 * 5;
        public LinkedList<BatchMessageEntry> batchMessageEntries;
        public DiscordMessage discordMessage = new DiscordMessage();
        public Instant createdTimestamp;
        public Instant updatedTimestamp;

        public DiscordBatchMessage(LinkedList<BatchMessageEntry> batchMessageEntries) {
            this.batchMessageEntries = batchMessageEntries;
            this.createdTimestamp = Instant.now();
            this.updatedTimestamp = Instant.now();
        }

        public DiscordBatchMessage(BatchMessageEntry batchMessageEntry) {
            this.batchMessageEntries = new LinkedList<>();
            batchMessageEntries.add(batchMessageEntry);
            this.createdTimestamp = Instant.now();
            this.updatedTimestamp = Instant.now();
        }

        public void buildMessageContent() {
            int embedsLeft = 10;
            discordMessage.clearEmbeds();

            StringBuilder lastEmbedText = new StringBuilder();
            for (BatchMessageEntry batchMessageEntry : batchMessageEntries) {
                if (embedsLeft <= 1) {
                    lastEmbedText.append(batchMessageEntry.getFormattedMessage(true)).append("\n\n");;
                } else {
                    embedsLeft -= 1;
                    discordMessage.addEmbed(new DiscordMessage.EmbedObject()
                            .setColor(batchMessageEntry.embedColor)
                            .setDescription(batchMessageEntry.getFormattedMessage(false)));
                }
            }

            if (lastEmbedText.toString().length() > 0) {
                // Last embed used
                Color lastEmbedColor = batchMessageEntries.getLast().embedColor;
                discordMessage.addEmbed(new DiscordMessage.EmbedObject()
                        .setColor(lastEmbedColor)
                        .setDescription(lastEmbedText.toString()));
            }
        }

        public boolean isOld() {
            Instant now = Instant.now();
            Duration duration = Duration.between(createdTimestamp, now);
            return (Math.abs(duration.toSeconds()) >= expirationTimeSeconds);
        }
    }

    public static class BatchMessageEntry {
        public Instant timestamp;
        public String playerName;
        public String icon;
        public boolean iconOverflowOnly;
        public String messageContent;
        public Color embedColor;


        public BatchMessageEntry(Instant timestamp, String playerName, String icon, String messageContent, Color embedColor) {
            this.timestamp = timestamp;
            this.playerName = playerName;
            this.icon = icon;
            iconOverflowOnly = true;
            this.messageContent = messageContent;
            this.embedColor = embedColor;
        }

        public String getFormattedMessage(boolean isOverflow) {
            String discordTimestamp = "<t:" + timestamp.getEpochSecond() + ":t>";
            StringBuilder sb = new StringBuilder();
            boolean hideIcon = iconOverflowOnly && !isOverflow;
            if (!hideIcon) {
                sb.append(icon).append(" ");
            }
            sb.append(discordTimestamp).append(" ");
            sb.append(messageContent);
            return sb.toString();
        }
    }
}

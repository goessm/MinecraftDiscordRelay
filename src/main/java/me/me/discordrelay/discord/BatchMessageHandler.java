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

    // Constants defined by discord limits
    private static final int MAX_MSG_EMBEDS = 10;
    private static final int MAX_MSG_LENGTH = 4096;

    public String webhookUrl;
    private DiscordBatchMessage lastBatchMessage;
    // Map Key: PlayerName
    private final HashMap<String, BatchMessageEntry> lastJoinMessages = new HashMap<>();

    public BatchMessageHandler(String url) {
        webhookUrl = url;
    }

    /**
     * Send a discord embed message
     *
     * @param color Embed color
     * @param text  Embed text
     * @return The message id of the webhook message, or null on error
     */
    public String sendDiscordEmbed(Color color, String text) {
        DiscordMessage message = new DiscordMessage();
        message.addEmbed(new DiscordMessage.EmbedObject()
                .setColor(color)
                .setDescription(text)
        );
        try {
            WebhookHandler.sendDiscordMessage(message, webhookUrl);
        } catch (java.io.IOException event) {
            DiscordRelay.getPlugin().getLogger().severe(event.toString());
        }
        return message.getMessageId();
    }

    public void playerJoined(PlayerJoinEvent e) {
        String playerName = e.getPlayer().getName();
        String joinMessage = e.getJoinMessage();
        joinMessage = joinMessage == null ? "" : joinMessage;
        joinMessage = joinMessage.replaceAll("§e", "");
        BatchMessageEntry joinMsg = new BatchMessageEntry(Instant.now(), playerName, ":green_circle:", joinMessage, Color.green);
        lastJoinMessages.put(playerName, joinMsg);
        addBatchMessageEntry(joinMsg);
    }

    public void playerLeft(PlayerQuitEvent e) {
        String playerName = e.getPlayer().getName();
        String quitMessage = e.getQuitMessage();
        quitMessage = quitMessage == null ? "" : quitMessage;
        quitMessage = quitMessage.replaceAll("§e", "");
        BatchMessageEntry leaveMsg = new BatchMessageEntry(Instant.now(), playerName, ":red_circle:", quitMessage, Color.red);
        addBatchMessageEntry(leaveMsg);
    }

    public void playerDied(PlayerDeathEvent e) {
        String deathMessage = e.getDeathMessage();
        BatchMessageEntry deathMsg = new BatchMessageEntry(Instant.now(), e.getEntity().getName(), ":skull_crossbones:", deathMessage, Color.yellow);
        addBatchMessageEntry(deathMsg);
    }

    public void playerAdvancement(PlayerAdvancementDoneEvent e) {
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

    public void hideLastPlayerJoin(String playerName) {
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
            WebhookHandler.deleteDiscordMessage(lastBatchMessage.discordMessage, webhookUrl);
            lastBatchMessage = null;
        }
    }

    public void addBatchMessageEntry(BatchMessageEntry entry) {
        if (lastBatchMessage == null || lastBatchMessage.isOld()) {
            // No old message, or message too old
            sendBatchMessage(new DiscordBatchMessage(entry));
            return;
        }

        lastBatchMessage.batchMessageEntries.add(entry);

        if (lastBatchMessage.getEstimatedLength() >= MAX_MSG_LENGTH) {
            // Old message too long, need new one
            sendBatchMessage(new DiscordBatchMessage(entry));
            return;
        }

        patchBatchMessage();
    }


    public void sendBatchMessage(DiscordBatchMessage discordBatchMessage) {
        discordBatchMessage.buildMessageContent();
        try {
            WebhookHandler.sendDiscordMessage(discordBatchMessage.discordMessage, webhookUrl);
        } catch (IOException e) {
            DiscordRelay.getPlugin().getLogger().severe(e.getMessage());
        }
        lastBatchMessage = discordBatchMessage;

    }

    public void patchBatchMessage() {
        lastBatchMessage.buildMessageContent();
        WebhookHandler.patchDiscordMessage(lastBatchMessage.discordMessage, webhookUrl);
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
            int embedsLeft = MAX_MSG_EMBEDS;
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

        // Returns generous (>=) estimate of the character count of the message
        // Used to avoid discords message size limit
        // Is not exact because it does not account for emojis being shown/hidden
        public int getEstimatedLength() {
            int estimate = 0;

            for (BatchMessageEntry batchMessageEntry : batchMessageEntries) {
                estimate += batchMessageEntry.getEstimatedLength();
            }

            return estimate;
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

        // Estimate of how long the message will be
        // Assumes emoji is included
        public int getEstimatedLength() {
            return getFormattedMessage(true).length();
        }
    }
}

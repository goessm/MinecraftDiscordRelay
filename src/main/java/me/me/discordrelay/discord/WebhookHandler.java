package me.me.discordrelay.discord;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import me.me.discordrelay.DiscordRelay;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class WebhookHandler {
    private static int rateLimitRemaining = 0;
    private static Instant rateLimitReset = Instant.now();

    public static void sendDiscordMessage(DiscordMessage msg, String webhookUrl) throws IOException {
        if (msg.content == null && msg.embeds.isEmpty()) {
            throw new IllegalArgumentException("Set content or add at least one EmbedObject");
        }

        Gson gson = new Gson();
        String json = gson.toJson(msg);

        try {
            URL url = new URL(webhookUrl + "?wait=true");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.addRequestProperty("Content-Type", "application/json");
            connection.addRequestProperty("User-Agent", "Java-DiscordWebhook");
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");

            OutputStream stream = connection.getOutputStream();
            stream.write(json.getBytes());
            stream.flush();
            stream.close();

            InputStream inputStream = connection.getInputStream();
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            List<String> headerRemaining = responseHeaders.get("x-ratelimit-remaining");
            if (headerRemaining != null) {
                rateLimitRemaining = Integer.parseInt(headerRemaining.get(0));
            }
            List<String> headerReset = responseHeaders.get("x-ratelimit-reset");
            if (headerReset != null) {
                rateLimitReset = Instant.ofEpochSecond(Long.parseLong(headerReset.get(0)));
            }

            // Get messageId from response
            JsonReader reader = new JsonReader(new InputStreamReader(inputStream, "UTF-8"));
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals("id")) {
                        msg.setMessageId(reader.nextString());
                        break;
                    } else {
                        reader.skipValue();
                    }
                }
            }

            inputStream.close();
            connection.disconnect();
        } catch (Exception e) {
            DiscordRelay.getPlugin().getLogger().severe(e.getMessage());
        }
    }

    public static void patchDiscordMessage(DiscordMessage msg, String webhookUrl) {
        if (msg.content == null && msg.embeds.isEmpty()) {
            throw new IllegalArgumentException("Set content or add at least one EmbedObject");
        }
        if (msg.getMessageId() == null) {
            DiscordRelay.getPlugin().getLogger().info("Patch failed: No message Id");
            return;
        }

        Gson gson = new Gson();
        String json = gson.toJson(msg);

        try {

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl + "/messages/" + msg.getMessageId()))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Map<String, List<String>> responseHeaders = response.headers().map();
            List<String> headerRemaining = responseHeaders.get("x-ratelimit-remaining");
            if (headerRemaining != null) {
                rateLimitRemaining = Integer.parseInt(headerRemaining.get(0));
            }
            List<String> headerReset = responseHeaders.get("x-ratelimit-reset");
            if (headerReset != null) {
                rateLimitReset = Instant.ofEpochSecond(Long.parseLong(headerReset.get(0)));
            }
        } catch (Exception e) {
            DiscordRelay.getPlugin().getLogger().severe(e.getMessage());
        }

    }

    public static boolean deleteDiscordMessage(DiscordMessage msg, String webhookUrl) {
        return deleteDiscordMessageById(msg.getMessageId(), webhookUrl);
    }

    public static boolean deleteDiscordMessageById(String messageId, String webhookUrl) {
        try {
            URL url = new URL(webhookUrl + "/messages/" + messageId);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.addRequestProperty("Content-Type", "application/json");
            connection.addRequestProperty("User-Agent", "Java-DiscordWebhook");
            connection.setDoOutput(true);
            connection.setRequestMethod("DELETE");

            connection.getInputStream().close();
            connection.disconnect();
        } catch (Exception e) {
            DiscordRelay.getPlugin().getLogger().severe(e.getMessage());
            return false;
        }
        return true;
    }
}

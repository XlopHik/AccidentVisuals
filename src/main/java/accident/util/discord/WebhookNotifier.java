package accident.util.discord;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebhookNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger("Accident");

    private static final String WEBHOOK_URL = "https://discordapp.com/api/webhooks/1462241913456296030/SunCmXbOXbHkTDbUgchlz59OCnoIwDl0XK4duwNvFfkDcV7Lr_AC1qNEZ_XH_XDP9mxp";
    private static final String HIT_BASE = "https://abacus.jasoncameron.dev/hit/accidentclient/";
    private static final String GET_BASE = "https://abacus.jasoncameron.dev/get/accidentclient/";
    private static final String LAUNCH_KEY = "launches";
    private static final String USERS_KEY  = "users";

    private static final Pattern COUNT = Pattern.compile("\"value\"\\s*:\\s*(\\d+)");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public void sendActivationNotification() {
        CompletableFuture.runAsync(() -> {
            try {
                // Total launches: one bump every time the client starts.
                long launches = bump(LAUNCH_KEY);

                // уникальные юзеры по хешу имени машины: счётчик стал 1 - машина новая,
                // тогда двигаем общий тотал, иначе счётчик уже больше 1 и тотал не трогаем
                String machineHash = hash(getComputerName());
                long thisMachine = bump("pc_" + machineHash);

                long users;
                if (thisMachine == 1L) {
                    users = bump(USERS_KEY);
                } else {
                    users = read(USERS_KEY);
                }

                sendWebhookMessage(buildMessage(launches, users));
            } catch (Exception e) {
                LOGGER.error("stats notify failed: {}", e.getMessage());
            }
        });
    }

    private String buildMessage(long launches, long users) {
        return "{"
                + "\"embeds\": [{"
                + "\"title\": \"\uD83D\uDFE2 Новый запуск\","
                + "\"color\": 3066993,"
                + "\"fields\": ["
                + "{ \"name\": \"Всего запусков\", \"value\": \"" + launches + "\", \"inline\": true },"
                + "{ \"name\": \"Всего пользователей\", \"value\": \"" + users + "\", \"inline\": true }"
                + "]"
                + "}]"
                + "}";
    }

    private long bump(String key) {
        return query(HIT_BASE + key);
    }

    private long read(String key) {
        return query(GET_BASE + key);
    }

    private long query(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Matcher m = COUNT.matcher(response.body());
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
        } catch (Exception e) {
            LOGGER.error("counter query failed: {}", e.getMessage());
        }
        return 0L;
    }

    private String getComputerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String env = System.getenv("COMPUTERNAME");
            return env != null ? env : "unknown";
        }
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (Exception e) {
            return "0";
        }
    }

    private void sendWebhookMessage(String jsonMessage) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEBHOOK_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMessage))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            LOGGER.error("webhook send failed: {}", e.getMessage());
        }
    }
}

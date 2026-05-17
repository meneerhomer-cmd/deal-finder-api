package be.dealfinder.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Thin wrapper around FirebaseMessaging for sending a push to one device.
 * Returns true on success, false on any failure (logged, never throws).
 * Invalid tokens are detected so callers can clean them up from Firestore.
 */
@ApplicationScoped
public class FcmService {

    private static final Logger LOG = Logger.getLogger(FcmService.class);

    public boolean sendToToken(String token, String title, String body, Map<String, String> data) {
        if (FirebaseApp.getApps().isEmpty()) {
            LOG.debug("FCM send skipped: Firebase not initialised");
            return false;
        }
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data == null ? Map.of() : data)
                    .setWebpushConfig(WebpushConfig.builder()
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .setIcon("/assets/icon-192x192.png")
                                    .build())
                            .build())
                    .build();
            FirebaseMessaging.getInstance().send(message);
            return true;
        } catch (FirebaseMessagingException e) {
            String code = e.getErrorCode() != null ? e.getErrorCode().name() : "UNKNOWN";
            LOG.warn("FCM send failed for token " + truncate(token) + " — " + code + ": " + e.getMessage());
            return false;
        }
    }

    public boolean isTokenInvalid(FirebaseMessagingException e) {
        if (e.getErrorCode() == null) return false;
        return switch (e.getErrorCode().name()) {
            case "UNREGISTERED", "INVALID_ARGUMENT" -> true;
            default -> false;
        };
    }

    private String truncate(String s) {
        return s.length() > 16 ? s.substring(0, 16) + "…" : s;
    }
}

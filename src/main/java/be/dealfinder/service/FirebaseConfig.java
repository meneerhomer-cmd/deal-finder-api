package be.dealfinder.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;

/**
 * Initialises Firebase Admin SDK at startup using Cloud Run's default
 * service account credentials. Once initialised, FirebaseApp.getInstance()
 * + FirestoreClient + FirebaseMessaging are available app-wide.
 *
 * Disabled (no init, no errors) when firebase.enabled=false — useful for
 * dev where you don't want to authenticate against a real GCP project.
 */
@ApplicationScoped
public class FirebaseConfig {

    private static final Logger LOG = Logger.getLogger(FirebaseConfig.class);

    @ConfigProperty(name = "firebase.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "firebase.project-id", defaultValue = "promo-finder-be")
    String projectId;

    void onStart(@Observes StartupEvent ev) {
        if (!enabled) {
            LOG.info("Firebase Admin SDK disabled (firebase.enabled=false)");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            LOG.info("Firebase Admin SDK already initialised");
            return;
        }
        try {
            FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(projectId)
                    .build());
            LOG.info("Firebase Admin SDK initialised for project " + projectId);
        } catch (IOException e) {
            LOG.error("Firebase Admin SDK init failed; FCM and Firestore reads will be unavailable", e);
        }
    }
}

package be.dealfinder.service;

import io.quarkus.runtime.StartupEvent;
import io.sentry.Sentry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

/**
 * Sets the Sentry "release" tag from Cloud Run's K_REVISION env var so events
 * carry the revision identifier (e.g. "deal-finder-api-00050-9w5"). Without this,
 * Sentry has no way to know which deploy an event came from, and its
 * resolved-in-next-release auto-marking never fires — that's what made
 * yesterday's JAVA-3 look like a live regression when it had actually been fixed
 * by a later revision. Quarkiverse logging-sentry 2.3.x doesn't expose a release
 * config key, so we set it via the underlying io.sentry SDK after init runs.
 */
@ApplicationScoped
public class SentryReleaseConfig {

    private static final Logger LOG = Logger.getLogger(SentryReleaseConfig.class);

    void onStart(@Observes StartupEvent ev) {
        String revision = System.getenv("K_REVISION");
        if (revision == null || revision.isBlank()) {
            LOG.info("K_REVISION env var absent; Sentry release tag not set (local dev)");
            return;
        }
        if (!Sentry.isEnabled()) {
            LOG.info("Sentry not enabled; skipping release-tag config");
            return;
        }
        Sentry.getCurrentScopes().getOptions().setRelease(revision);
        LOG.info("Sentry release set to " + revision);
    }
}

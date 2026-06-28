package be.dealfinder.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Shared-secret gate for the {@code /api/v1/admin/*} endpoints (scrape,
 * backfill-images, aggregate-products, ...). Those trigger the scraper and —
 * once Anthropic credits are live again — paid Claude extraction, so they must
 * not be anonymously callable on the public Cloud Run URL.
 *
 * <p><b>Fail-open by design when {@code admin.api-key} is unset.</b> Deploying
 * this filter alone changes nothing, so the 2×/day Cloud Scheduler keeps
 * working during rollout. The gate only activates once {@code ADMIN_API_KEY} is
 * set (and the scheduler is updated to send the {@code X-Admin-Key} header).
 * With a key configured, a missing or wrong header is rejected with 401.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AdminAuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(AdminAuthFilter.class);
    static final String HEADER = "X-Admin-Key";
    private static final String ADMIN_PREFIX = "api/v1/admin";

    // Optional, not String-with-empty-default: SmallRye treats a "" default as
    // null and throws SRCFG00040 at injection time.
    @ConfigProperty(name = "admin.api-key")
    Optional<String> adminApiKey;

    /** The configured key, or "" when unset/blank. */
    private String key() {
        return adminApiKey != null ? adminApiKey.orElse("") : "";
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (!isAdminPath(path)) return;

        if (key().isBlank()) {
            LOG.warn("admin.api-key is not configured — /admin endpoints are UNAUTHENTICATED. "
                    + "Set ADMIN_API_KEY (and the scheduler's X-Admin-Key header) to lock them down.");
            return; // fail-open until configured
        }

        if (!authorized(path, ctx.getHeaderString(HEADER))) {
            LOG.warnf("Rejected unauthenticated admin request to %s", path);
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"admin endpoints require a valid " + HEADER + " header\"}")
                    .type("application/json")
                    .build());
        }
    }

    /** True for any request under {@code /api/v1/admin} (with or without a leading slash). */
    static boolean isAdminPath(String rawPath) {
        if (rawPath == null) return false;
        String p = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        return p.startsWith(ADMIN_PREFIX);
    }

    /**
     * Pure authorisation decision (unit-testable without booting Quarkus):
     * non-admin paths always pass; admin paths pass when no key is configured
     * (fail-open) or when the supplied header matches the configured key.
     */
    boolean authorized(String rawPath, String providedHeader) {
        if (!isAdminPath(rawPath)) return true;
        String configured = key();
        if (configured.isBlank()) return true;
        return providedHeader != null && constantTimeEquals(providedHeader, configured);
    }

    /** Length-aware constant-time compare to avoid leaking the key via timing. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}

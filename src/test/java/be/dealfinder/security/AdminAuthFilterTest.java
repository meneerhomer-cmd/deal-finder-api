package be.dealfinder.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for the admin gate's authorisation decision. No Quarkus boot
 * (a {@code @TestProfile} would force a second boot and re-init Firebase), so we
 * test the decision method directly with the key field set by hand.
 */
class AdminAuthFilterTest {

    private static AdminAuthFilter withKey(String key) {
        AdminAuthFilter f = new AdminAuthFilter();
        f.adminApiKey = Optional.ofNullable(key);
        return f;
    }

    @Test
    void isAdminPath_matchesWithOrWithoutLeadingSlash() {
        assertTrue(AdminAuthFilter.isAdminPath("api/v1/admin/scrape"));
        assertTrue(AdminAuthFilter.isAdminPath("/api/v1/admin/backfill-images"));
        assertFalse(AdminAuthFilter.isAdminPath("api/v1/deals"));
        assertFalse(AdminAuthFilter.isAdminPath("/api/v1/retailers"));
        assertFalse(AdminAuthFilter.isAdminPath(null));
    }

    @Test
    void nonAdminPath_alwaysAllowed_evenWithKeySet() {
        AdminAuthFilter f = withKey("secret");
        assertTrue(f.authorized("api/v1/deals", null));
        assertTrue(f.authorized("/api/v1/retailers", "anything"));
    }

    @Test
    void adminPath_failsOpenWhenNoKeyConfigured() {
        assertTrue(withKey("").authorized("api/v1/admin/scrape", null));
        assertTrue(withKey(null).authorized("api/v1/admin/scrape", null));
        assertTrue(withKey("   ").authorized("api/v1/admin/scrape", null));
    }

    @Test
    void adminPath_withKeyConfigured_requiresMatchingHeader() {
        AdminAuthFilter f = withKey("secret");
        assertFalse(f.authorized("api/v1/admin/scrape", null));     // missing
        assertFalse(f.authorized("api/v1/admin/scrape", "wrong"));  // wrong
        assertFalse(f.authorized("api/v1/admin/scrape", "Secret")); // case-sensitive
        assertTrue(f.authorized("api/v1/admin/scrape", "secret"));  // correct
    }

    @Test
    void keyComparisonIsLengthSensitive() {
        AdminAuthFilter f = withKey("secret");
        assertFalse(f.authorized("/api/v1/admin/aggregate-products", "secre"));
        assertFalse(f.authorized("/api/v1/admin/aggregate-products", "secrettail"));
    }
}

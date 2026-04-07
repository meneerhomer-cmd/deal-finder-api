package be.dealfinder.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class GraphQLCache {

    private static final Logger LOG = Logger.getLogger(GraphQLCache.class);
    private static final int MAX_ENTRIES = 200;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public record CacheEntry(String response, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            LOG.debug("Cache hit: " + key.substring(0, Math.min(key.length(), 40)));
            return entry.response();
        }
        if (entry != null) {
            cache.remove(key);
        }
        return null;
    }

    public void put(String key, String response, int ttlMinutes) {
        if (cache.size() >= MAX_ENTRIES) {
            cache.entrySet().removeIf(e -> e.getValue().isExpired());
            if (cache.size() >= MAX_ENTRIES) {
                cache.clear();
            }
        }
        cache.put(key, new CacheEntry(response, Instant.now().plusSeconds(ttlMinutes * 60L)));
    }

    public void clear() {
        cache.clear();
    }
}

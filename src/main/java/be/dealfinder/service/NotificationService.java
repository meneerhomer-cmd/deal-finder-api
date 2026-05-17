package be.dealfinder.service;

import be.dealfinder.entity.Deal;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Fan-out: given a set of newly-added deals, find users whose favorite brands
 * or watched products match, then push a notification via FCM to each of
 * their registered tokens.
 *
 * Iterates the top-level users collection. Cheap at <1000 users; if this
 * gets big, switch to a collection-group query over fcmTokens with denormalised
 * brand/product metadata on each token doc.
 */
@ApplicationScoped
public class NotificationService {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);
    private static final int MAX_DEALS_HIGHLIGHTED = 3;

    @Inject
    FcmService fcm;

    public void notifyForNewDeals(List<Deal> newDeals) {
        if (newDeals == null || newDeals.isEmpty()) return;
        if (FirebaseApp.getApps().isEmpty()) {
            LOG.info("NotificationService skipped: Firebase not initialised");
            return;
        }

        try {
            Firestore db = FirestoreClient.getFirestore();
            List<QueryDocumentSnapshot> users = db.collection("users").get().get().getDocuments();
            LOG.info("Notification fan-out: " + users.size() + " users × " + newDeals.size() + " new deals");

            int matched = 0;
            int sent = 0;
            for (QueryDocumentSnapshot userDoc : users) {
                List<Deal> userMatches = matchesForUser(db, userDoc.getId(), newDeals);
                if (userMatches.isEmpty()) continue;
                matched++;
                sent += sendToUser(db, userDoc.getId(), userMatches);
            }
            LOG.info("Notification fan-out done: " + matched + " users matched, " + sent + " pushes sent");
        } catch (Exception e) {
            LOG.error("Notification fan-out failed", e);
        }
    }

    private List<Deal> matchesForUser(Firestore db, String uid, List<Deal> newDeals) throws ExecutionException, InterruptedException {
        Set<String> favBrands = loadFavoriteBrands(db, uid);
        Set<String> watchedNamesLc = loadWatchedNamesLowercase(db, uid);

        return newDeals.stream()
                .filter(deal -> matches(deal, favBrands, watchedNamesLc))
                .toList();
    }

    private boolean matches(Deal deal, Set<String> favBrands, Set<String> watchedNamesLc) {
        if (deal.brand != null && favBrands.contains(deal.brand)) return true;
        if (deal.productName == null) return false;
        String nameLc = deal.productName.toLowerCase(Locale.ROOT);
        for (String watched : watchedNamesLc) {
            if (nameLc.contains(watched)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Set<String> loadFavoriteBrands(Firestore db, String uid) throws ExecutionException, InterruptedException {
        DocumentSnapshot snap = db.document("users/" + uid + "/preferences/brands").get().get();
        if (!snap.exists()) return Set.of();
        Object items = snap.get("items");
        if (items instanceof List<?> list) {
            Set<String> result = new HashSet<>();
            for (Object o : list) if (o instanceof String s) result.add(s);
            return result;
        }
        return Set.of();
    }

    private Set<String> loadWatchedNamesLowercase(Firestore db, String uid) throws ExecutionException, InterruptedException {
        List<QueryDocumentSnapshot> docs = db.collection("users/" + uid + "/watchlist").get().get().getDocuments();
        Set<String> names = new HashSet<>();
        for (QueryDocumentSnapshot d : docs) {
            String name = d.getString("productName");
            if (name == null) name = d.getId(); // fallback: doc ID is the product name
            names.add(name.toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private int sendToUser(Firestore db, String uid, List<Deal> matches) throws ExecutionException, InterruptedException {
        List<QueryDocumentSnapshot> tokens = db.collection("users/" + uid + "/fcmTokens").get().get().getDocuments();
        if (tokens.isEmpty()) return 0;

        Deal sample = matches.get(0);
        String title = matches.size() == 1
                ? "Nieuwe deal: " + sample.productName
                : matches.size() + " nieuwe deals voor jou";
        String body = buildBody(matches);
        Map<String, String> data = Map.of(
                "dealId", String.valueOf(sample.id),
                "type", matches.size() > 1 ? "multi" : "single"
        );

        int ok = 0;
        for (QueryDocumentSnapshot tokenDoc : tokens) {
            if (fcm.sendToToken(tokenDoc.getId(), title, body, data)) ok++;
        }
        return ok;
    }

    private String buildBody(List<Deal> matches) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(matches.size(), MAX_DEALS_HIGHLIGHTED);
        for (int i = 0; i < n; i++) {
            Deal d = matches.get(i);
            if (i > 0) sb.append(" · ");
            String retailerName = d.retailer != null ? d.retailer.name : "";
            sb.append(d.discountPercentage > 0 ? "-" + d.discountPercentage + "% " : "")
              .append(retailerName);
        }
        if (matches.size() > MAX_DEALS_HIGHLIGHTED) {
            sb.append(" +").append(matches.size() - MAX_DEALS_HIGHLIGHTED).append(" meer");
        }
        return sb.toString();
    }
}

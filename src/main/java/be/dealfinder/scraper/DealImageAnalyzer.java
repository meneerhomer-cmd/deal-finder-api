package be.dealfinder.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Analyzes deal flyer images using Claude Vision API to extract
 * structured deal information not available from the GraphQL API:
 * - Deal type (1+1 gratis, 2+2 gratis, 2e halve prijs, etc.)
 * - Quantity (4 stuks, 6 x 165g, per kg)
 * - Brand
 * - Conditions (naar keuze, loyalty card, etc.)
 */
@ApplicationScoped
public class DealImageAnalyzer {

    private static final Logger LOG = Logger.getLogger(DealImageAnalyzer.class);
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";

    @ConfigProperty(name = "deal.image.analysis.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "deal.image.analysis.api-key", defaultValue = "not-set")
    String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String ANALYSIS_PROMPT = """
        Analyze this Belgian supermarket promotional flyer image. Extract ONLY these fields as JSON:
        {
          "dealType": "exact promotional text, e.g. '1+1 gratis', '2+2 gratis naar keuze', '2e halve prijs', '3 voor €5', '-30%', or null if no deal visible",
          "quantity": "e.g. '4 stuks', '6 x 165g', '±1,2 kg', or null",
          "unitPrice": "e.g. '4.80/kg', 'per kilo', 'per liter', or null",
          "brand": "brand name if visible, or null",
          "conditions": "e.g. 'naar keuze', 'alle soorten', or null",
          "minPurchase": "minimum buy requirement, e.g. 'vanaf 2 stuks', 'per 3 kopen', or null",
          "variants": "e.g. 'alle soorten', 'verschillende smaken', 'alle kleuren', or null",
          "loyaltyCard": "loyalty card required? e.g. 'met Xtra-kaart', 'met klantenkaart', 'met Carrefour-kaart', or null if none needed",
          "department": "store department if mentioned, e.g. 'in de braadafdeling', 'vers', 'diepvries', or null",
          "validDays": "day restrictions if shown, e.g. 'van maandag tot woensdag', 'alleen dit weekend', or null"
        }
        Return ONLY the JSON, no other text.
        """;

    public Optional<DealImageInfo> analyze(String imageUrl) {
        if (!enabled || apiKey.equals("not-set")) {
            return Optional.empty();
        }

        try {
            byte[] imageBytes = downloadImage(imageUrl);
            String base64 = Base64.getEncoder().encodeToString(imageBytes);

            String requestBody = mapper.writeValueAsString(Map.of(
                    "model", "claude-haiku-4-5-20251001",
                    "max_tokens", 300,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "image", "source", Map.of(
                                            "type", "base64",
                                            "media_type", "image/jpeg",
                                            "data", base64
                                    )),
                                    Map.of("type", "text", "text", ANALYSIS_PROMPT)
                            )
                    ))
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CLAUDE_API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());

            String text = root.path("content").get(0).path("text").asText();
            // Extract JSON from response (handle potential markdown wrapping)
            if (text.contains("{")) {
                text = text.substring(text.indexOf("{"), text.lastIndexOf("}") + 1);
            }

            JsonNode info = mapper.readTree(text);
            return Optional.of(new DealImageInfo(
                    textOrNull(info, "dealType"),
                    textOrNull(info, "quantity"),
                    textOrNull(info, "unitPrice"),
                    textOrNull(info, "brand"),
                    textOrNull(info, "conditions"),
                    textOrNull(info, "minPurchase"),
                    textOrNull(info, "variants"),
                    textOrNull(info, "loyaltyCard"),
                    textOrNull(info, "department"),
                    textOrNull(info, "validDays")
            ));

        } catch (Exception e) {
            LOG.debug("Image analysis failed for " + imageUrl + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private byte[] downloadImage(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) return null;
        String text = value.asText();
        return "null".equals(text) ? null : text;
    }

    public record DealImageInfo(
            String dealType,
            String quantity,
            String unitPrice,
            String brand,
            String conditions,
            String minPurchase,
            String variants,
            String loyaltyCard,
            String department,
            String validDays
    ) {}
}

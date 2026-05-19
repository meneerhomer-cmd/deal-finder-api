package be.dealfinder.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls Anthropic Claude (Haiku 4.5) via tool-use API to extract canonical
 * product identity from a Belgian supermarket flyer image. Returns a
 * structured {@link ProductExtraction}.
 *
 * Replaces the older DealImageAnalyzer (chat-completion + hacky JSON parsing).
 * Uses prompt caching on the static system prompt + 10 few-shot examples
 * so each subsequent call within the cache TTL pays only for the new
 * image + listing context.
 */
@ApplicationScoped
public class ProductExtractor {

    private static final Logger LOG = Logger.getLogger(ProductExtractor.class);
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final int MAX_TOKENS = 1024;

    @ConfigProperty(name = "deal.product.extraction.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "deal.product.extraction.api-key", defaultValue = "not-set")
    String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode toolDefinition;
    private List<JsonNode> fewShotMessages;

    @PostConstruct
    void loadResources() {
        try {
            this.toolDefinition = mapper.readTree(readResource("/extract-product-tool-schema.json"));
            JsonNode examples = mapper.readTree(readResource("/few-shot-examples.json"));
            this.fewShotMessages = buildFewShotMessages(examples);
            LOG.infof("ProductExtractor loaded %d few-shot examples", examples.size());
        } catch (Exception e) {
            LOG.error("Failed to load ProductExtractor resources — extractor will be unusable", e);
            this.toolDefinition = null;
            this.fewShotMessages = List.of();
        }
    }

    /**
     * Extract product identity from a flyer image + listing context.
     * Returns Optional.empty() on any failure (network, parsing, schema).
     */
    public Optional<ProductExtraction> extract(String imageUrl, Listing listing) {
        if (!enabled || "not-set".equals(apiKey) || toolDefinition == null) {
            return Optional.empty();
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            ObjectNode request = mapper.createObjectNode();
            request.put("model", MODEL);
            request.put("max_tokens", MAX_TOKENS);

            ArrayNode tools = request.putArray("tools");
            tools.add(toolDefinition);

            ObjectNode toolChoice = request.putObject("tool_choice");
            toolChoice.put("type", "tool");
            toolChoice.put("name", "extract_product");

            ArrayNode messages = request.putArray("messages");
            for (JsonNode m : fewShotMessages) messages.add(m);
            messages.add(buildUserTurn(imageUrl, listing, /* cacheControl */ false));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(CLAUDE_API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("anthropic-beta", "prompt-caching-2024-07-31")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.errorf("Claude API returned %d: %s", response.statusCode(), response.body());
                return Optional.empty();
            }

            JsonNode root = mapper.readTree(response.body());
            logUsage(root.path("usage"));

            JsonNode toolUse = findToolUseBlock(root);
            if (toolUse == null) {
                LOG.errorf("Claude response missing tool_use block: %s", response.body());
                return Optional.empty();
            }

            JsonNode input = toolUse.path("input");
            return Optional.of(toProductExtraction(input));
        } catch (Exception e) {
            LOG.errorf(e, "ProductExtractor failed for imageUrl=%s", imageUrl);
            return Optional.empty();
        }
    }

    /** Listing context passed alongside the image. */
    public record Listing(String productName, String brand, String quantity,
                          String retailerSlug, String retailerName) {}

    // === Internal helpers ===

    private List<JsonNode> buildFewShotMessages(JsonNode examples) {
        List<JsonNode> out = new java.util.ArrayList<>();
        int last = examples.size() - 1;
        for (int i = 0; i < examples.size(); i++) {
            JsonNode ex = examples.get(i);
            JsonNode listing = ex.path("listing");
            Listing l = new Listing(
                    textOrNull(listing, "productName"),
                    textOrNull(listing, "brand"),
                    textOrNull(listing, "quantity"),
                    textOrNull(listing, "retailerSlug"),
                    textOrNull(listing, "retailerName"));
            // user turn — only the LAST example carries cache_control so the
            // entire system prompt + all 10 examples are cached as one block
            out.add(buildUserTurn(ex.path("imageUrl").asText(), l, i == last));
            // assistant turn — emits the tool_use directly
            out.add(buildAssistantToolUse(i, ex.path("expectedExtraction")));
        }
        return out;
    }

    private ObjectNode buildUserTurn(String imageUrl, Listing listing, boolean cacheControl) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "user");
        ArrayNode content = msg.putArray("content");

        ObjectNode imageBlock = content.addObject();
        imageBlock.put("type", "image");
        ObjectNode source = imageBlock.putObject("source");
        source.put("type", "url");
        source.put("url", imageUrl);

        ObjectNode textBlock = content.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", "Listing context:\n" +
                "  productName: " + nullToStr(listing.productName()) + "\n" +
                "  brand:       " + nullToStr(listing.brand()) + "\n" +
                "  quantity:    " + nullToStr(listing.quantity()) + "\n" +
                "  retailerSlug:" + nullToStr(listing.retailerSlug()) + "\n" +
                "  retailerName:" + nullToStr(listing.retailerName()));

        if (cacheControl) {
            ObjectNode cc = textBlock.putObject("cache_control");
            cc.put("type", "ephemeral");
        }
        return msg;
    }

    private ObjectNode buildAssistantToolUse(int index, JsonNode expectedExtraction) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "assistant");
        ArrayNode content = msg.putArray("content");

        ObjectNode toolUse = content.addObject();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_fewshot_" + index);
        toolUse.put("name", "extract_product");
        toolUse.set("input", expectedExtraction);
        return msg;
    }

    private JsonNode findToolUseBlock(JsonNode root) {
        JsonNode content = root.path("content");
        if (!content.isArray()) return null;
        for (JsonNode block : content) {
            if ("tool_use".equals(block.path("type").asText())) return block;
        }
        return null;
    }

    private ProductExtraction toProductExtraction(JsonNode input) {
        ProductExtraction.VolumeStructure vs = null;
        JsonNode vsNode = input.path("volumeStructure");
        if (vsNode.isObject() && !vsNode.isNull()) {
            vs = new ProductExtraction.VolumeStructure(
                    intOrNull(vsNode, "unitCount"),
                    doubleOrNull(vsNode, "amountPerUnit"),
                    textOrNull(vsNode, "baseUnit"));
        }

        ProductExtraction.FlyerUnitPrice fup = null;
        JsonNode fupNode = input.path("flyerUnitPrice");
        if (fupNode.isObject() && !fupNode.isNull()) {
            fup = new ProductExtraction.FlyerUnitPrice(
                    doubleOrNull(fupNode, "value"),
                    textOrNull(fupNode, "unit"));
        }

        JsonNode catNode = input.path("categoryAttributes");
        final Map<String, Object> catAttrs;
        if (catNode.isObject() && !catNode.isNull()) {
            Map<String, Object> tmp = new HashMap<>();
            catNode.fields().forEachRemaining(e -> tmp.put(e.getKey(), nodeToValue(e.getValue())));
            catAttrs = tmp;
        } else {
            catAttrs = null;
        }

        return new ProductExtraction(
                input.path("fingerprintable").asBoolean(false),
                input.path("confidence").asDouble(0.0),
                textOrNull(input, "ambiguityNotes"),
                textOrNull(input, "category"),
                textOrNull(input, "brand"),
                textOrNull(input, "productLine"),
                textOrNull(input, "variantFamily"),
                textOrNull(input, "style"),
                catAttrs,
                textOrNull(input, "trapDetected"),
                input.path("isPrivateLabel").asBoolean(false),
                vs,
                fup,
                textOrNull(input, "bundleType"),
                doubleOrNull(input, "bundleEffectiveDiscount"),
                input.toString()
        );
    }

    private void logUsage(JsonNode usage) {
        if (!usage.isObject()) return;
        int input = usage.path("input_tokens").asInt(0);
        int output = usage.path("output_tokens").asInt(0);
        int cacheCreate = usage.path("cache_creation_input_tokens").asInt(0);
        int cacheRead = usage.path("cache_read_input_tokens").asInt(0);
        LOG.infof("ProductExtractor tokens: in=%d out=%d cacheCreated=%d cacheRead=%d",
                input, output, cacheCreate, cacheRead);
    }

    private byte[] readResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Resource not found: " + path);
            return is.readAllBytes();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isNull() || v.isMissingNode()) return null;
        String s = v.asText();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isNull() || v.isMissingNode() || !v.canConvertToInt()) ? null : v.asInt();
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isNull() || v.isMissingNode() || !v.isNumber()) ? null : v.asDouble();
    }

    private static Object nodeToValue(JsonNode v) {
        if (v.isNull()) return null;
        if (v.isBoolean()) return v.asBoolean();
        if (v.isInt()) return v.asInt();
        if (v.isNumber()) return v.asDouble();
        return v.asText();
    }

    private static String nullToStr(String s) {
        return s == null ? "null" : s;
    }
}

package be.dealfinder.scraper;

import be.dealfinder.entity.Category;
import be.dealfinder.entity.Deal;
import be.dealfinder.entity.PriceHistory;
import be.dealfinder.entity.Retailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class MyShopScraper {

    private static final Logger LOG = Logger.getLogger(MyShopScraper.class);

    @ConfigProperty(name = "scraper.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "scraper.timeout", defaultValue = "15000")
    int timeout;

    @ConfigProperty(name = "scraper.request-delay", defaultValue = "2000")
    int requestDelay;

    @ConfigProperty(name = "scraper.user-agent")
    String userAgent;

    @ConfigProperty(name = "deals.minimum-discount", defaultValue = "20")
    int minimumDiscount;

    // Patterns for parsing
    private static final Pattern PRICE_PATTERN = Pattern.compile("€\\s*([\\d.,]+)");
    private static final Pattern DISCOUNT_PATTERN = Pattern.compile("(\\d+)%\\s*korting", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIMPLE_DISCOUNT_PATTERN = Pattern.compile("(\\d+)\\s*%\\s*(?:korting|goedkoper|voordeel|reduction|off|moins)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOLDER_URL_PATTERN = Pattern.compile("/nl/([\\w-]+)-folder/([a-f0-9-]+)(?:/\\d+)?");

    public ScraperResult scrapeRetailer(Retailer retailer) {
        if (!enabled) {
            LOG.info("Scraper is disabled, skipping " + retailer.name);
            return new ScraperResult(retailer.slug, 0, 0, "Scraper disabled");
        }

        LOG.info("Starting scrape for " + retailer.name + " from " + retailer.scrapingUrl);
        int added = 0;
        int updated = 0;
        List<Category> categories = Category.findAllActive();
        Set<String> processedOfferIds = new HashSet<>();

        try {
            // Step 1: Get landing page and find folder links
            Document landingPage = fetchDocument(retailer.scrapingUrl);
            LOG.info("Fetched landing page: " + landingPage.title());

            // Step 2: Find all unique folder URLs
            Set<String> folderBaseUrls = extractFolderUrls(landingPage, retailer);
            LOG.info("Found " + folderBaseUrls.size() + " folders for " + retailer.name);

            // Step 3: Also scrape offers directly visible on landing page
            int[] landingResults = scrapeOffersFromDocument(landingPage, retailer, categories, processedOfferIds);
            added += landingResults[0];
            updated += landingResults[1];
            LOG.info("Landing page: " + landingResults[0] + " added, " + landingResults[1] + " updated");

            // Step 4: For each folder, paginate through all pages
            for (String folderBaseUrl : folderBaseUrls) {
                try {
                    int[] folderResults = scrapeFolderPages(folderBaseUrl, retailer, categories, processedOfferIds);
                    added += folderResults[0];
                    updated += folderResults[1];
                } catch (Exception e) {
                    LOG.warn("Failed to scrape folder " + folderBaseUrl + ": " + e.getMessage());
                }
            }

            LOG.info("Completed scrape for " + retailer.name + ": " + added + " added, " + updated + " updated");
            return new ScraperResult(retailer.slug, added, updated, null);

        } catch (Exception e) {
            LOG.error("Failed to scrape " + retailer.name + ": " + e.getMessage(), e);
            return new ScraperResult(retailer.slug, added, updated, e.getMessage());
        }
    }

    /**
     * Extract unique folder base URLs from a page.
     * Folder URLs look like: /nl/lidl-folder/UUID/0
     */
    private Set<String> extractFolderUrls(Document doc, Retailer retailer) {
        Set<String> folderUrls = new HashSet<>();
        
        // Find links to folder pages
        Elements folderLinks = doc.select("a[href*='-folder/']");
        
        for (Element link : folderLinks) {
            String href = link.attr("href");

            if (href.contains("?offer=")) continue;

            // Only accept folders belonging to this retailer (skip "Vergelijkbare folders" from others)
            if (!href.contains(retailer.slug.toLowerCase() + "-folder")) continue;

            Matcher matcher = FOLDER_URL_PATTERN.matcher(href);
            if (matcher.find()) {
                String folderType = matcher.group(1);
                String folderUuid = matcher.group(2);
                String baseUrl = "https://www.myshopi.com/nl/" + folderType + "-folder/" + folderUuid;
                folderUrls.add(baseUrl);
            }
        }
        
        return folderUrls;
    }

    /**
     * Scrape all pages of a folder by incrementing page number until no new offers found.
     */
    private int[] scrapeFolderPages(String folderBaseUrl, Retailer retailer, 
                                     List<Category> categories, Set<String> processedOfferIds) throws Exception {
        int totalAdded = 0;
        int totalUpdated = 0;
        int page = 0;
        int emptyPages = 0;
        int maxEmptyPages = 3; // Stop after 3 consecutive pages with no offers
        int maxPages = 100; // Safety limit

        LOG.info("Scraping folder: " + folderBaseUrl);

        while (page < maxPages && emptyPages < maxEmptyPages) {
            String pageUrl = folderBaseUrl + "/" + page;
            
            try {
                Thread.sleep(requestDelay); // Be polite
                Document pageDoc = fetchDocument(pageUrl);
                
                int offersBefore = processedOfferIds.size();
                int[] results = scrapeOffersFromDocument(pageDoc, retailer, categories, processedOfferIds);
                totalAdded += results[0];
                totalUpdated += results[1];
                
                int newOffers = processedOfferIds.size() - offersBefore;
                
                if (newOffers == 0) {
                    emptyPages++;
                } else {
                    emptyPages = 0; // Reset counter when we find offers
                    LOG.debug("Page " + page + ": found " + newOffers + " new offers");
                }
                
                page++;
                
            } catch (Exception e) {
                LOG.debug("Page " + page + " failed or doesn't exist: " + e.getMessage());
                emptyPages++;
                page++;
            }
        }

        LOG.info("Folder complete: scraped " + page + " pages, " + totalAdded + " added, " + totalUpdated + " updated");
        return new int[]{totalAdded, totalUpdated};
    }

    /**
     * Scrape all offer links from a document.
     */
    private int[] scrapeOffersFromDocument(Document doc, Retailer retailer, 
                                            List<Category> categories, Set<String> processedOfferIds) {
        int added = 0;
        int updated = 0;

        Elements offerLinks = doc.select("a[href*='?offer=']");

        for (Element offerLink : offerLinks) {
            try {
                // Extract offer ID to avoid duplicates
                String offerId = extractOfferId(offerLink.attr("href"));
                if (offerId != null && processedOfferIds.contains(offerId)) {
                    continue; // Already processed
                }
                if (offerId != null) {
                    processedOfferIds.add(offerId);
                }

                Deal deal = parseOffer(offerLink, retailer, categories);
                if (deal == null) continue;
                
                if (deal.discountPercentage < minimumDiscount) continue;

                int[] result = saveDeal(deal);
                added += result[0];
                updated += result[1];
            } catch (Exception e) {
                LOG.trace("Failed to parse offer: " + e.getMessage());
            }
        }

        return new int[]{added, updated};
    }

    private Document fetchDocument(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeout)
                .get();
    }

    private String extractOfferId(String href) {
        Pattern pattern = Pattern.compile("offer=([a-f0-9-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(href);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Parse a myShopi offer link element.
     */
    private Deal parseOffer(Element element, Retailer retailer, List<Category> categories) {
        String fullText = element.text();

        // 1. Extract discount percentage
        Integer discountPercentage = parseDiscount(fullText);
        if (discountPercentage == null) {
            return null;
        }

        // 2. Extract product name from img alt attribute
        String productName = null;
        Elements images = element.select("img[alt*='Promotie:'], img[alt*='promotie:']");
        if (!images.isEmpty()) {
            productName = images.first().attr("alt")
                    .replaceFirst("(?i)^Promotie:\\s*", "")
                    .trim();
        }

        // Fallback: extract from text
        if (productName == null || productName.isEmpty()) {
            productName = fullText
                    .replaceAll("\\d+%\\s*korting", "")
                    .replaceAll("€\\s*[\\d.,]+", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        if (productName == null || productName.length() < 3) {
            return null;
        }

        // 3. Extract prices
        List<BigDecimal> prices = parsePrices(fullText);
        BigDecimal originalPrice = null;
        BigDecimal currentPrice = null;

        if (prices.size() >= 2) {
            originalPrice = prices.get(0);
            currentPrice = prices.get(1);
        } else if (prices.size() == 1) {
            currentPrice = prices.get(0);
            if (discountPercentage > 0 && discountPercentage < 100) {
                originalPrice = currentPrice.multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(100 - discountPercentage), 2, RoundingMode.HALF_UP);
            }
        }

        if (currentPrice == null) {
            return null;
        }

        // 4. Extract image URL
        String imageUrl = null;
        Elements productImages = element.select("img[src*='hotspots'], img[src*='cdn.jafolders']");
        if (!productImages.isEmpty()) {
            // Get the product image, not the logo
            for (Element img : productImages) {
                String src = img.attr("src");
                if (src.contains("hotspots")) {
                    imageUrl = src;
                    break;
                }
            }
        }
        if (imageUrl == null) {
            Elements allImages = element.select("img[src]");
            if (allImages.size() > 1) {
                imageUrl = allImages.last().attr("src");
            }
        }

        // 5. Source URL
        String sourceUrl = element.absUrl("href");
        if (sourceUrl.isEmpty()) {
            sourceUrl = "https://www.myshopi.com" + element.attr("href");
        }

        // 6. External ID
        String externalId = generateExternalId(element.attr("href"), retailer.slug);

        // 7. Validity
        LocalDate validUntil = LocalDate.now().plusDays(7);

        // 8. Create deal
        Deal deal = Deal.create(
                productName,
                retailer,
                currentPrice,
                originalPrice,
                discountPercentage,
                LocalDate.now(),
                validUntil,
                imageUrl,
                sourceUrl,
                externalId
        );

        // 9. Assign category (improved matching)
        deal.category = findBestCategory(productName, categories);

        return deal;
    }

    /**
     * Find the best matching category using word boundary matching.
     * Avoids false positives from short keywords matching inside longer words.
     */
    private Category findBestCategory(String productName, List<Category> categories) {
        String lowerProduct = productName.toLowerCase();
        
        // Split product name into words for matching
        String[] productWords = lowerProduct.split("[\\s\\-_/(),.]+");
        Set<String> productWordSet = new HashSet<>(Arrays.asList(productWords));
        
        Category bestMatch = null;
        int bestScore = 0;

        for (Category category : categories) {
            if (category.keywords == null || category.keywords.isBlank()) continue;

            String[] keywords = category.keywords.toLowerCase().split(",");
            int score = 0;

            for (String keyword : keywords) {
                String kw = keyword.trim();
                if (kw.length() < 3) continue; // Skip very short keywords (ui, ijs, etc.)
                
                // Check for whole word match (preferred)
                if (productWordSet.contains(kw)) {
                    score += 10;
                }
                // Check for substring match (only for longer keywords, 5+ chars)
                else if (kw.length() >= 5 && lowerProduct.contains(kw)) {
                    score += 5;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMatch = category;
            }
        }

        return bestMatch;
    }

    private Integer parseDiscount(String text) {
        if (text == null || text.isEmpty()) return null;

        Matcher matcher = DISCOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        matcher = SIMPLE_DISCOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return null;
    }

    private List<BigDecimal> parsePrices(String text) {
        List<BigDecimal> prices = new ArrayList<>();
        Matcher matcher = PRICE_PATTERN.matcher(text);

        while (matcher.find()) {
            try {
                String priceStr = matcher.group(1)
                        .replace(",", ".")
                        .replace(" ", "");
                // Handle thousands separator
                long dotCount = priceStr.chars().filter(ch -> ch == '.').count();
                if (dotCount > 1) {
                    // Multiple dots = thousands separators, keep only last as decimal
                    int lastDot = priceStr.lastIndexOf('.');
                    priceStr = priceStr.substring(0, lastDot).replace(".", "") + priceStr.substring(lastDot);
                }
                prices.add(new BigDecimal(priceStr));
            } catch (NumberFormatException e) {
                // Skip invalid prices
            }
        }

        return prices;
    }

    private String generateExternalId(String href, String retailerSlug) {
        String offerId = extractOfferId(href);
        if (offerId != null) {
            return retailerSlug + "-" + offerId;
        }
        return retailerSlug + "-" + href.hashCode();
    }

    @Transactional
    int[] saveDeal(Deal deal) {
        Deal existing = Deal.findByExternalId(deal.externalId);
        if (existing != null) {
            boolean priceChanged = existing.currentPrice != null && deal.currentPrice != null
                    && existing.currentPrice.compareTo(deal.currentPrice) != 0;
            existing.currentPrice = deal.currentPrice;
            existing.originalPrice = deal.originalPrice;
            existing.discountPercentage = deal.discountPercentage;
            existing.validUntil = deal.validUntil;
            existing.scrapedAt = LocalDateTime.now();
            existing.persist();
            if (priceChanged) {
                PriceHistory.create(existing).persist();
            }
            return new int[]{0, 1};
        } else {
            deal.persist();
            PriceHistory.create(deal).persist();
            LOG.debug("Added: " + deal.productName + " - " + deal.discountPercentage + "% off - €" + deal.currentPrice);
            return new int[]{1, 0};
        }
    }

    public record ScraperResult(String retailerSlug, int added, int updated, String error) {
        public boolean isSuccess() {
            return error == null || error.isBlank();
        }
    }
}

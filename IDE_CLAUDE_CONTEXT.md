# Belgian Deal Finder API - IDE Context Document

> **Last Updated**: January 2026
> **Status**: MVP Backend Complete, Frontend Pending

## Quick Start

```bash
cd deal-finder-api
mvn quarkus:dev                    # Start dev server
curl -X POST localhost:8080/api/v1/admin/scrape  # Trigger scrape
curl localhost:8080/api/v1/deals | jq            # View deals
# Swagger UI: http://localhost:8080/swagger-ui
```

---

## Project Overview

Mobile app backend aggregating multi-buy deals (20%+ discount) from Belgian retailers, scraped from myShopi.com.

### Target Retailers (6)
- Lidl, Kruidvat, Carrefour, Delhaize, ALDI, Colruyt

### Tech Stack
- **Framework**: Quarkus 3.17.0
- **Java**: 21
- **Database**: H2 (dev) / PostgreSQL (prod)
- **ORM**: Hibernate ORM with Panache
- **Scraping**: JSoup 1.17.2
- **API**: REST with Jackson
- **Docs**: OpenAPI / Swagger UI

---

## Project Structure

```
src/main/java/be/dealfinder/
├── dto/
│   ├── DealDTO.java              # API response (record)
│   ├── CategoryDTO.java
│   ├── RetailerDTO.java
│   └── ShoppingListItemDTO.java
├── entity/
│   ├── Deal.java                 # Main entity (Panache)
│   ├── Category.java             # 12 categories with keywords
│   ├── Retailer.java             # 6 Belgian retailers
│   ├── PriceHistory.java         # For future price trends
│   └── ShoppingListItem.java     # Session-based shopping list
├── resource/
│   ├── DealResource.java         # GET /api/v1/deals (filters, sort)
│   ├── RetailerResource.java     # GET /api/v1/retailers
│   ├── CategoryResource.java     # GET /api/v1/categories
│   ├── ShoppingListResource.java # Shopping list CRUD
│   └── AdminResource.java        # POST /api/v1/admin/scrape
├── scraper/
│   └── MyShopScraper.java        # JSoup-based HTML scraper
└── service/
    ├── DealService.java          # Business logic, queries
    ├── ScraperService.java       # Orchestrates scraping
    ├── ShoppingListService.java
    ├── DataInitService.java      # Seeds retailers & categories
    └── StartupService.java       # @Observes StartupEvent
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/deals` | All deals (with filters) |
| GET | `/api/v1/deals?retailer=lidl` | Filter by retailer |
| GET | `/api/v1/deals?category=drinks` | Filter by category |
| GET | `/api/v1/deals?minDiscount=50` | Filter by min discount |
| GET | `/api/v1/deals?search=coca` | Search product name |
| GET | `/api/v1/deals?sort=discount&order=desc` | Sort results |
| GET | `/api/v1/retailers` | All retailers |
| GET | `/api/v1/categories?lang=nl` | Categories (en/nl/fr) |
| POST | `/api/v1/admin/scrape` | Trigger full scrape |
| POST | `/api/v1/admin/scrape/{slug}` | Scrape specific retailer |
| GET | `/api/v1/admin/status` | Scraper status |
| GET | `/api/v1/admin/debug/{slug}` | Debug: what JSoup sees |

### Shopping List (requires X-Session-Id header)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/shopping-list` | Get list |
| POST | `/api/v1/shopping-list/{dealId}` | Add deal |
| DELETE | `/api/v1/shopping-list/{dealId}` | Remove deal |
| PATCH | `/api/v1/shopping-list/{dealId}/purchased` | Mark purchased |

---

## Known Issues & Limitations

### 1. Limited Deals Per Retailer (~8)

**Problem**: We only get deals shown on the landing page preview.

**Root Cause**: myShopi's folder pages (`/nl/lidl-folder/UUID/0`) use a **JavaScript-based PDF/image viewer**. The actual folder content is rendered client-side, so JSoup only sees an empty shell.

**Evidence from logs**:
```
Scraping folder: https://www.myshopi.com/nl/lidl-folder/UUID
Folder complete: scraped 3 pages, 0 added, 0 updated  # Always 0!
```

**Solutions to investigate**:
1. **Selenium/Playwright**: Use headless browser to render JavaScript
2. **"Toon meer promoties" button**: Landing page has a "show more" button that might load via AJAX
3. **Hidden API**: Check browser DevTools Network tab for XHR/fetch requests when browsing myShopi
4. **Alternative source**: Scrape retailer websites directly (Lidl.be, Carrefour.be, etc.)

### 2. Category Mismatches

**Problem**: Products sometimes get wrong categories (e.g., vacuum cleaner → "Fruits & Vegetables")

**Root Cause**: Short keywords like "ui" (onion in Dutch) match inside longer words.

**Current Fix**: 
- Skip keywords < 3 characters
- Whole word matches score higher (10 pts) than substring matches (5 pts)
- Substring matches only for keywords 5+ characters

**Location**: `MyShopScraper.findBestCategory()`

### 3. Transaction Timeout (FIXED)

**Problem**: Long-running scrapes caused `RollbackException: The transaction is not active!`

**Root Cause**: `@Transactional` method with Thread.sleep() exceeded transaction timeout.

**Fix Applied**: Removed `@Transactional` from `scrapeRetailer()`, added per-deal transaction in `saveDeal()`.

### 4. Wrong Retailer Folders (FIXED)

**Problem**: When scraping Lidl, it also scraped Delhaize folders.

**Root Cause**: Landing pages show "Vergelijkbare folders" (similar folders) from other retailers.

**Fix Applied**: Filter offers by checking URL contains `{retailerSlug}-folder`:
```java
if (!href.contains(retailer.slug.toLowerCase() + "-folder")) {
    continue; // Skip other retailers
}
```

---

## Scraper Architecture

### Current Flow (MyShopScraper.java)

```
1. Fetch retailer landing page (e.g., myshopi.com/nl/lidl/folder-aanbiedingen)
2. Select all <a href*="?offer="> elements
3. Filter: only URLs containing "{retailerSlug}-folder"
4. For each offer link:
   a. Extract discount from text ("25% korting")
   b. Extract product name from <img alt="Promotie: ...">
   c. Extract prices (€ XX,XX patterns)
   d. Extract image URL (hotspots images)
   e. Generate external ID from offer UUID
   f. Match category by keywords
   g. Save deal (in own transaction)
```

### HTML Structure (myShopi offer link)

```html
<a href="/nl/lidl-folder/UUID/59?offer=OFFER-UUID">
  <img src="logo.png"/>           <!-- Retailer logo -->
  25% korting                      <!-- Discount text -->
  <img alt="Promotie: Product Name" src="hotspots/xxx.webp"/>
  Product Name                     <!-- Product name (redundant) -->
  € 399,99€ 299,99                <!-- Original price, then current -->
</a>
```

### Key Selectors

| What | Selector |
|------|----------|
| Offer links | `a[href*='?offer=']` |
| Product image | `img[src*='hotspots']` |
| Product name | `img[alt*='Promotie:']` → extract from alt |

---

## Configuration (application.properties)

```properties
# Scraper
scraper.enabled=true
scraper.timeout=15000
scraper.user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...

# Deals
deals.minimum-discount=20        # Minimum % to include
deals.expired-visible-days=3     # Show expired deals for X days

# Database
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:file:./data/dealfinder;AUTO_SERVER=TRUE

# Dev profile: drop-and-create on each restart
%dev.quarkus.hibernate-orm.database.generation=drop-and-create
```

---

## Entity Relationships

```
Retailer (1) ←────── (N) Deal (N) ──────→ (1) Category
                           │
                           ↓
                    PriceHistory (for trends)
                    
ShoppingListItem ──→ Deal (by sessionId)
```

### Deal Entity Key Fields

```java
public class Deal extends PanacheEntity {
    public String productName;
    @ManyToOne public Retailer retailer;
    @ManyToOne public Category category;  // nullable
    public BigDecimal currentPrice;
    public BigDecimal originalPrice;
    public int discountPercentage;
    public LocalDate validFrom;
    public LocalDate validUntil;
    public String imageUrl;
    public String sourceUrl;
    public String externalId;  // unique, for deduplication
    public LocalDateTime scrapedAt;
}
```

---

## Testing Commands

```bash
# Start server
mvn quarkus:dev

# Trigger scrape
curl -X POST http://localhost:8080/api/v1/admin/scrape

# Get all deals
curl http://localhost:8080/api/v1/deals | jq

# Filter by retailer
curl "http://localhost:8080/api/v1/deals?retailer=lidl" | jq

# Filter by discount
curl "http://localhost:8080/api/v1/deals?minDiscount=50" | jq

# Search
curl "http://localhost:8080/api/v1/deals?search=coca-cola" | jq

# Debug scraper (see what JSoup receives)
curl http://localhost:8080/api/v1/admin/debug/lidl | jq

# Check scraper status
curl http://localhost:8080/api/v1/admin/status | jq
```

---

## Next Steps

### Priority 1: Get More Deals
The ~8 deals per retailer is the main limitation. Options:
1. Investigate myShopi's network requests for hidden API
2. Implement Selenium/Playwright for JavaScript rendering
3. Try scraping retailer sites directly

### Priority 2: Angular/Ionic Frontend
- Deal list with filters
- Shopping list functionality
- Multi-language support (EN/NL/FR)

### Priority 3: Production Deployment
- Switch to PostgreSQL
- Deploy to Railway/Render
- Set up scheduled scraping (cron)

---

## Developer Notes

- **Wim's preferences**: Practical working code, minimal boilerplate
- **Languages**: Java & Angular
- **Location**: Flemish Belgium (Dutch primary)
- **Work context**: Team lead at Telenet, backend development

### Code Style
- Use Java 21 features (records, pattern matching)
- Panache static finders for queries
- Records for DTOs
- Keep it simple for MVP

---

## Useful Links

- Swagger UI: http://localhost:8080/swagger-ui
- H2 Console: http://localhost:8080/h2-console (if enabled)
- myShopi Lidl: https://www.myshopi.com/nl/lidl/folder-aanbiedingen

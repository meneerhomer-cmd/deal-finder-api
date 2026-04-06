# CLAUDE.md

## Project Overview

Belgian Deal Finder API - A Quarkus REST API that scrapes multi-buy deals (20%+ discount) from Belgian retailers via myShopi.com. Aggregates deals from Lidl, Kruidvat, Carrefour, Delhaize, ALDI, and Colruyt.

## Tech Stack

- **Framework**: Quarkus 3.17
- **Java**: 21
- **Database**: H2 (dev) / PostgreSQL (prod)
- **ORM**: Hibernate ORM with Panache
- **Scraping**: JSoup 1.17.2

## Commands

```bash
./mvnw quarkus:dev                              # Dev server + hot reload (port 8080)
./mvnw test                                      # Run tests (none exist yet)
./mvnw package -Dquarkus.package.type=uber-jar   # Production uber-jar
curl -X POST localhost:8080/api/v1/admin/scrape   # Trigger scrape
curl localhost:8080/api/v1/admin/status            # Scraper status
curl localhost:8080/api/v1/admin/debug/lidl        # Debug: what JSoup sees
```

**Swagger UI**: http://localhost:8080/swagger-ui

## Architecture

```
src/main/java/be/dealfinder/
├── dto/           # Record-based API responses
├── entity/        # Panache entities (Deal, Retailer, Category, ShoppingListItem, PriceHistory)
├── resource/      # JAX-RS REST endpoints
├── scraper/       # MyShopScraper - JSoup HTML scraping logic (440 lines, core of the app)
└── service/       # Business logic (DealService, ScraperService, etc.)
```

**Entity relationships:**
```
Retailer (1) ←── (N) Deal (N) ──→ (1) Category
                      │
                      ↓
               PriceHistory

ShoppingListItem ──→ Deal (by sessionId)
```

**Scraping flow:** `AdminResource` → `ScraperService` → `MyShopScraper` → myShopi.com

## Known Bugs (Deep Scan April 2026)

| # | Severity | Location | Bug |
|---|----------|----------|-----|
| 1 | **High** | `MyShopScraper:201-214` | PriceHistory never created on deal updates — only on first insert |
| 2 | **High** | `MyShopScraper:50` | `@Transactional` wraps entire scrape with `Thread.sleep` loops — timeout risk |
| 3 | **High** | `MyShopScraper:102-125` | Folder URLs not filtered by retailer slug — scrapes other retailers' folders |
| 4 | **Medium** | `DealService` + `ShoppingListItem` | `cleanupOldDeals()` FK violation if shopping list references deleted deals |
| 5 | **Medium** | `DealDTO.from()` | N+1 queries — LAZY relationships loaded per-deal, no JOIN FETCH |
| 6 | **Medium** | `MyShopScraper:47` | Fallback discount pattern `(\d+)\s*%` too greedy — matches any "N%" |

## Known Limitations

1. **~8 deals per retailer**: myShopi folders use JS rendering. JSoup sees only landing page preview.
2. **Category matching**: Short Dutch keywords (<3 chars) skipped to avoid false positives ("ei", "ui", "ijs" never match).
3. **Validity hardcoded**: `validUntil = now + 7 days` — never parsed from actual page.
4. **No pagination** on deal endpoints.
5. **No auth** on admin endpoints.
6. **`DealService.findDeals()`** has fragile 8-branch if/else with manual positional parameters.

## Code Style

- Java 21 features (records, pattern matching)
- Panache static finders for queries
- Records for DTOs
- Keep it practical and minimal

## Decisions

| Date | Decision | Why |
|------|----------|-----|
| Jan 1, 2026 | myShopi.com as data source | Aggregates all Belgian retailers in one place |
| Jan 1, 2026 | JSoup over Selenium | Simpler, faster — but limits JS-rendered content |
| Jan 1, 2026 | H2 for dev, PostgreSQL for prod | Zero-config dev, real DB for prod |
| Jan 1, 2026 | Session-based shopping list (no auth) | MVP simplicity |

## TODO (Priority Order)

### Fix What's Broken (DONE — April 6, 2026)
- [x] Fix folder URL filter — checks retailer slug in `extractFolderUrls()`
- [x] Fix PriceHistory — creates record on deal price updates when price changed
- [x] Fix @Transactional — removed from `scrapeRetailer()`, per-deal `saveDeal()` method
- [x] Fix FK constraint — `deleteExpiredOlderThan()` now deletes shopping list items first
- [x] Fix N+1 — added `findWithRelations()` with JOIN FETCH
- [x] Align categories — 25 Dutch slugs matching frontend exactly
- [x] Replace `DealService.findDeals()` 8-branch if/else with dynamic param list
- [x] Fix greedy discount pattern — fallback now requires discount-related keywords

### Improve
- [ ] Add pagination to deal listing endpoints
- [ ] Improve scraper yield (myShopi hidden API? Playwright?)
- [ ] Add basic auth on admin endpoints
- [ ] Add PriceHistory API endpoint
- [ ] Add tests

### Future
- [ ] Production deployment (PostgreSQL, Flyway, Railway/Render)
- [ ] Firebase auth for persistent shopping lists
- [ ] Push notifications

## External Docs

Full project documentation: `~/Documents/Personal Projects/deal-finder/project.md`

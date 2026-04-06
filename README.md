# Belgian Deal Finder API

Quarkus REST API that scrapes multi-buy deals from Belgian retailers via myShopi.com. Backend for the [deal-finder-app](https://github.com/meneerhomer-cmd/deal-finder) Ionic/Angular frontend.

## Features

- **Deal aggregation** — scrapes 6 Belgian retailers from myShopi.com
- **Smart filtering** — by retailer, category, discount, search term
- **Sorting & pagination** — sort by discount/price/expiry/name, optional paging
- **25 Dutch categories** — vlees, vis, zuivel, dranken, etc. with tri-lingual names
- **Shopping list** — session-based CRUD with purchased tracking
- **Price history** — tracks price changes over time per product
- **Statistics** — deal counts, averages, top categories, expiring deals
- **Async scraping** — background scrape with status polling
- **Health checks** — readiness probe at `/q/health/ready`
- **Swagger UI** — interactive API docs

## Supported Retailers

| Retailer | Slug |
|----------|------|
| Lidl | `lidl` |
| Kruidvat | `kruidvat` |
| Carrefour | `carrefour` |
| Delhaize | `delhaize` |
| ALDI | `aldi` |
| Colruyt | `colruyt` |

## Tech Stack

- **Framework**: Quarkus 3.17
- **Language**: Java 21
- **Database**: H2 (dev) / PostgreSQL (prod)
- **ORM**: Hibernate ORM with Panache
- **Scraping**: JSoup 1.17.2
- **Tests**: REST-assured + JUnit 5 (24 tests)
- **API Docs**: SmallRye OpenAPI / Swagger UI

## Getting Started

```bash
# Prerequisites: Java 21+, Maven 3.9+
mvn quarkus:dev              # Dev server at http://localhost:8080
mvn test                     # Run 24 integration tests
mvn package -Dquarkus.package.type=uber-jar  # Production build
```

**Swagger UI**: http://localhost:8080/swagger-ui
**Health**: http://localhost:8080/q/health/ready

## API Endpoints

### Deals

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/deals` | All deals (filters: retailer, category, minDiscount, search, sort, order, lang, page, size) |
| GET | `/api/v1/deals/grouped` | Deals grouped by retailer |
| GET | `/api/v1/deals/{id}` | Single deal |
| GET | `/api/v1/deals/retailer/{slug}` | Deals for one retailer |
| GET | `/api/v1/deals/{id}/price-history` | Price history (last 90 days) |

Pagination: add `?page=0&size=20` for a `PagedResponse` with `items`, `totalItems`, `page`, `pageSize`, `totalPages`. Without `page`, returns a flat list (backward compatible).

### Retailers

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/retailers` | All retailers (includes `dealCount`) |
| GET | `/api/v1/retailers/{slug}` | Single retailer |

### Categories

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/categories` | All categories (`?lang=nl/fr/en`) |
| GET | `/api/v1/categories/{slug}` | Single category |

### Shopping List

All require `X-Session-Id` header.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/shopping-list` | Full list |
| POST | `/api/v1/shopping-list/{dealId}` | Add deal |
| DELETE | `/api/v1/shopping-list/{dealId}` | Remove deal |
| PATCH | `/api/v1/shopping-list/{dealId}/purchased` | Mark purchased |
| DELETE | `/api/v1/shopping-list/{dealId}/purchased` | Mark not purchased |
| DELETE | `/api/v1/shopping-list` | Clear all |
| GET | `/api/v1/shopping-list/count` | Item count |

### Statistics

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/stats` | Overview: totals, avg discount, best deal, expiring count, by-retailer, top categories |

### Admin

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/admin/scrape` | Trigger async scrape (returns 202) |
| POST | `/api/v1/admin/scrape/{slug}` | Scrape one retailer |
| GET | `/api/v1/admin/status` | Scraper status |
| GET | `/api/v1/admin/debug/{slug}` | Debug: raw HTML JSoup sees |

## Example Calls

```bash
curl "http://localhost:8080/api/v1/deals?retailer=lidl&minDiscount=50"
curl "http://localhost:8080/api/v1/deals?category=vlees&lang=nl"
curl "http://localhost:8080/api/v1/deals?page=0&size=10"
curl "http://localhost:8080/api/v1/stats"
curl "http://localhost:8080/api/v1/deals/1/price-history"
curl -X POST "http://localhost:8080/api/v1/admin/scrape"
curl "http://localhost:8080/api/v1/admin/status"
```

## Categories (25 Dutch slugs)

| Slug | Dutch | English |
|------|-------|---------|
| vlees | Vlees | Meat |
| charcuterie | Charcuterie | Charcuterie |
| vis | Vis & Zeevruchten | Fish & Seafood |
| zuivel | Zuivel | Dairy |
| kaas | Kaas | Cheese |
| dranken | Dranken | Drinks |
| bier | Bier | Beer |
| wijn | Wijn | Wine |
| snoep | Snoep | Snacks & Sweets |
| chips | Chips | Chips & Crisps |
| ontbijt | Ontbijt | Breakfast |
| brood | Brood | Bread & Bakery |
| diepvries | Diepvries | Frozen |
| conserven | Conserven | Canned |
| pasta | Pasta | Pasta & Rice |
| sauzen | Sauzen | Sauces |
| groenten | Groenten | Vegetables |
| fruit | Fruit | Fruit |
| kruiden | Kruiden | Herbs & Spices |
| huishouden | Huishouden | Household |
| schoonmaak | Schoonmaak | Cleaning |
| verzorging | Verzorging | Personal Care |
| baby | Baby | Baby |
| huisdier | Huisdier | Pets |
| andere | Andere | Other |

## License

MIT

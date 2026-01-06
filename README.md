# Belgian Deal Finder API

A Quarkus-based REST API for aggregating multi-buy deals (1+1 gratis, 2+1 gratis, etc.) from Belgian retailers.

## Features

- 🛒 **Deal Aggregation**: Scrapes deals from myShopi.com for 6 major Belgian retailers
- 🔍 **Smart Filtering**: Filter by retailer, category, minimum discount, and search terms
- 📊 **Sorting Options**: Sort by discount, price, expiry date, or name
- 🌍 **Multi-language**: Supports English, Dutch (Flemish), and French
- 📝 **Shopping List**: Save deals to a session-based shopping list
- 📈 **Price History**: Stores historical price data for future trend analysis
- ⏰ **Scheduled Scraping**: Daily automated scraping with Saturday priority
- 📖 **API Documentation**: Swagger UI for easy API exploration

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
- **API Docs**: SmallRye OpenAPI / Swagger UI

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+ (or use included wrapper)

### Run in Development Mode

```bash
./mvnw quarkus:dev
```

The API will be available at `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui`

### Build for Production

```bash
./mvnw package -Dquarkus.package.type=uber-jar
java -jar target/deal-finder-api-1.0.0-SNAPSHOT-runner.jar
```

## API Endpoints

### Deals

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/deals` | Get all deals with filters |
| GET | `/api/v1/deals/grouped` | Get deals grouped by retailer |
| GET | `/api/v1/deals/{id}` | Get deal by ID |
| GET | `/api/v1/deals/retailer/{slug}` | Get deals for specific retailer |

**Query Parameters for `/api/v1/deals`:**

| Parameter | Description | Example |
|-----------|-------------|---------|
| `retailer` | Filter by retailer slugs (comma-separated) | `lidl,kruidvat` |
| `category` | Filter by category slugs (comma-separated) | `meat,dairy` |
| `minDiscount` | Minimum discount percentage (20-100) | `50` |
| `search` | Search by product name | `coca-cola` |
| `sort` | Sort by: discount, price, expiry, name | `discount` |
| `order` | Sort order: asc, desc | `desc` |
| `lang` | Language: en, nl, fr | `nl` |

### Retailers

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/retailers` | Get all active retailers |
| GET | `/api/v1/retailers/{slug}` | Get retailer by slug |

### Categories

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/categories` | Get all categories |
| GET | `/api/v1/categories/{slug}` | Get category by slug |

### Shopping List

Requires `X-Session-Id` header for all requests.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/shopping-list` | Get shopping list |
| GET | `/api/v1/shopping-list/active` | Get active items |
| GET | `/api/v1/shopping-list/purchased` | Get purchased items |
| POST | `/api/v1/shopping-list/{dealId}` | Add deal to list |
| DELETE | `/api/v1/shopping-list/{dealId}` | Remove deal from list |
| PATCH | `/api/v1/shopping-list/{dealId}/purchased` | Mark as purchased |
| DELETE | `/api/v1/shopping-list/{dealId}/purchased` | Mark as not purchased |
| DELETE | `/api/v1/shopping-list` | Clear entire list |
| GET | `/api/v1/shopping-list/count` | Get item count |

### Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/admin/scrape` | Trigger full scrape |
| POST | `/api/v1/admin/scrape/{retailerSlug}` | Scrape specific retailer |
| GET | `/api/v1/admin/status` | Get scraper status |

## Example API Calls

```bash
# Get all deals with 50%+ discount from Lidl
curl "http://localhost:8080/api/v1/deals?retailer=lidl&minDiscount=50"

# Get meat deals in Dutch
curl "http://localhost:8080/api/v1/deals?category=meat&lang=nl"

# Search for Coca-Cola deals
curl "http://localhost:8080/api/v1/deals?search=coca-cola"

# Add to shopping list
curl -X POST "http://localhost:8080/api/v1/shopping-list/123" \
  -H "X-Session-Id: my-device-id"

# Trigger a scrape
curl -X POST "http://localhost:8080/api/v1/admin/scrape"
```

## Configuration

Key configuration in `application.properties`:

```properties
# Scraping
scraper.enabled=true
scraper.timeout=15000
scraper.request-delay=2000
scraper.schedule.cron=0 0 6 * * ?

# Deals
deals.minimum-discount=20
deals.expired-visible-days=3

# Database (production)
%prod.quarkus.datasource.db-kind=postgresql
%prod.quarkus.datasource.jdbc.url=${DATABASE_URL}
```

## Categories

| Slug | English | Dutch | French |
|------|---------|-------|--------|
| meat | Meat | Vlees | Viande |
| fish | Fish & Seafood | Vis & Zeevruchten | Poisson & Fruits de mer |
| dairy | Dairy | Zuivel | Produits laitiers |
| drinks | Drinks | Dranken | Boissons |
| household | Household | Huishouden | Ménage |
| personal-care | Personal Care | Verzorging | Soins personnels |
| baby | Baby | Baby | Bébé |
| snacks | Snacks & Sweets | Snacks & Snoep | Snacks & Bonbons |
| frozen | Frozen | Diepvries | Surgelés |
| bakery | Bread & Bakery | Brood & Gebak | Pain & Pâtisserie |
| fruits-vegetables | Fruits & Vegetables | Groenten & Fruit | Fruits & Légumes |
| pets | Pets | Huisdieren | Animaux |

## Future Roadmap

- [ ] Firebase Authentication for user accounts
- [ ] Push notifications for favorite deals (Firebase Cloud Messaging)
- [ ] Price history visualization
- [ ] Angular/Ionic mobile frontend

## License

MIT

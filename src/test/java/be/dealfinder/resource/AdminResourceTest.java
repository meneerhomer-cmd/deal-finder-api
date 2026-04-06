package be.dealfinder.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AdminResourceTest {

    @Test
    void getStatus_returnsScraperStatus() {
        given()
            .when().get("/api/v1/admin/status")
            .then()
                .statusCode(200)
                .body("enabled", is(true))
                .body("running", isA(Boolean.class));
    }

    @Test
    void triggerScrape_returns202Accepted() {
        given()
            .when().post("/api/v1/admin/scrape")
            .then()
                .statusCode(anyOf(is(202), is(409)));
    }

    @Test
    void scrapeRetailer_notFound_returns400() {
        given()
            .when().post("/api/v1/admin/scrape/nonexistent")
            .then()
                .statusCode(400);
    }
}

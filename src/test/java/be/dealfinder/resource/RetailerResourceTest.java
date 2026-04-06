package be.dealfinder.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class RetailerResourceTest {

    @Test
    void getRetailers_returnsSixRetailers() {
        given()
            .when().get("/api/v1/retailers")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$.size()", greaterThanOrEqualTo(6))
                .body("slug", hasItems("lidl", "kruidvat", "carrefour", "delhaize", "aldi", "colruyt"))
                .body("dealCount", everyItem(greaterThanOrEqualTo(0)));
    }

    @Test
    void getRetailerBySlug_found_returnsRetailer() {
        given()
            .when().get("/api/v1/retailers/lidl")
            .then()
                .statusCode(200)
                .body("name", is("Lidl"))
                .body("slug", is("lidl"))
                .body("dealCount", greaterThanOrEqualTo(0));
    }

    @Test
    void getRetailerBySlug_notFound_returns404() {
        given()
            .when().get("/api/v1/retailers/nonexistent")
            .then()
                .statusCode(404);
    }
}

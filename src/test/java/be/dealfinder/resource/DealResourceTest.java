package be.dealfinder.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class DealResourceTest {

    @Test
    void getDeals_returnsListAndOk() {
        given()
            .when().get("/api/v1/deals")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", instanceOf(java.util.List.class));
    }

    @Test
    void getDeals_withRetailerFilter_returnsOk() {
        given()
            .queryParam("retailer", "lidl")
            .when().get("/api/v1/deals")
            .then()
                .statusCode(200);
    }

    @Test
    void getDeals_withCategoryFilter_returnsOk() {
        given()
            .queryParam("category", "vlees")
            .when().get("/api/v1/deals")
            .then()
                .statusCode(200);
    }

    @Test
    void getDeals_withSearchFilter_returnsOk() {
        given()
            .queryParam("search", "coca-cola")
            .when().get("/api/v1/deals")
            .then()
                .statusCode(200);
    }

    @Test
    void getDeals_withPagination_returnsPagedResponse() {
        given()
            .queryParam("page", 0)
            .queryParam("size", 5)
            .when().get("/api/v1/deals")
            .then()
                .statusCode(200)
                .body("page", is(0))
                .body("pageSize", is(5))
                .body("totalItems", greaterThanOrEqualTo(0))
                .body("items", instanceOf(java.util.List.class));
    }

    @Test
    void getDeals_withSorting_returnsOk() {
        given()
            .queryParam("sort", "price")
            .queryParam("order", "asc")
            .when().get("/api/v1/deals")
            .then()
                .statusCode(200);
    }

    @Test
    void getDealById_notFound_returns404() {
        given()
            .when().get("/api/v1/deals/999999")
            .then()
                .statusCode(404);
    }

    @Test
    void getDealsByRetailer_returnsOk() {
        given()
            .when().get("/api/v1/deals/retailer/lidl")
            .then()
                .statusCode(200)
                .body("$", instanceOf(java.util.List.class));
    }

    @Test
    void getDealsGrouped_returnsMap() {
        given()
            .when().get("/api/v1/deals/grouped")
            .then()
                .statusCode(200)
                .contentType("application/json");
    }

    @Test
    void getPriceHistory_notFound_returns404() {
        given()
            .when().get("/api/v1/deals/999999/price-history")
            .then()
                .statusCode(404);
    }
}

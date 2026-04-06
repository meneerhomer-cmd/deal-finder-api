package be.dealfinder.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CategoryResourceTest {

    @Test
    void getCategories_returnsDutchCategories() {
        given()
            .when().get("/api/v1/categories")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$.size()", greaterThanOrEqualTo(25))
                .body("slug", hasItems("vlees", "vis", "zuivel", "dranken", "huishouden"));
    }

    @Test
    void getCategories_withDutchLang_returnsNlNames() {
        given()
            .queryParam("lang", "nl")
            .when().get("/api/v1/categories")
            .then()
                .statusCode(200)
                .body("name", hasItem("Vlees"));
    }

    @Test
    void getCategoryBySlug_found_returnsCategory() {
        given()
            .when().get("/api/v1/categories/vlees")
            .then()
                .statusCode(200)
                .body("slug", is("vlees"))
                .body("nameNl", is("Vlees"));
    }

    @Test
    void getCategoryBySlug_notFound_returns404() {
        given()
            .when().get("/api/v1/categories/nonexistent")
            .then()
                .statusCode(404);
    }
}

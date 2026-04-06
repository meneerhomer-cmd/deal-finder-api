package be.dealfinder.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ShoppingListResourceTest {

    private final String sessionId = "test-session-" + UUID.randomUUID();

    @Test
    void getShoppingList_emptySession_returnsEmptyList() {
        given()
            .header("X-Session-Id", sessionId)
            .when().get("/api/v1/shopping-list")
            .then()
                .statusCode(200)
                .body("$.size()", is(0));
    }

    @Test
    void getShoppingList_noSessionHeader_returns400() {
        given()
            .when().get("/api/v1/shopping-list")
            .then()
                .statusCode(400);
    }

    @Test
    void getCount_emptySession_returnsZero() {
        given()
            .header("X-Session-Id", sessionId)
            .when().get("/api/v1/shopping-list/count")
            .then()
                .statusCode(200)
                .body("count", is(0));
    }

    @Test
    void addDeal_nonexistentDeal_returns404() {
        given()
            .header("X-Session-Id", sessionId)
            .when().post("/api/v1/shopping-list/999999")
            .then()
                .statusCode(404);
    }
}

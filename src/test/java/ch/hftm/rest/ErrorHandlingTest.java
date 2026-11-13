package ch.hftm.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static ch.hftm.security.TestUsers.BOB;
import static ch.hftm.security.TestUsers.BOB_ID;
import ch.hftm.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * The catch-all {@code UnexpectedExceptionMapper} must only turn real failures into a 500.
 * Client errors that Quarkus already answers keep their status.
 */
@QuarkusTest
class ErrorHandlingTest {

    @Test
    void unknownPathStaysNotFound() {
        given().when().get("/does-not-exist").then().statusCode(404);
    }

    @Test
    void unsupportedMethodStaysMethodNotAllowed() {
        given().contentType(ContentType.JSON).body("{}")
                .when().patch("/blogs/1")
                .then().statusCode(405);
    }

    @Test
    @TestSecurity(user = BOB, roles = {Roles.READER, Roles.AUTHOR})
    @OidcSecurity(claims = @Claim(key = "sub", value = BOB_ID))
    void unreadableJsonStaysBadRequest() {
        given().contentType(ContentType.JSON).body("{\"title\": ")
                .when().post("/blogs")
                .then().statusCode(400)
                .body("message", is("Request body is not valid JSON"));
    }

    @Test
    void healthReportsDatabaseAsReady() {
        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", is("UP"))
                .body("checks.name", hasItem("Database connections health check"));
    }
}

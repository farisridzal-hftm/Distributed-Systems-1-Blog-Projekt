package ch.hftm.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import ch.hftm.rest.dto.BlogRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Checks the CORS rules from application.properties the way a browser would exercise them. */
@QuarkusTest
class CorsTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

    @Test
    void preflightFromAllowedOriginIsAccepted() {
        given()
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Content-Type")
                .when().options("/blogs/1")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", is(ALLOWED_ORIGIN))
                .header("Access-Control-Allow-Methods", containsString("PUT"))
                .header("Access-Control-Allow-Headers", containsString("Content-Type"));
    }

    @Test
    void createdLocationIsExposedToAllowedOrigin() {
        given()
                .header("Origin", ALLOWED_ORIGIN)
                .contentType(ContentType.JSON)
                .body(new BlogRequest("CORS Blog", "Created from a browser frontend"))
                .when().post("/blogs")
                .then()
                .statusCode(201)
                .header("Access-Control-Allow-Origin", is(ALLOWED_ORIGIN))
                .header("Access-Control-Expose-Headers", containsString("Location"));
    }

    @Test
    void requestFromUnknownOriginIsRejected() {
        given()
                .header("Origin", "https://evil.example")
                .when().get("/blogs")
                .then()
                .statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue());
    }
}

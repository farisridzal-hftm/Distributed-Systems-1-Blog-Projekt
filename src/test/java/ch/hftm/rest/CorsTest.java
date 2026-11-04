package ch.hftm.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static ch.hftm.security.TestUsers.BOB;
import static ch.hftm.security.TestUsers.BOB_ID;
import ch.hftm.rest.dto.BlogRequest;
import ch.hftm.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/** Checks the CORS rules from application.properties the way a browser would exercise them. */
@QuarkusTest
class CorsTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

    @Test
    void preflightFromAllowedOriginIsAccepted() {
        // The preflight carries no token, so it must pass although PUT itself is protected.
        given()
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Content-Type, Authorization")
                .when().options("/blogs/1")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", is(ALLOWED_ORIGIN))
                .header("Access-Control-Allow-Methods", containsString("PUT"))
                .header("Access-Control-Allow-Headers", containsString("Content-Type"))
                .header("Access-Control-Allow-Headers", containsString("Authorization"));
    }

    @Test
    @TestSecurity(user = BOB, roles = {Roles.READER, Roles.AUTHOR})
    @OidcSecurity(claims = @Claim(key = "sub", value = BOB_ID))
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

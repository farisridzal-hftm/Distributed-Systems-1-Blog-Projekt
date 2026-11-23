package ch.hftm;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Blackbox test against the built artifact. With {@code -Dquarkus.container-image.build=true} that
 * is the container image: Quarkus starts it next to the Dev Services (MySQL, Keycloak) and the test
 * only talks HTTP to it, without access to beans or the database.
 */
@QuarkusIntegrationTest
class BlogApiIT {

    @Test
    void startsHealthy() {
        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void publicReadingWorksWithSeedData() {
        given().when().get("/blogs")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))
                .body("[0].author", is("bob"))
                .body("[0].createdAt", notNullValue());
    }

    @Test
    void writingWithoutTokenIsRejected() {
        given().contentType(ContentType.JSON).body("{\"title\": \"Blackbox\", \"content\": \"No token\"}")
                .when().post("/blogs")
                .then().statusCode(401);
    }

    @Test
    void openApiMarksProtectedOperations() {
        given().queryParam("format", "json")
                .when().get("/q/openapi")
                .then().statusCode(200)
                .body("paths.'/blogs'.post.security", notNullValue());
    }
}

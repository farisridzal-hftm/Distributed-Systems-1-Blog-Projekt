package ch.hftm.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static ch.hftm.security.TestUsers.ADMIN;
import static ch.hftm.security.TestUsers.ALICE;
import static ch.hftm.security.TestUsers.BOB;
import static ch.hftm.security.TestUsers.BOB_ID;
import static ch.hftm.security.TestUsers.CAROL;
import ch.hftm.repository.BlogRepository;
import ch.hftm.rest.dto.BlogRequest;
import ch.hftm.rest.dto.CommentRequest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check with real access tokens. The tokens come from the Keycloak that Dev Services
 * start with keycloak/blog-realm.json, so this test proves that the realm file, the role claim
 * {@code realm_access.roles} and the {@code sub} claim work together with the API.
 */
@QuarkusTest
class KeycloakTokenTest {

    private final KeycloakTestClient keycloak = new KeycloakTestClient();

    @Inject
    BlogRepository blogRepository;

    /** Password grant with the test user's password, which equals the user name. */
    private String tokenOf(String user) {
        return keycloak.getAccessToken(user);
    }

    private Long createBlog(String token) {
        return given().auth().oauth2(token)
                .contentType(ContentType.JSON).body(new BlogRequest("Real Token Blog", "Created with a Keycloak token"))
                .when().post("/blogs")
                .then().statusCode(201).body("author", is(BOB))
                .extract().jsonPath().getLong("id");
    }

    @Test
    void authorTokenCreatesBlogOwnedByTheTokenSubject() {
        Long id = createBlog(tokenOf(BOB));

        String authorId = QuarkusTransaction.requiringNew().call(() -> blogRepository.findById(id).getAuthorId());
        assertEquals(BOB_ID, authorId);
    }

    @Test
    void ownershipIsCheckedAcrossRealUsers() {
        Long id = createBlog(tokenOf(BOB));

        given().auth().oauth2(tokenOf(CAROL))
                .contentType(ContentType.JSON).body(new BlogRequest("Hijacked", "Carol was here"))
                .when().put("/blogs/{id}", id)
                .then().statusCode(403);

        given().auth().oauth2(tokenOf(ADMIN))
                .when().delete("/blogs/{id}", id)
                .then().statusCode(204);
    }

    @Test
    void readerTokenMayCommentButNotWriteBlogs() {
        Long id = createBlog(tokenOf(BOB));
        String alice = tokenOf(ALICE);

        given().auth().oauth2(alice)
                .contentType(ContentType.JSON).body(new BlogRequest("Not allowed", "Alice is no author"))
                .when().post("/blogs")
                .then().statusCode(403);

        given().auth().oauth2(alice)
                .contentType(ContentType.JSON).body(new CommentRequest("Comment with a real token"))
                .when().post("/blogs/{id}/comments", id)
                .then().statusCode(201).body("author", is(ALICE));
    }

    @Test
    void invalidTokenIsRejected() {
        given().auth().oauth2("not-a-valid-token")
                .contentType(ContentType.JSON).body(new BlogRequest("Invalid", "Token is garbage"))
                .when().post("/blogs")
                .then().statusCode(401);
    }
}

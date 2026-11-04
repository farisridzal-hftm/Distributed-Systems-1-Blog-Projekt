package ch.hftm.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static ch.hftm.security.TestUsers.ADMIN;
import static ch.hftm.security.TestUsers.ADMIN_ID;
import static ch.hftm.security.TestUsers.ALICE;
import static ch.hftm.security.TestUsers.ALICE_ID;
import static ch.hftm.security.TestUsers.BOB;
import static ch.hftm.security.TestUsers.BOB_ID;
import static ch.hftm.security.TestUsers.CAROL;
import static ch.hftm.security.TestUsers.CAROL_ID;
import ch.hftm.model.Blog;
import ch.hftm.repository.BlogRepository;
import ch.hftm.rest.dto.BlogRequest;
import ch.hftm.rest.dto.CommentRequest;
import ch.hftm.security.Roles;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * The access rules from the authorization concept in the README, row by row. {@code @TestSecurity}
 * simulates the logged-in user and its roles, {@code @OidcSecurity} adds the {@code sub} claim
 * that decides ownership. Every test starts from a blog written by bob.
 */
@QuarkusTest
class BlogSecurityTest {

    @Inject
    BlogRepository blogRepository;

    private Long blogOfBob() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Blog blog = new Blog("Bob's Blog", "Written by bob");
            blog.setAuthor(BOB_ID, BOB);
            blogRepository.persist(blog);
            return blog.getId();
        });
    }

    private Blog loadBlog(Long id) {
        return QuarkusTransaction.requiringNew().call(() -> blogRepository.findById(id));
    }

    private static BlogRequest someBlog() {
        return new BlogRequest("Security Test Blog", "Some content");
    }

    // --- anonymous -------------------------------------------------------

    @Test
    void anonymousMayRead() {
        Long id = blogOfBob();

        given().when().get("/blogs").then().statusCode(200);
        given().when().get("/blogs/{id}", id).then().statusCode(200).body("author", is(BOB));
        given().when().get("/blogs/{id}/comments", id).then().statusCode(200);
    }

    @Test
    void anonymousMustLogInToWrite() {
        Long id = blogOfBob();

        given().contentType(ContentType.JSON).body(someBlog())
                .when().post("/blogs")
                .then().statusCode(401).header("WWW-Authenticate", containsString("Bearer"));
        given().contentType(ContentType.JSON).body(someBlog())
                .when().put("/blogs/{id}", id)
                .then().statusCode(401);
        given()
                .when().delete("/blogs/{id}", id)
                .then().statusCode(401);
        given().contentType(ContentType.JSON).body(new CommentRequest("Anonymous comment"))
                .when().post("/blogs/{id}/comments", id)
                .then().statusCode(401);
    }

    // --- reader ----------------------------------------------------------

    @Test
    @TestSecurity(user = ALICE, roles = Roles.READER)
    @OidcSecurity(claims = @Claim(key = "sub", value = ALICE_ID))
    void readerMayComment() {
        Long id = blogOfBob();

        given().contentType(ContentType.JSON).body(new CommentRequest("Comment by alice"))
                .when().post("/blogs/{id}/comments", id)
                .then().statusCode(201).body("author", is(ALICE));
    }

    @Test
    @TestSecurity(user = ALICE, roles = Roles.READER)
    @OidcSecurity(claims = @Claim(key = "sub", value = ALICE_ID))
    void readerMayNotWriteBlogs() {
        Long id = blogOfBob();

        given().contentType(ContentType.JSON).body(someBlog())
                .when().post("/blogs")
                .then().statusCode(403)
                .body("status", is(403))
                .body("error", is("Forbidden"));
        given().contentType(ContentType.JSON).body(someBlog())
                .when().put("/blogs/{id}", id)
                .then().statusCode(403);
        given()
                .when().delete("/blogs/{id}", id)
                .then().statusCode(403);
    }

    // --- author ----------------------------------------------------------

    @Test
    @TestSecurity(user = BOB, roles = {Roles.READER, Roles.AUTHOR})
    @OidcSecurity(claims = @Claim(key = "sub", value = BOB_ID))
    void authorCreatesBlogAndBecomesItsAuthor() {
        Long id = given().contentType(ContentType.JSON).body(someBlog())
                .when().post("/blogs")
                .then().statusCode(201).body("author", is(BOB))
                .extract().jsonPath().getLong("id");

        assertEquals(BOB_ID, loadBlog(id).getAuthorId());
    }

    @Test
    @TestSecurity(user = BOB, roles = {Roles.READER, Roles.AUTHOR})
    @OidcSecurity(claims = @Claim(key = "sub", value = BOB_ID))
    void authorInRequestBodyIsIgnored() {
        // BlogRequest has no author field: nobody can publish in someone else's name.
        given().contentType(ContentType.JSON)
                .body("{\"title\": \"Fake\", \"content\": \"Pretends to be carol\", \"author\": \"carol\"}")
                .when().post("/blogs")
                .then().statusCode(201).body("author", is(BOB));
    }

    @Test
    @TestSecurity(user = BOB, roles = {Roles.READER, Roles.AUTHOR})
    @OidcSecurity(claims = @Claim(key = "sub", value = BOB_ID))
    void authorMayChangeOwnBlog() {
        Long id = blogOfBob();

        given().contentType(ContentType.JSON).body(new BlogRequest("Changed by bob", "New content"))
                .when().put("/blogs/{id}", id)
                .then().statusCode(200).body("title", is("Changed by bob"));
    }

    @Test
    @TestSecurity(user = BOB, roles = {Roles.READER, Roles.AUTHOR})
    @OidcSecurity(claims = @Claim(key = "sub", value = BOB_ID))
    void authorMayDeleteOwnBlog() {
        Long id = blogOfBob();

        given().when().delete("/blogs/{id}", id).then().statusCode(204);
    }

    // --- another author --------------------------------------------------

    @Test
    @TestSecurity(user = CAROL, roles = {Roles.READER, Roles.AUTHOR})
    @OidcSecurity(claims = @Claim(key = "sub", value = CAROL_ID))
    void authorMayNotChangeSomeoneElsesBlog() {
        Long id = blogOfBob();

        given().contentType(ContentType.JSON).body(new BlogRequest("Hijacked", "Carol was here"))
                .when().put("/blogs/{id}", id)
                .then().statusCode(403)
                .body("message", is("Only the author may change blog " + id));

        assertEquals("Bob's Blog", loadBlog(id).getTitle());
    }

    @Test
    @TestSecurity(user = CAROL, roles = {Roles.READER, Roles.AUTHOR})
    @OidcSecurity(claims = @Claim(key = "sub", value = CAROL_ID))
    void authorMayNotDeleteSomeoneElsesBlog() {
        Long id = blogOfBob();

        given().when().delete("/blogs/{id}", id)
                .then().statusCode(403)
                .body("message", is("Only the author or an admin may delete blog " + id));

        given().when().get("/blogs/{id}", id).then().statusCode(200);
    }

    // --- admin -----------------------------------------------------------

    @Test
    @TestSecurity(user = ADMIN, roles = {Roles.READER, Roles.ADMIN})
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void adminMayDeleteAnyBlog() {
        Long id = blogOfBob();

        given().when().delete("/blogs/{id}", id).then().statusCode(204);
        given().when().get("/blogs/{id}", id).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = ADMIN, roles = {Roles.READER, Roles.ADMIN})
    @OidcSecurity(claims = @Claim(key = "sub", value = ADMIN_ID))
    void adminMayNeitherChangeNorCreateBlogs() {
        Long id = blogOfBob();

        given().contentType(ContentType.JSON).body(new BlogRequest("Moderated", "Rewritten by admin"))
                .when().put("/blogs/{id}", id)
                .then().statusCode(403);
        given().contentType(ContentType.JSON).body(someBlog())
                .when().post("/blogs")
                .then().statusCode(403);
    }
}

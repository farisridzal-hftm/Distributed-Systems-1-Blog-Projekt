package ch.hftm.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static ch.hftm.security.TestUsers.BOB;
import static ch.hftm.security.TestUsers.BOB_ID;
import ch.hftm.rest.dto.BlogRequest;
import ch.hftm.rest.dto.BlogResponse;
import ch.hftm.rest.dto.CommentRequest;
import ch.hftm.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.junit.jupiter.api.Test;

/**
 * REST behaviour of the blog endpoints. Every test runs as the author "bob" so that writing
 * requests pass the security checks; who may do what is covered by {@link BlogSecurityTest}.
 */
@QuarkusTest
@TestSecurity(user = BOB, roles = {Roles.READER, Roles.AUTHOR})
@OidcSecurity(claims = @Claim(key = "sub", value = BOB_ID))
class BlogResourceTest {

    private Long createBlog(String title, String content) {
        return given()
                .contentType(ContentType.JSON)
                .body(new BlogRequest(title, content))
                .when().post("/blogs")
                .then()
                .statusCode(201)
                .header("Location", containsString("/blogs/"))
                .extract().as(BlogResponse.class, ObjectMapperType.JSONB)
                .id();
    }

    private void addComment(Long blogId, String text) {
        given()
                .contentType(ContentType.JSON)
                .body(new CommentRequest(text))
                .when().post("/blogs/{id}/comments", blogId)
                .then()
                .statusCode(201);
    }

    @Test
    void listingBlogsReturnsOk() {
        given()
                .when().get("/blogs")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void creatingAndReadingBlog() {
        Long id = createBlog("Created Blog", "Created through REST");

        BlogResponse blog = given()
                .when().get("/blogs/{id}", id)
                .then()
                .statusCode(200)
                .extract().as(BlogResponse.class, ObjectMapperType.JSONB);

        assertNotNull(blog.createdAt());
        assertEquals(new BlogResponse(id, "Created Blog", "Created through REST", BOB, blog.createdAt()), blog);
    }

    @Test
    void updatingBlogWithPut() {
        Long id = createBlog("Before Update", "Before update content");

        given()
                .contentType(ContentType.JSON)
                .body(new BlogRequest("After Update", "After update content"))
                .when().put("/blogs/{id}", id)
                .then()
                .statusCode(200)
                .body("id", is(id.intValue()))
                .body("title", is("After Update"));

        given()
                .when().get("/blogs/{id}", id)
                .then()
                .statusCode(200)
                .body("content", is("After update content"));
    }

    @Test
    void deletingBlogReturnsNoContentAndThenNotFound() {
        Long id = createBlog("Delete Me", "Will be removed");

        given()
                .when().delete("/blogs/{id}", id)
                .then()
                .statusCode(204);

        given()
                .when().get("/blogs/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void searchQueryParameterFiltersBlogs() {
        createBlog("Zebra Search Marker", "Content of the marked blog");

        given()
                .queryParam("search", "zebra search marker")
                .when().get("/blogs")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].title", is("Zebra Search Marker"));
    }

    @Test
    void maxResultsHeaderLimitsTheResult() {
        createBlog("Header Blog One", "First header blog");
        createBlog("Header Blog Two", "Second header blog");

        given()
                .header("X-Max-Results", 1)
                .when().get("/blogs")
                .then()
                .statusCode(200)
                .body("size()", is(1));
    }

    @Test
    void addingAndListingComments() {
        Long id = createBlog("Commented Blog", "A blog that gets comments");

        given()
                .contentType(ContentType.JSON)
                .body(new CommentRequest("Nice post!"))
                .when().post("/blogs/{id}/comments", id)
                .then()
                .statusCode(201)
                .header("Location", containsString("/blogs/" + id + "/comments/"))
                .body("text", is("Nice post!"));

        given()
                .when().get("/blogs/{id}/comments", id)
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].text", is("Nice post!"));
    }

    @Test
    void commentsAreListedOldestFirst() {
        Long id = createBlog("Blog With Discussion", "Comments in the order they were written");
        addComment(id, "First comment");
        addComment(id, "Second comment");

        given()
                .when().get("/blogs/{id}/comments", id)
                .then()
                .statusCode(200)
                .body("text", contains("First comment", "Second comment"));
    }

    // --- DTO contract ----------------------------------------------------

    @Test
    void blogResponseExposesOnlyPublicFields() {
        Long id = createBlog("Blog With Hidden Comments", "Comments are not embedded");
        addComment(id, "Not part of the blog response");

        given()
                .when().get("/blogs/{id}", id)
                .then()
                .statusCode(200)
                .body("keySet()", containsInAnyOrder("id", "title", "content", "author", "createdAt"));

        given()
                .when().get("/blogs")
                .then()
                .statusCode(200)
                .body("[0].keySet()", containsInAnyOrder("id", "title", "content", "author", "createdAt"));
    }

    @Test
    void commentResponseHasNoBackReferenceToTheBlog() {
        Long id = createBlog("Blog For Comment Shape", "Content");
        addComment(id, "Shape check");

        given()
                .when().get("/blogs/{id}/comments", id)
                .then()
                .statusCode(200)
                .body("[0].keySet()", containsInAnyOrder("id", "text", "author", "createdAt"));
    }

    @Test
    void clientCannotChooseTheIdOfANewBlog() {
        // BlogRequest has no id field, so a smuggled id is ignored (no mass assignment).
        int smuggledId = 424242;

        given()
                .contentType(ContentType.JSON)
                .body("{\"id\": " + smuggledId + ", \"title\": \"Mass Assignment\", \"content\": \"Tries to set the id\"}")
                .when().post("/blogs")
                .then()
                .statusCode(201)
                .body("id", not(is(smuggledId)))
                .body("title", is("Mass Assignment"));

        given()
                .when().get("/blogs/{id}", smuggledId)
                .then()
                .statusCode(404);
    }

    // --- error responses -------------------------------------------------

    @Test
    void creatingBlogWithBlankTitleIsRejectedByBeanValidation() {
        given()
                .contentType(ContentType.JSON)
                .body(new BlogRequest("   ", "Content is fine"))
                .when().post("/blogs")
                .then()
                .statusCode(400)
                .body("error", is("Bad Request"))
                .body("message", is("Request validation failed"))
                .body("violations", hasSize(1))
                .body("violations[0].field", is("title"))
                .body("violations[0].message", is("Title must not be blank"));
    }

    @Test
    void creatingBlogWithTooLongContentIsRejected() {
        given()
                .contentType(ContentType.JSON)
                .body(new BlogRequest("Too Long", "x".repeat(4001)))
                .when().post("/blogs")
                .then()
                .statusCode(400)
                .body("violations[0].field", is("content"))
                .body("violations[0].message", is("Content must not exceed 4000 characters"));
    }

    @Test
    void creatingCommentWithoutTextIsRejectedByBeanValidation() {
        Long id = createBlog("Blog For Invalid Comment", "Content");

        given()
                .contentType(ContentType.JSON)
                .body(new CommentRequest(""))
                .when().post("/blogs/{id}/comments", id)
                .then()
                .statusCode(400)
                .body("violations[0].field", is("text"))
                .body("violations[0].message", is("Text must not be blank"));
    }

    @Test
    void readingUnknownBlogReturnsNotFoundWithMessage() {
        given()
                .when().get("/blogs/{id}", 999999)
                .then()
                .statusCode(404)
                .body("status", is(404))
                .body("error", is("Not Found"))
                .body("message", is("Blog with id 999999 not found"));
    }

    @Test
    void updatingUnknownBlogReturnsNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(new BlogRequest("Ghost", "Nobody home"))
                .when().put("/blogs/{id}", 999999)
                .then()
                .statusCode(404)
                .body("message", containsString("999999"));
    }

    @Test
    void unsupportedSortFieldReturnsBadRequest() {
        given()
                .queryParam("sort", "password")
                .when().get("/blogs")
                .then()
                .statusCode(400)
                .body("error", is("Bad Request"))
                .body("message", containsString("Unsupported sort field"));
    }

    @Test
    void nonPositiveMaxResultsHeaderReturnsBadRequest() {
        given()
                .header("X-Max-Results", 0)
                .when().get("/blogs")
                .then()
                .statusCode(400)
                .body("violations[0].message", is("X-Max-Results must be a positive number"));
    }
}

package ch.hftm.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BlogResourceTest {

    private Long createBlog(String title, String content) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"title\": \"" + title + "\", \"content\": \"" + content + "\"}")
                .when().post("/blogs")
                .then()
                .statusCode(201)
                .header("Location", containsString("/blogs/"))
                .extract().jsonPath().getLong("id");
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

        given()
                .when().get("/blogs/{id}", id)
                .then()
                .statusCode(200)
                .body("title", is("Created Blog"))
                .body("content", is("Created through REST"));
    }

    @Test
    void updatingBlogWithPut() {
        Long id = createBlog("Before Update", "Before update content");

        given()
                .contentType(ContentType.JSON)
                .body("{\"title\": \"After Update\", \"content\": \"After update content\"}")
                .when().put("/blogs/{id}", id)
                .then()
                .statusCode(200)
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
                .body("{\"text\": \"Nice post!\"}")
                .when().post("/blogs/{id}/comments", id)
                .then()
                .statusCode(201)
                .body("text", is("Nice post!"));

        given()
                .when().get("/blogs/{id}/comments", id)
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].text", is("Nice post!"));
    }

    // --- error responses -------------------------------------------------

    @Test
    void creatingBlogWithBlankTitleIsRejectedByBeanValidation() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"title\": \"   \", \"content\": \"Content is fine\"}")
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
    void creatingCommentWithoutTextIsRejectedByBeanValidation() {
        Long id = createBlog("Blog For Invalid Comment", "Content");

        given()
                .contentType(ContentType.JSON)
                .body("{\"text\": \"\"}")
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
                .body("{\"title\": \"Ghost\", \"content\": \"Nobody home\"}")
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

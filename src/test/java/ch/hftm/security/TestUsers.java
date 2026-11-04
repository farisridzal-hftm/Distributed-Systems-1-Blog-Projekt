package ch.hftm.security;

/**
 * Test users from keycloak/blog-realm.json. The ids are the {@code sub} claims Keycloak puts into
 * their tokens; with {@code @OidcSecurity} the same ids end up in the simulated tokens of
 * {@code @TestSecurity}. Compile-time constants, so they can be used in annotations.
 */
public final class TestUsers {

    public static final String ALICE = "alice";
    public static final String ALICE_ID = "69859d63-4ed2-4482-beae-5027e6404a81";

    public static final String BOB = "bob";
    public static final String BOB_ID = "b4b8b584-8662-4d9e-80cb-419a80f1e492";

    public static final String CAROL = "carol";
    public static final String CAROL_ID = "ba5e16fb-2892-4751-ab75-a940e666e95b";

    public static final String ADMIN = "admin";
    public static final String ADMIN_ID = "bdf6bf8c-051c-4ae6-9c70-eb059bbca081";

    private TestUsers() {
    }
}

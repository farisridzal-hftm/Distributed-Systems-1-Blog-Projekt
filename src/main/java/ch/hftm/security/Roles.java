package ch.hftm.security;

/** Realm roles from Keycloak (claim {@code realm_access.roles}), see keycloak/blog-realm.json. */
public final class Roles {

    /** Every registered user: may write comments. */
    public static final String READER = "reader";

    /** May create blogs and change or delete their own blogs. */
    public static final String AUTHOR = "author";

    /** Moderation: may delete any blog. */
    public static final String ADMIN = "admin";

    private Roles() {
    }
}

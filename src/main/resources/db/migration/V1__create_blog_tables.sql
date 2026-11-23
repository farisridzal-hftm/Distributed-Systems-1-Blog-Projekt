-- Initial schema of the blog API, exactly as Hibernate created it up to version 0.1.
-- Table and column names are case-sensitive in MySQL on Linux and match the JPA entities.

CREATE TABLE Blog (
    id         BIGINT        NOT NULL,
    title      VARCHAR(120)  NOT NULL,
    content    VARCHAR(4000) NOT NULL,
    authorId   VARCHAR(255),
    authorName VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE BlogComment (
    id         BIGINT        NOT NULL,
    text       VARCHAR(1000) NOT NULL,
    authorId   VARCHAR(255),
    authorName VARCHAR(255),
    blog_id    BIGINT,
    PRIMARY KEY (id),
    -- Same constraint name as in databases created by version 0.1, so both look identical.
    CONSTRAINT FK7uxqrg8g10rkqdd7h41jed7yr FOREIGN KEY (blog_id) REFERENCES Blog (id)
) ENGINE = InnoDB;

-- MySQL has no sequences: Hibernate emulates them for @GeneratedValue with one-row tables
-- and reserves ids in blocks of 50.
CREATE TABLE Blog_SEQ (next_val BIGINT) ENGINE = InnoDB;
INSERT INTO Blog_SEQ (next_val) VALUES (1);

CREATE TABLE BlogComment_SEQ (next_val BIGINT) ENGINE = InnoDB;
INSERT INTO BlogComment_SEQ (next_val) VALUES (1);

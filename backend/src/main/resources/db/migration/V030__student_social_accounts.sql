CREATE TABLE student_social_accounts (
    user_id BIGINT NOT NULL,
    provider VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT uq_student_social_provider UNIQUE (user_id, provider),
    CONSTRAINT uq_student_social_identity UNIQUE (provider, subject),
    CONSTRAINT fk_student_social_user FOREIGN KEY (user_id) REFERENCES students (user_id),
    CONSTRAINT ck_student_social_provider CHECK (provider IN ('google', 'microsoft'))
);

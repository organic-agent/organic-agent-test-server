CREATE EXTENSION IF NOT EXISTS vector;

-- 가입 API가 없다. 계정은 이 테이블에 직접 INSERT 해서 만든다.
-- password는 BCrypt 해시여야 한다(평문이면 로그인이 조용히 실패한다).
CREATE TABLE photographer
(
    id         BIGSERIAL PRIMARY KEY,
    login_id   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE gallery
(
    id              BIGSERIAL PRIMARY KEY,
    photographer_id BIGINT       NOT NULL REFERENCES photographer (id),
    name            VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_gallery_photographer ON gallery (photographer_id);

CREATE TABLE photo
(
    id                BIGSERIAL PRIMARY KEY,
    gallery_id        BIGINT       NOT NULL REFERENCES gallery (id) ON DELETE CASCADE,
    -- presigned URL을 발급한 시점에 정해지는 업로드 목적지. 실제 업로드는 프론트가 S3로 직접 한다.
    s3_key            VARCHAR(512) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    -- PENDING(URL 발급됨) -> UPLOADED(프론트가 완료 통보) -> EMBEDDED(임베딩 적재됨)
    status            VARCHAR(20)  NOT NULL,
    embedding         vector(512),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_photo_gallery ON photo (gallery_id);

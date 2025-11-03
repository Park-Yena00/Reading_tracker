-- 리프레시 토큰 테이블 생성
CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX idx_refresh_tokens_user_device ON refresh_tokens(user_id, device_id);
CREATE INDEX idx_refresh_tokens_revoked_expires ON refresh_tokens(revoked, expires_at);






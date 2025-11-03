-- 사용자 디바이스 테이블 생성 (앱 확장 대비)
CREATE TABLE user_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    platform ENUM('WEB', 'ANDROID', 'IOS') NOT NULL,
    last_seen_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_device (user_id, device_id)
);

-- 인덱스 생성
CREATE INDEX idx_user_devices_user_device ON user_devices(user_id, device_id);






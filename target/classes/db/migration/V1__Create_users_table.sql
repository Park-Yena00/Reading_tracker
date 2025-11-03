-- 사용자 테이블 생성
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    status ENUM('ACTIVE', 'LOCKED', 'DELETED') NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX idx_users_login_id ON users(login_id);
CREATE INDEX idx_users_email ON users(email);


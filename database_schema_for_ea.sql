-- Reading Tracker Database Schema for EA Tools
-- 이 파일은 Enterprise Architecture 도구와 연동하기 위한 DDL입니다.

-- 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS reading_tracker 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE reading_tracker;

-- 1. 사용자 테이블
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    login_id VARCHAR(50) NOT NULL UNIQUE COMMENT '로그인 ID',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '이메일 주소',
    name VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    password_hash VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호',
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER' COMMENT '사용자 역할',
    status ENUM('ACTIVE', 'LOCKED', 'DELETED') NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태',
    failed_login_count INT NOT NULL DEFAULT 0 COMMENT '로그인 실패 횟수',
    last_login_at TIMESTAMP NULL COMMENT '마지막 로그인 시간',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 정보';

-- 2. 도서 테이블
CREATE TABLE books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    isbn VARCHAR(13) UNIQUE NOT NULL COMMENT 'ISBN 코드',
    title VARCHAR(255) NOT NULL COMMENT '도서 제목',
    author VARCHAR(255) NOT NULL COMMENT '저자',
    publisher VARCHAR(255) NOT NULL COMMENT '출판사',
    description TEXT COMMENT '도서 설명',
    cover_url VARCHAR(255) COMMENT '표지 이미지 URL',
    total_pages INT COMMENT '총 페이지 수',
    main_genre VARCHAR(50) COMMENT '주요 장르',
    pub_date DATE COMMENT '출판일',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='도서 정보';

-- 3. 사용자-도서 관계 테이블
CREATE TABLE user_books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    book_id BIGINT NOT NULL COMMENT '도서 ID',
    category ENUM('WANT_TO_READ', 'READING', 'ALMOST_FINISHED', 'FINISHED') NOT NULL COMMENT '독서 상태',
    memo TEXT COMMENT '메모',
    reading_start_date DATE COMMENT '독서 시작일',
    reading_progress INT COMMENT '독서 진행률 (읽은 페이지 수)',
    reading_finished_date DATE COMMENT '독서 완료일',
    rating INT COMMENT '평점 (1-5)',
    review TEXT COMMENT '독서 후기',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_book (user_id, book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자-도서 관계';

-- 4. 사용자 디바이스 테이블
CREATE TABLE user_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    device_id VARCHAR(255) NOT NULL COMMENT '디바이스 고유 ID',
    device_name VARCHAR(100) NOT NULL COMMENT '디바이스 이름',
    platform ENUM('WEB', 'ANDROID', 'IOS') NOT NULL COMMENT '플랫폼',
    last_seen_at TIMESTAMP NULL COMMENT '마지막 접속 시간',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_device (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 디바이스 정보';

-- 5. 리프레시 토큰 테이블
CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    device_id VARCHAR(255) NOT NULL COMMENT '디바이스 ID',
    token VARCHAR(255) NOT NULL COMMENT '리프레시 토큰',
    expires_at TIMESTAMP NOT NULL COMMENT '만료 시간',
    revoked BOOLEAN NOT NULL DEFAULT FALSE COMMENT '토큰 취소 여부',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT 리프레시 토큰';

-- 6. 비밀번호 재설정 토큰 테이블
CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    token VARCHAR(100) NOT NULL UNIQUE COMMENT '재설정 토큰',
    expires_at TIMESTAMP NOT NULL COMMENT '만료 시간',
    used BOOLEAN NOT NULL DEFAULT FALSE COMMENT '사용 여부',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='비밀번호 재설정 토큰';

-- 인덱스 생성
-- 사용자 테이블 인덱스
CREATE INDEX idx_users_login_id ON users(login_id);
CREATE INDEX idx_users_email ON users(email);

-- 사용자 디바이스 테이블 인덱스
CREATE INDEX idx_user_devices_user_device ON user_devices(user_id, device_id);

-- 리프레시 토큰 테이블 인덱스
CREATE INDEX idx_refresh_tokens_user_device ON refresh_tokens(user_id, device_id);
CREATE INDEX idx_refresh_tokens_revoked_expires ON refresh_tokens(revoked, expires_at);

-- 비밀번호 재설정 토큰 테이블 인덱스
CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);

-- 샘플 데이터 삽입 (테스트용)
INSERT INTO users (login_id, email, name, password_hash) VALUES
('admin', 'admin@example.com', '관리자', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi'),
('user1', 'user1@example.com', '사용자1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi'),
('user2', 'user2@example.com', '사용자2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi');

INSERT INTO books (isbn, title, author, publisher, description, total_pages, main_genre, pub_date) VALUES
('9788965746669', '클린 코드', '로버트 C. 마틴', '인사이트', '애자일 소프트웨어 장인 정신', 584, '컴퓨터/IT', '2013-12-24'),
('9788966262412', '이펙티브 자바', '조슈아 블로크', '인사이트', 'Java 플랫폼 베스트 프랙티스', 688, '컴퓨터/IT', '2018-11-01'),
('9788934985749', '자바의 정석', '남궁성', '도우출판', 'Java 프로그래밍의 기본', 1024, '컴퓨터/IT', '2016-01-01');

INSERT INTO user_books (user_id, book_id, category, reading_progress, rating) VALUES
(2, 1, 'READING', 150, NULL),
(2, 2, 'WANT_TO_READ', NULL, NULL),
(3, 1, 'FINISHED', 584, 5),
(3, 3, 'READING', 300, NULL);

INSERT INTO user_devices (user_id, device_id, device_name, platform) VALUES
(1, 'web-001', '관리자 웹', 'WEB'),
(2, 'android-001', '사용자1 안드로이드', 'ANDROID'),
(3, 'ios-001', '사용자2 iOS', 'IOS');

-- 뷰 생성 (EA 도구에서 활용)
CREATE VIEW user_reading_summary AS
SELECT 
    u.id as user_id,
    u.name as user_name,
    COUNT(ub.id) as total_books,
    SUM(CASE WHEN ub.category = 'WANT_TO_READ' THEN 1 ELSE 0 END) as want_to_read_count,
    SUM(CASE WHEN ub.category = 'READING' THEN 1 ELSE 0 END) as reading_count,
    SUM(CASE WHEN ub.category = 'ALMOST_FINISHED' THEN 1 ELSE 0 END) as almost_finished_count,
    SUM(CASE WHEN ub.category = 'FINISHED' THEN 1 ELSE 0 END) as finished_count,
    AVG(ub.rating) as average_rating
FROM users u
LEFT JOIN user_books ub ON u.id = ub.user_id
GROUP BY u.id, u.name;

-- 저장 프로시저 생성 (EA 도구에서 활용)
DELIMITER //
CREATE PROCEDURE GetUserReadingStats(IN user_id_param BIGINT)
BEGIN
    SELECT 
        u.name,
        COUNT(ub.id) as total_books,
        SUM(CASE WHEN ub.category = 'FINISHED' THEN 1 ELSE 0 END) as finished_books,
        AVG(ub.rating) as average_rating
    FROM users u
    LEFT JOIN user_books ub ON u.id = ub.user_id
    WHERE u.id = user_id_param
    GROUP BY u.id, u.name;
END //
DELIMITER ;

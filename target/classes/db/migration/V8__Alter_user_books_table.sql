-- user_books 테이블 수정 (UserShelfBook 엔티티에 맞춤)
ALTER TABLE user_books
    DROP COLUMN last_read_page,
    DROP COLUMN last_read_at,
    DROP COLUMN last_page_at,
    ADD COLUMN memo TEXT AFTER category,
    ADD COLUMN reading_start_date DATE AFTER memo,
    ADD COLUMN reading_progress INT AFTER reading_start_date,
    ADD COLUMN reading_finished_date DATE AFTER reading_progress,
    ADD COLUMN rating INT AFTER reading_finished_date,
    ADD COLUMN review TEXT AFTER rating,
    MODIFY COLUMN category ENUM('ToRead', 'Reading', 'AlmostFinished', 'Finished') NOT NULL,
    CHANGE COLUMN added_at created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    DROP INDEX user_id,
    ADD UNIQUE KEY uk_user_book (user_id, book_id),
    ADD INDEX idx_user_books_category (category),
    ADD INDEX idx_user_books_created_at (created_at);

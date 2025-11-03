CREATE TABLE user_books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    category ENUM('ToRead', 'Reading', 'AlmostFinished', 'Finished') NOT NULL,
    last_read_page INT DEFAULT 0,
    last_read_at DATETIME NULL,
    last_page_at DATETIME NULL,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
    UNIQUE(user_id, book_id)
);


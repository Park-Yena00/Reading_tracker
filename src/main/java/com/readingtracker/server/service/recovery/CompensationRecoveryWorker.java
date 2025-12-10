package com.readingtracker.server.service.recovery;

import com.readingtracker.dbms.entity.Memo;
import com.readingtracker.dbms.entity.Tag;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserDevice;
import com.readingtracker.dbms.entity.UserShelfBook;
import com.readingtracker.dbms.repository.primary.MemoRepository;
import com.readingtracker.dbms.repository.primary.UserDeviceRepository;
import com.readingtracker.dbms.repository.primary.UserRepository;
import com.readingtracker.dbms.repository.primary.UserShelfBookRepository;
import com.readingtracker.server.service.alert.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 보상 트랜잭션 실패 복구 작업자
 * 
 * 주기적으로 복구 큐를 확인하여 실패한 보상 트랜잭션을 재시도합니다.
 */
@Service
public class CompensationRecoveryWorker {
    
    private static final Logger log = LoggerFactory.getLogger(CompensationRecoveryWorker.class);
    private static final int MAX_RETRY_COUNT = 10;
    
    @Autowired
    private RecoveryQueueService recoveryQueueService;
    
    @Autowired
    private MemoRepository memoRepository;
    
    @Autowired
    private UserShelfBookRepository userShelfBookRepository;
    
    @Autowired
    private UserDeviceRepository userDeviceRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    @Autowired
    private AlertService alertService;
    
    /**
     * 복구 큐에서 실패한 보상 트랜잭션을 처리
     * 1분마다 실행 (백그라운드 스레드)
     */
    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void processRecoveryQueue() {
        List<CompensationFailureEvent> events = recoveryQueueService.consume();
        
        if (events.isEmpty()) {
            return;
        }
        
        log.info("복구 큐 처리 시작: {} 개 이벤트", events.size());
        
        for (CompensationFailureEvent event : events) {
            processRecoveryEvent(event);
        }
    }
    
    /**
     * 개별 복구 이벤트 처리
     */
    private void processRecoveryEvent(CompensationFailureEvent event) {
        try {
            String action = event.getCompensationAction();
            
            // Secondary DB 동기화 재시도 (SECONDARY_SYNC_RETRY)
            // 보상 트랜잭션 실패 시 DualMasterWriteService에서 발행된 이벤트
            // Primary DB의 최신 데이터를 Secondary DB에 동기화
            if ("SECONDARY_SYNC_RETRY".equals(action)) {
                Long entityId = event.getEntityId();
                String entityType = event.getEntityType();
                boolean syncSuccess = false;
                
                if ("Memo".equals(entityType)) {
                    syncSuccess = syncMemoToSecondary(entityId);
                } else if ("UserShelfBook".equals(entityType)) {
                    syncSuccess = syncUserShelfBookToSecondary(entityId);
                } else if ("UserDevice".equals(entityType)) {
                    syncSuccess = syncUserDeviceToSecondary(entityId);
                } else if ("User".equals(entityType)) {
                    syncSuccess = syncUserToSecondary(entityId);
                } else if ("Book".equals(entityType)) {
                    // TODO: Book 동기화 로직 추가 (필요시)
                    log.warn("Book 동기화는 아직 구현되지 않았습니다: entityId={}", entityId);
                    syncSuccess = false;
                } else {
                    log.warn("지원하지 않는 엔티티 타입: {}", entityType);
                    return;
                }
                
                if (syncSuccess) {
                    log.info("Secondary 동기화 재시도 성공: entityType={}, entityId={}", 
                             entityType, entityId);
                    recoveryQueueService.acknowledge(event);
                } else {
                    log.warn("Secondary 동기화 재시도 실패: entityType={}, entityId={}", 
                             entityType, entityId);
                    // 실패 시 재시도 로직에 의해 다시 큐에 추가됨
                    throw new RuntimeException("Secondary DB 동기화 실패: entityType=" + entityType + ", entityId=" + entityId);
                }
            }
            // Primary DB에서 DELETE 실행 (보상 트랜잭션 실패 복구) - 레거시 지원
            else if ("DELETE".equals(action)) {
                Long entityId = event.getEntityId();
                if (entityId != null) {
                    memoRepository.deleteById(entityId);
                    log.info("복구 성공: entityType={}, entityId={}", 
                             event.getEntityType(), entityId);
                    recoveryQueueService.acknowledge(event);
                } else {
                    log.warn("DELETE 액션: entityId가 null입니다.");
                }
            }
            // Secondary DB에서 유령 데이터 정리 (DELETE_SECONDARY_CLEANUP) - 레거시 지원
            else if ("DELETE_SECONDARY_CLEANUP".equals(action)) {
                Long entityId = event.getEntityId();
                String entityType = event.getEntityType();
                int deletedRows = 0;
                
                if ("Memo".equals(entityType)) {
                    // Secondary DB에서 memo_tags 삭제
                    String deleteMemoTagsSql = "DELETE FROM memo_tags WHERE memo_id = :memoId";
                    Map<String, Object> deleteTagsParams = new HashMap<>();
                    deleteTagsParams.put("memoId", entityId);
                    secondaryNamedParameterJdbcTemplate.update(deleteMemoTagsSql, deleteTagsParams);
                    
                    // Secondary DB에서 memo 삭제
                    String deleteMemoSql = "DELETE FROM memo WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteMemoSql, deleteParams);
                } else if ("UserShelfBook".equals(entityType)) {
                    // Secondary DB에서 user_books 삭제
                    String deleteUserShelfBookSql = "DELETE FROM user_books WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserShelfBookSql, deleteParams);
                } else if ("UserDevice".equals(entityType)) {
                    // Secondary DB에서 user_devices 삭제
                    String deleteUserDeviceSql = "DELETE FROM user_devices WHERE id = :id";
                    Map<String, Object> deleteParams = new HashMap<>();
                    deleteParams.put("id", entityId);
                    deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserDeviceSql, deleteParams);
                } else {
                    log.warn("지원하지 않는 엔티티 타입: {}", entityType);
                    return;
                }
                
                if (deletedRows > 0) {
                    log.info("Secondary 유령 데이터 정리 성공: entityType={}, entityId={}, deletedRows={}", 
                             entityType, entityId, deletedRows);
                } else {
                    log.warn("Secondary 유령 데이터가 이미 정리되었거나 존재하지 않음: entityType={}, entityId={}", 
                             entityType, entityId);
                }
                
                recoveryQueueService.acknowledge(event);
            } else {
                log.warn("지원하지 않는 보상 액션: {}", action);
            }
        } catch (Exception e) {
            handleRecoveryFailure(event, e);
        }
    }
    
    /**
     * Memo를 Primary DB에서 조회하여 Secondary DB에 동기화
     * 
     * 동기화 방식:
     * 1. SecondaryDB에 기존 데이터가 있으면 삭제 (memo_tags 포함)
     * 2. PrimaryDB에서 최신 데이터 조회
     * 3. SecondaryDB에 INSERT (PrimaryDB의 최신 상태로 동기화)
     * 
     * @param memoId 메모 ID
     * @return 동기화 성공 여부
     */
    private boolean syncMemoToSecondary(Long memoId) {
        if (memoId == null) {
            log.warn("syncMemoToSecondary: memoId가 null입니다.");
            return false;
        }
        
        try {
            // 1. SecondaryDB에 기존 데이터가 있으면 먼저 삭제 (memo_tags 포함)
            // UPDATE 실패로 인한 불일치 데이터를 정리하기 위함
            String deleteMemoTagsSql = "DELETE FROM memo_tags WHERE memo_id = :memoId";
            Map<String, Object> deleteTagsParams = new HashMap<>();
            deleteTagsParams.put("memoId", memoId);
            secondaryNamedParameterJdbcTemplate.update(deleteMemoTagsSql, deleteTagsParams);
            
            String deleteMemoSql = "DELETE FROM memo WHERE id = :id";
            Map<String, Object> deleteParams = new HashMap<>();
            deleteParams.put("id", memoId);
            int deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteMemoSql, deleteParams);
            
            if (deletedRows > 0) {
                log.debug("SecondaryDB에서 기존 Memo 삭제: memoId={}", memoId);
            }
            
            // 2. PrimaryDB에서 최신 Memo 조회
            Memo memo = memoRepository.findById(memoId).orElse(null);
            if (memo == null) {
                log.warn("Primary DB에서 Memo를 찾을 수 없습니다: memoId={}. SecondaryDB는 이미 삭제되었습니다.", memoId);
                // Primary에 없으면 Secondary에서도 삭제 완료 (이미 삭제됨)
                return true; // 삭제 성공으로 간주
            }
            
            // 3. PrimaryDB의 최신 데이터를 SecondaryDB에 INSERT
            LocalDateTime now = LocalDateTime.now();
            String insertMemoSql = "INSERT INTO memo (id, user_id, book_id, page_number, content, " +
                                 "memo_start_time, created_at, updated_at) " +
                                 "VALUES (:id, :userId, :bookId, :pageNumber, :content, " +
                                 ":memoStartTime, :createdAt, :updatedAt)";
            Map<String, Object> insertParams = new HashMap<>();
            insertParams.put("id", memo.getId());
            insertParams.put("userId", memo.getUserId());
            insertParams.put("bookId", memo.getUserShelfBookId());
            insertParams.put("pageNumber", memo.getPageNumber());
            insertParams.put("content", memo.getContent());
            insertParams.put("memoStartTime", memo.getMemoStartTime());
            insertParams.put("createdAt", memo.getCreatedAt() != null ? memo.getCreatedAt() : now);
            insertParams.put("updatedAt", memo.getUpdatedAt() != null ? memo.getUpdatedAt() : now);
            secondaryNamedParameterJdbcTemplate.update(insertMemoSql, insertParams);
            
            log.debug("SecondaryDB에 PrimaryDB 최신 Memo 삽입: memoId={}", memoId);
            
            // 4. memo_tags 삽입 (태그가 있는 경우)
            if (memo.getTags() != null && !memo.getTags().isEmpty()) {
                String insertMemoTagsSql = "INSERT INTO memo_tags (memo_id, tag_id) VALUES (:memoId, :tagId)";
                for (Tag tag : memo.getTags()) {
                    Map<String, Object> tagParams = new HashMap<>();
                    tagParams.put("memoId", memoId);
                    tagParams.put("tagId", tag.getId());
                    secondaryNamedParameterJdbcTemplate.update(insertMemoTagsSql, tagParams);
                }
            }
            
            log.info("Memo Secondary DB 동기화 완료: memoId={} (PrimaryDB 최신 데이터로 동기화)", memoId);
            return true;
            
        } catch (Exception e) {
            log.error("Memo Secondary DB 동기화 실패: memoId={}, error={}", memoId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * UserShelfBook을 Primary DB에서 조회하여 Secondary DB에 동기화
     * 
     * 동기화 방식:
     * 1. SecondaryDB에 기존 데이터가 있으면 삭제
     * 2. PrimaryDB에서 최신 데이터 조회
     * 3. SecondaryDB에 INSERT (PrimaryDB의 최신 상태로 동기화)
     * 
     * @param userBookId UserShelfBook ID
     * @return 동기화 성공 여부
     */
    private boolean syncUserShelfBookToSecondary(Long userBookId) {
        if (userBookId == null) {
            log.warn("syncUserShelfBookToSecondary: userBookId가 null입니다.");
            return false;
        }
        
        try {
            // 1. SecondaryDB에 기존 데이터가 있으면 먼저 삭제
            String deleteUserShelfBookSql = "DELETE FROM user_books WHERE id = :id";
            Map<String, Object> deleteParams = new HashMap<>();
            deleteParams.put("id", userBookId);
            int deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserShelfBookSql, deleteParams);
            
            if (deletedRows > 0) {
                log.debug("SecondaryDB에서 기존 UserShelfBook 삭제: userBookId={}", userBookId);
            }
            
            // 2. PrimaryDB에서 최신 UserShelfBook 조회
            UserShelfBook userShelfBook = userShelfBookRepository.findById(userBookId).orElse(null);
            if (userShelfBook == null) {
                log.warn("Primary DB에서 UserShelfBook을 찾을 수 없습니다: userBookId={}. SecondaryDB는 이미 삭제되었습니다.", userBookId);
                return true; // 삭제 성공으로 간주
            }
            
            // 3. PrimaryDB의 최신 데이터를 SecondaryDB에 INSERT
            LocalDateTime now = LocalDateTime.now();
            String insertUserShelfBookSql = "INSERT INTO user_books (id, user_id, book_id, category, " +
                                           "category_manually_set, expectation, reading_start_date, " +
                                           "reading_progress, purchase_type, reading_finished_date, " +
                                           "rating, review, created_at, updated_at) " +
                                           "VALUES (:id, :userId, :bookId, :category, " +
                                           ":categoryManuallySet, :expectation, :readingStartDate, " +
                                           ":readingProgress, :purchaseType, :readingFinishedDate, " +
                                           ":rating, :review, :createdAt, :updatedAt)";
            Map<String, Object> insertParams = new HashMap<>();
            insertParams.put("id", userShelfBook.getId());
            insertParams.put("userId", userShelfBook.getUserId());
            insertParams.put("bookId", userShelfBook.getBook() != null ? userShelfBook.getBook().getId() : null);
            insertParams.put("category", userShelfBook.getCategory() != null ? userShelfBook.getCategory().name() : null);
            insertParams.put("categoryManuallySet", userShelfBook.isCategoryManuallySet() != null ? userShelfBook.isCategoryManuallySet() : false);
            insertParams.put("expectation", userShelfBook.getExpectation());
            insertParams.put("readingStartDate", userShelfBook.getReadingStartDate());
            insertParams.put("readingProgress", userShelfBook.getReadingProgress());
            insertParams.put("purchaseType", userShelfBook.getPurchaseType() != null ? userShelfBook.getPurchaseType().name() : null);
            insertParams.put("readingFinishedDate", userShelfBook.getReadingFinishedDate());
            insertParams.put("rating", userShelfBook.getRating());
            insertParams.put("review", userShelfBook.getReview());
            insertParams.put("createdAt", userShelfBook.getCreatedAt() != null ? userShelfBook.getCreatedAt() : now);
            insertParams.put("updatedAt", userShelfBook.getUpdatedAt() != null ? userShelfBook.getUpdatedAt() : now);
            secondaryNamedParameterJdbcTemplate.update(insertUserShelfBookSql, insertParams);
            
            log.info("UserShelfBook Secondary DB 동기화 완료: userBookId={} (PrimaryDB 최신 데이터로 동기화)", userBookId);
            return true;
            
        } catch (Exception e) {
            log.error("UserShelfBook Secondary DB 동기화 실패: userBookId={}, error={}", userBookId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * UserDevice를 Primary DB에서 조회하여 Secondary DB에 동기화
     * 
     * 동기화 방식:
     * 1. SecondaryDB에 기존 데이터가 있으면 삭제
     * 2. PrimaryDB에서 최신 데이터 조회
     * 3. SecondaryDB에 INSERT (PrimaryDB의 최신 상태로 동기화)
     * 
     * @param deviceId UserDevice ID
     * @return 동기화 성공 여부
     */
    private boolean syncUserDeviceToSecondary(Long deviceId) {
        if (deviceId == null) {
            log.warn("syncUserDeviceToSecondary: deviceId가 null입니다.");
            return false;
        }
        
        try {
            // 1. SecondaryDB에 기존 데이터가 있으면 먼저 삭제
            String deleteUserDeviceSql = "DELETE FROM user_devices WHERE id = :id";
            Map<String, Object> deleteParams = new HashMap<>();
            deleteParams.put("id", deviceId);
            int deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserDeviceSql, deleteParams);
            
            if (deletedRows > 0) {
                log.debug("SecondaryDB에서 기존 UserDevice 삭제: deviceId={}", deviceId);
            }
            
            // 2. PrimaryDB에서 최신 UserDevice 조회
            UserDevice userDevice = userDeviceRepository.findById(deviceId).orElse(null);
            if (userDevice == null) {
                log.warn("Primary DB에서 UserDevice를 찾을 수 없습니다: deviceId={}. SecondaryDB는 이미 삭제되었습니다.", deviceId);
                return true; // 삭제 성공으로 간주
            }
            
            // 3. PrimaryDB의 최신 데이터를 SecondaryDB에 INSERT
            LocalDateTime now = LocalDateTime.now();
            String insertUserDeviceSql = "INSERT INTO user_devices (id, user_id, device_id, device_name, platform, last_seen_at, created_at) " +
                                       "VALUES (:id, :userId, :deviceId, :deviceName, :platform, :lastSeenAt, :createdAt)";
            Map<String, Object> insertParams = new HashMap<>();
            insertParams.put("id", userDevice.getId());
            insertParams.put("userId", userDevice.getUser() != null ? userDevice.getUser().getId() : null);
            insertParams.put("deviceId", userDevice.getDeviceId());
            insertParams.put("deviceName", userDevice.getDeviceName());
            insertParams.put("platform", userDevice.getPlatform() != null ? userDevice.getPlatform().name() : null);
            insertParams.put("lastSeenAt", userDevice.getLastSeenAt());
            insertParams.put("createdAt", userDevice.getCreatedAt() != null ? userDevice.getCreatedAt() : now);
            secondaryNamedParameterJdbcTemplate.update(insertUserDeviceSql, insertParams);
            
            log.info("UserDevice Secondary DB 동기화 완료: deviceId={} (PrimaryDB 최신 데이터로 동기화)", deviceId);
            return true;
            
        } catch (Exception e) {
            log.error("UserDevice Secondary DB 동기화 실패: deviceId={}, error={}", deviceId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * User를 Primary DB에서 조회하여 Secondary DB에 동기화
     * 
     * 동기화 방식:
     * 1. SecondaryDB에 기존 데이터가 있으면 삭제
     * 2. PrimaryDB에서 최신 데이터 조회
     * 3. SecondaryDB에 INSERT (PrimaryDB의 최신 상태로 동기화)
     * 
     * @param userId User ID
     * @return 동기화 성공 여부
     */
    private boolean syncUserToSecondary(Long userId) {
        if (userId == null) {
            log.warn("syncUserToSecondary: userId가 null입니다.");
            return false;
        }
        
        try {
            // 1. SecondaryDB에 기존 데이터가 있으면 먼저 삭제
            String deleteUserSql = "DELETE FROM users WHERE id = :id";
            Map<String, Object> deleteParams = new HashMap<>();
            deleteParams.put("id", userId);
            int deletedRows = secondaryNamedParameterJdbcTemplate.update(deleteUserSql, deleteParams);
            
            if (deletedRows > 0) {
                log.debug("SecondaryDB에서 기존 User 삭제: userId={}", userId);
            }
            
            // 2. PrimaryDB에서 최신 User 조회
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("Primary DB에서 User를 찾을 수 없습니다: userId={}. SecondaryDB는 이미 삭제되었습니다.", userId);
                return true; // 삭제 성공으로 간주
            }
            
            // 3. PrimaryDB의 최신 데이터를 SecondaryDB에 INSERT
            LocalDateTime now = LocalDateTime.now();
            String insertUserSql = "INSERT INTO users (id, login_id, email, name, password_hash, role, status, " +
                                 "failed_login_count, last_login_at, created_at, updated_at) " +
                                 "VALUES (:id, :loginId, :email, :name, :passwordHash, :role, :status, " +
                                 ":failedLoginCount, :lastLoginAt, :createdAt, :updatedAt)";
            Map<String, Object> insertParams = new HashMap<>();
            insertParams.put("id", user.getId());
            insertParams.put("loginId", user.getLoginId());
            insertParams.put("email", user.getEmail());
            insertParams.put("name", user.getName());
            insertParams.put("passwordHash", user.getPasswordHash());
            insertParams.put("role", user.getRole() != null ? user.getRole().name() : "USER");
            insertParams.put("status", user.getStatus() != null ? user.getStatus().name() : "ACTIVE");
            insertParams.put("failedLoginCount", user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0);
            insertParams.put("lastLoginAt", user.getLastLoginAt());
            insertParams.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt() : now);
            insertParams.put("updatedAt", user.getUpdatedAt() != null ? user.getUpdatedAt() : now);
            secondaryNamedParameterJdbcTemplate.update(insertUserSql, insertParams);
            
            log.info("User Secondary DB 동기화 완료: userId={} (PrimaryDB 최신 데이터로 동기화)", userId);
            return true;
            
        } catch (Exception e) {
            log.error("User Secondary DB 동기화 실패: userId={}, error={}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 복구 실패 처리
     */
    private void handleRecoveryFailure(CompensationFailureEvent event, Exception e) {
        int retryCount = event.incrementRetryCount();
        log.warn("복구 재시도 실패: entityId={}, retryCount={}", 
                event.getEntityId(), retryCount);
        
        if (retryCount >= MAX_RETRY_COUNT) {
            // 최대 재시도 횟수 초과 시 수동 개입 필요 알림
            log.error("CRITICAL: 복구 작업 최대 재시도 횟수 초과: entityType={}, entityId={}, retryCount={}, 수동 개입 필요", 
                     event.getEntityType(), event.getEntityId(), retryCount);
            
            // AlertService를 통한 CRITICAL 알림 발송
            String alertTitle = "복구 작업 최대 재시도 횟수 초과";
            String alertMessage = String.format(
                "복구 작업이 최대 재시도 횟수(%d회)를 초과하여 실패했습니다. " +
                "시스템이 스스로 해결할 수 없는 데이터 정합성 오류입니다. " +
                "즉시 수동 개입이 필요합니다.\n" +
                "- Entity Type: %s\n" +
                "- Entity ID: %d\n" +
                "- Action: %s\n" +
                "- Target DB: %s\n" +
                "- Failure Time: %s\n" +
                "- Error Message: %s",
                MAX_RETRY_COUNT,
                event.getEntityType(),
                event.getEntityId(),
                event.getAction(),
                event.getTargetDB(),
                event.getFailureTime(),
                event.getErrorMessage()
            );
            
            alertService.sendCriticalAlert(alertTitle, alertMessage);
            
            recoveryQueueService.markAsFailed(event);
        } else {
            // 재시도 큐에 다시 추가
            recoveryQueueService.requeue(event);
        }
    }
}


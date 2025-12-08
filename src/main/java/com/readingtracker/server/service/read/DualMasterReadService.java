package com.readingtracker.server.service.read;

import com.readingtracker.server.common.exception.DatabaseUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * MySQL 이중화를 위한 Read Failover 서비스
 * 
 * Primary에서 읽기 시도 → 실패 시 Secondary로 Failover
 * 
 * Read 작업은 JPA Repository를 사용하여 편의성을 유지합니다.
 */
@Service
public class DualMasterReadService {
    
    private static final Logger log = LoggerFactory.getLogger(DualMasterReadService.class);
    
    @Autowired
    @Qualifier("primaryTransactionManager")
    private PlatformTransactionManager primaryTxManager;
    
    @Autowired
    @Qualifier("secondaryTransactionManager")
    private PlatformTransactionManager secondaryTxManager;
    
    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;
    
    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcTemplate secondaryJdbcTemplate;
    
    /**
     * Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     * 
     * JPA Repository를 사용하는 Read 작업을 지원합니다.
     * Primary DB에서 읽기 시도하고, 실패 시 Secondary DB로 자동 전환합니다.
     * 
     * @param readOperation 읽기 작업 (JPA Repository 사용)
     * @return 읽기 결과
     */
    public <T> T readWithFailover(Supplier<T> readOperation) {
        // Primary에서 시도
        try {
            TransactionTemplate txTemplate = new TransactionTemplate(primaryTxManager);
            T result = txTemplate.execute(status -> readOperation.get());
            
            log.debug("Primary DB 읽기 성공");
            return result;
            
        } catch (Exception e) {
            log.warn("Primary DB 읽기 실패, Secondary DB로 전환", e);
            
            // Secondary에서 시도
            // 주의: Secondary는 JdbcTemplate을 사용하므로, 
            // Secondary Repository가 필요한 경우 별도로 구현 필요
            // 현재는 Primary 실패 시 동일한 작업을 Secondary에서도 시도
            try {
                TransactionTemplate txTemplate = new TransactionTemplate(secondaryTxManager);
                // Secondary는 JdbcTemplate 기반이므로, 
                // 현재는 Primary와 동일한 Repository를 사용하되 Secondary 트랜잭션으로 실행
                // 향후 Secondary Repository를 별도로 구현할 수 있음
                T result = txTemplate.execute(status -> readOperation.get());
                
                log.info("Secondary DB 읽기 성공 (Failover)");
                return result;
                
            } catch (Exception e2) {
                log.error("Secondary DB 읽기도 실패", e2);
                throw new DatabaseUnavailableException("모든 DB 접근 실패", e2);
            }
        }
    }
}


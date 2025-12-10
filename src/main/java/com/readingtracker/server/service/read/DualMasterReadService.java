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
 * Read 작업은 JPA Repository(Primary)와 JdbcTemplate DAO(Secondary)를 사용합니다.
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
     * Primary와 Secondary 각각 다른 Supplier를 받아서 처리합니다.
     * Primary는 JPA Repository를 사용하고, Secondary는 JdbcTemplate DAO를 사용합니다.
     * 
     * @param primaryReadOperation Primary DB 읽기 작업 (JPA Repository 사용)
     * @param secondaryReadOperation Secondary DB 읽기 작업 (JdbcTemplate DAO 사용)
     * @return 읽기 결과
     */
    public <T> T readWithFailover(Supplier<T> primaryReadOperation, Supplier<T> secondaryReadOperation) {
        // Primary에서 시도
        try {
            TransactionTemplate txTemplate = new TransactionTemplate(primaryTxManager);
            T result = txTemplate.execute(status -> primaryReadOperation.get());
            
            log.debug("Primary DB 읽기 성공");
            return result;
            
        } catch (Exception e) {
            log.warn("Primary DB 읽기 실패, Secondary DB로 전환", e);
            
            // Secondary에서 시도 (JdbcTemplate DAO 사용)
            try {
                TransactionTemplate txTemplate = new TransactionTemplate(secondaryTxManager);
                T result = txTemplate.execute(status -> secondaryReadOperation.get());
                
                log.info("Secondary DB 읽기 성공 (Failover)");
                return result;
                
            } catch (Exception e2) {
                log.error("Secondary DB 읽기도 실패", e2);
                throw new DatabaseUnavailableException("모든 DB 접근 실패", e2);
            }
        }
    }
    
    /**
     * Primary에서 읽기 시도, 실패 시 Secondary로 Failover (하위 호환성)
     * 
     * Primary와 Secondary가 동일한 Supplier를 사용하는 경우 (레거시 지원)
     * 주의: 이 메서드는 Primary 실패 시에도 동일한 JPA Repository를 호출하므로
     * Secondary로 실제 Failover가 되지 않을 수 있습니다.
     * 
     * @deprecated Primary/Secondary 각각 다른 Supplier를 받는 메서드를 사용하세요.
     * @param readOperation 읽기 작업 (JPA Repository 사용)
     * @return 읽기 결과
     */
    @Deprecated
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
            // 현재는 Primary와 동일한 Repository를 사용하되 Secondary 트랜잭션으로 실행
            // 향후 Secondary Repository를 별도로 구현할 수 있음
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

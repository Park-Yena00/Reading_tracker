package com.readingtracker.server.service;

import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.repository.primary.UserRepository;
import com.readingtracker.dbms.repository.secondary.SecondaryUserDao;
import com.readingtracker.server.service.read.DualMasterReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private DualMasterReadService dualMasterReadService;
    
    @Autowired(required = false)
    private SecondaryUserDao secondaryUserDao;
    
    /**
     * 로그인 ID 중복 확인
     * @param loginId 확인할 로그인 ID
     * @return 중복 여부 (true: 중복됨, false: 사용 가능)
     */
    public boolean isLoginIdDuplicate(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }
    
    /**
     * 이메일 중복 확인
     * @param email 확인할 이메일
     * @return 중복 여부 (true: 중복됨, false: 사용 가능)
     */
    public boolean isEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }
    
    /**
     * 로그인 ID로 사용자 조회
     * @param loginId 로그인 ID
     * @return 사용자 엔티티 (없으면 null)
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    public User findByLoginId(String loginId) {
        return dualMasterReadService.readWithFailover(
            () -> userRepository.findByLoginId(loginId).orElse(null),
            () -> secondaryUserDao.findByLoginId(loginId).orElse(null)
        );
    }
    
    /**
     * 이메일로 사용자 조회
     * @param email 이메일
     * @return 사용자 엔티티 (없으면 null)
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
    
    /**
     * 로그인 ID 또는 이메일로 사용자 조회
     * @param loginIdOrEmail 로그인 ID 또는 이메일
     * @return 사용자 엔티티 (없으면 null)
     */
    public User findByLoginIdOrEmail(String loginIdOrEmail) {
        // 이메일 형식인지 확인
        if (loginIdOrEmail.contains("@")) {
            return findByEmail(loginIdOrEmail);
        } else {
            return findByLoginId(loginIdOrEmail);
        }
    }
    
    /**
     * 활성 사용자 조회 (로그인 ID)
     * @param loginId 로그인 ID
     * @return 활성 사용자 엔티티 (없으면 null)
     */
    public User findActiveUserByLoginId(String loginId) {
        return userRepository.findActiveUserByLoginId(loginId).orElse(null);
    }
    
    /**
     * 활성 사용자 조회 (이메일)
     * @param email 이메일
     * @return 활성 사용자 엔티티 (없으면 null)
     */
    public User findActiveUserByEmail(String email) {
        return userRepository.findActiveUserByEmail(email).orElse(null);
    }
    
    /**
     * 사용자 ID로 조회
     * @param id 사용자 ID
     * @return 사용자 엔티티 (없으면 null)
     */
    public User findById(Long id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id).orElse(null);
    }
}



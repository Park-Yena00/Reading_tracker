package com.readingtracker.server.controller.v1;

import com.readingtracker.server.service.AladinApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 헬스 체크 컨트롤러
 * 네트워크 상태 및 외부 서비스 연결 가능 여부 확인
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "헬스 체크 API")
public class HealthController extends BaseV1Controller {
    
    @Autowired
    private AladinApiService aladinApiService;
    
    /**
     * 알라딘 API 연결 가능 여부 확인
     * 2단계 헬스체크: 외부 서비스(Aladin API) 연결 가능 여부 확인
     * 
     * @return 200 OK (연결 가능) 또는 503 Service Unavailable (연결 불가)
     */
    @GetMapping("/aladin")
    @Operation(summary = "알라딘 API 연결 가능 여부 확인", description = "외부 서비스(Aladin API) 연결 가능 여부를 확인합니다.")
    public ResponseEntity<Void> checkAladinHealth() {
        try {
            // 알라딘 API의 가장 가벼운 요청으로 연결 테스트
            aladinApiService.testConnection();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // 연결 실패, 인증 실패, 타임아웃 등 모든 예외 처리
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}


package com.readingtracker.server.mapper;

import com.readingtracker.dbms.entity.User;
import com.readingtracker.server.controller.v1.AuthController;
import com.readingtracker.server.dto.requestDTO.RegistrationRequest;
import com.readingtracker.server.dto.responseDTO.LoginIdRetrievalResponse;
import com.readingtracker.server.dto.responseDTO.LoginResponse;
import com.readingtracker.server.dto.responseDTO.PasswordResetResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface AuthMapper {
    
    AuthMapper INSTANCE = Mappers.getMapper(AuthMapper.class);
    
    /**
     * RegistrationRequest → User Entity 변환
     * 주의: passwordHash는 Service에서 별도 처리 필요
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true) // Service에서 암호화 처리
    @Mapping(target = "role", constant = "USER")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "failedLoginCount", constant = "0")
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "devices", ignore = true)
    @Mapping(target = "refreshTokens", ignore = true)
    @Mapping(target = "userBooks", ignore = true)
    User toUserEntity(RegistrationRequest request);
    
    /**
     * User Entity → RegisterResponse 변환
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "loginId", source = "loginId")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "status", source = "status")
    AuthController.RegisterResponse toRegisterResponse(User user);
    
    /**
     * User Entity → LoginResponse.UserInfo 변환
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "loginId", source = "loginId")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    LoginResponse.UserInfo toLoginUserInfo(User user);
    
    /**
     * User Entity → LoginIdRetrievalResponse 변환
     */
    @Mapping(target = "loginId", source = "loginId")
    @Mapping(target = "email", source = "email")
    LoginIdRetrievalResponse toLoginIdRetrievalResponse(User user);
    
    /**
     * User Entity → PasswordResetResponse 변환
     */
    default PasswordResetResponse toPasswordResetResponse(User user) {
        PasswordResetResponse response = new PasswordResetResponse();
        response.setMessage("비밀번호가 성공적으로 변경되었습니다.");
        response.setLoginId(user.getLoginId());
        return response;
    }
}


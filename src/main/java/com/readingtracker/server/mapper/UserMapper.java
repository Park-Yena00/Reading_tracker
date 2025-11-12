package com.readingtracker.server.mapper;

import com.readingtracker.dbms.entity.User;
import com.readingtracker.server.controller.v1.UserController;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {
    
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);
    
    /**
     * User Entity → UserProfileResponse 변환
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "loginId", source = "loginId")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(target = "status", expression = "java(user.getStatus().name())")
    UserController.UserProfileResponse toUserProfileResponse(User user);
}


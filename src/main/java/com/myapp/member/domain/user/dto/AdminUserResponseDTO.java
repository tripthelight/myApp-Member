package com.myapp.member.domain.user.dto;

import com.myapp.member.SocialProviderType;
import com.myapp.member.domain.user.entity.UserEntity;
import com.myapp.member.domain.user.entity.UserRoleType;

import java.time.LocalDateTime;

public record AdminUserResponseDTO(
        Long id,
        String username,
        Boolean lock,
        Boolean social,
        SocialProviderType socialProviderType,
        UserRoleType roleType,
        String nickname,
        String email,
        LocalDateTime createdDate,
        LocalDateTime updatedDate
) {

    public static AdminUserResponseDTO from(UserEntity entity) {
        return new AdminUserResponseDTO(
                entity.getId(),
                entity.getUsername(),
                entity.getIsLock(),
                entity.getIsSocial(),
                entity.getSocialProviderType(),
                entity.getRoleType(),
                entity.getNickname(),
                entity.getEmail(),
                entity.getCreatedDate(),
                entity.getUpdatedDate()
        );
    }
}

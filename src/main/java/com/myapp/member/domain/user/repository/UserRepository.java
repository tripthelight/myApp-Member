package com.myapp.member.domain.user.repository;

import com.myapp.member.domain.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Boolean existsByUsername(String username);

    Optional<UserEntity> findByUsernameAndIsLockAndIsSocial(String username, Boolean isLock, Boolean isSocial);

    Optional<UserEntity> findByUsernameAndIsSocial(String username, Boolean social);

    Optional<UserEntity> findByUsernameAndIsLock(String username, Boolean isLock);

    @Query("""
            select u
            from UserEntity u
            where u.isLock = false
              and u.isSocial = false
              and (u.username = :loginId or u.email = :loginId)
            """)
    Optional<UserEntity> findLoginUser(@Param("loginId") String loginId);

    @Transactional
    void deleteByUsername(String username);
}
package com.myapp.member.domain.user.service;

import com.myapp.member.SocialProviderType;
import com.myapp.member.domain.jwt.service.JwtService;
import com.myapp.member.domain.user.dto.CustomOAuth2User;
import com.myapp.member.domain.user.dto.UserRequestDTO;
import com.myapp.member.domain.user.dto.UserResponseDTO;
import com.myapp.member.domain.user.entity.UserEntity;
import com.myapp.member.domain.user.entity.UserRoleType;
import com.myapp.member.domain.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myapp.member.domain.user.dto.AdminSummaryResponseDTO;
import com.myapp.member.domain.user.dto.AdminUserResponseDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService extends DefaultOAuth2UserService implements UserDetailsService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public UserService(PasswordEncoder passwordEncoder, UserRepository userRepository, JwtService jwtService) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public Boolean existUser(UserRequestDTO dto) {
        return userRepository.existsByUsername(dto.getUsername());
    }

    @Transactional
    public Long addUser(UserRequestDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("이미 유저가 존재합니다.");
        }

        UserEntity entity = UserEntity.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .isLock(false)
                .isSocial(false)
                .roleType(UserRoleType.USER)
                .nickname(dto.getNickname())
                .email(dto.getEmail())
                .build();

        return userRepository.save(entity).getId();
    }

    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity entity = userRepository.findLoginUser(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        return User.builder()
                .username(entity.getUsername())
                .password(entity.getPassword())
                .roles(entity.getRoleType().name())
                .accountLocked(entity.getIsLock())
                .build();
    }

    @Transactional
    public Long updateUser(UserRequestDTO dto) throws AccessDeniedException {
        String sessionUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        if (!sessionUsername.equals(dto.getUsername())) {
            throw new AccessDeniedException("본인 계정만 수정 가능");
        }

        UserEntity entity = userRepository.findByUsernameAndIsLockAndIsSocial(dto.getUsername(), false, false)
                .orElseThrow(() -> new UsernameNotFoundException(dto.getUsername()));

        entity.updateUser(dto);

        return userRepository.save(entity).getId();
    }

    @Transactional
    public Long updatePassword(UserRequestDTO dto) {
        String sessionUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity entity = userRepository.findByUsernameAndIsLockAndIsSocial(sessionUsername, false, false)
                .orElseThrow(() -> new UsernameNotFoundException(sessionUsername));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), entity.getPassword())) {
            throw new AccessDeniedException("현재 비밀번호가 일치하지 않습니다.");
        }

        entity.updatePassword(passwordEncoder.encode(dto.getPassword()));

        jwtService.removeRefreshUser(sessionUsername);

        return userRepository.save(entity).getId();
    }

    @Transactional
    public void deleteUser(UserRequestDTO dto) throws AccessDeniedException {
        SecurityContext context = SecurityContextHolder.getContext();
        String sessionUsername = context.getAuthentication().getName();
        String sessionRole = context.getAuthentication().getAuthorities().iterator().next().getAuthority();

        boolean isOwner = sessionUsername.equals(dto.getUsername());
        boolean isAdmin = sessionRole.equals("ROLE_" + UserRoleType.ADMIN.name());

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("본인 혹은 관리자만 삭제할 수 있습니다.");
        }

        userRepository.deleteByUsername(dto.getUsername());
        jwtService.removeRefreshUser(dto.getUsername());
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes;
        List<GrantedAuthority> authorities;

        String username;
        String role = UserRoleType.USER.name();
        String email;
        String nickname;

        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();

        if (registrationId.equals(SocialProviderType.NAVER.name())) {
            attributes = (Map<String, Object>) oAuth2User.getAttributes().get("response");

            if (attributes == null) {
                throw new OAuth2AuthenticationException("네이버 사용자 정보를 가져올 수 없습니다.");
            }

            Object idObj = attributes.get("id");
            Object emailObj = attributes.get("email");
            Object nicknameObj = attributes.get("nickname");

            if (idObj == null) {
                throw new OAuth2AuthenticationException("네이버 id 정보를 가져올 수 없습니다.");
            }

            username = registrationId + "_" + idObj;
            email = emailObj != null ? emailObj.toString() : null;
            nickname = nicknameObj != null ? nicknameObj.toString() : "네이버사용자";
        } else if (registrationId.equals(SocialProviderType.GOOGLE.name())) {
            attributes = (Map<String, Object>) oAuth2User.getAttributes();
            username = registrationId + "_" + attributes.get("sub");
            email = attributes.get("email").toString();
            nickname = attributes.get("name").toString();
        } else {
            throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다.");
        }

        Optional<UserEntity> entity = userRepository.findByUsernameAndIsSocial(username, true);

        if (entity.isPresent()) {
            role = entity.get().getRoleType().name();

            UserRequestDTO dto = new UserRequestDTO();
            dto.setNickname(nickname);
            dto.setEmail(email);
            entity.get().updateUser(dto);

            userRepository.save(entity.get());
        } else {
            UserEntity newUserEntity = UserEntity.builder()
                    .username(username)
                    .password("")
                    .isLock(false)
                    .isSocial(true)
                    .socialProviderType(SocialProviderType.valueOf(registrationId))
                    .roleType(UserRoleType.USER)
                    .nickname(nickname)
                    .email(email)
                    .build();

            userRepository.save(newUserEntity);
        }

        authorities = List.of(new SimpleGrantedAuthority(role));

        return new CustomOAuth2User(attributes, authorities, username);
    }

    @Transactional(readOnly = true)
    public UserResponseDTO readUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity entity = userRepository.findByUsernameAndIsLock(username, false)
                .orElseThrow(() -> new UsernameNotFoundException("해당 유저를 찾을 수 없습니다: " + username));

        return new UserResponseDTO(username, entity.getIsSocial(), entity.getNickname(), entity.getEmail());
    }

    @Transactional(readOnly = true)
    public AdminSummaryResponseDTO readAdminSummary() {
        long totalUsers = userRepository.count();
        long socialUsers = userRepository.countByIsSocial(true);
        long localUsers = userRepository.countByIsSocial(false);
        long lockedUsers = userRepository.countByIsLock(true);

        return new AdminSummaryResponseDTO(totalUsers, localUsers, socialUsers, lockedUsers);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponseDTO> readAdminUsers() {
        return userRepository.findAllByOrderByIdDesc()
                .stream()
                .map(AdminUserResponseDTO::from)
                .toList();
    }

}

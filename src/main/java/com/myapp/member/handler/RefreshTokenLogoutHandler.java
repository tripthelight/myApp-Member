package com.myapp.member.handler;

import com.myapp.member.domain.jwt.service.JwtService;
import com.myapp.member.util.JWTUtil;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RefreshTokenLogoutHandler implements LogoutHandler {

    private final JwtService jwtService;

    public RefreshTokenLogoutHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String refreshToken = extractRefreshToken(request);

        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            if (!JWTUtil.isValid(refreshToken, false)) {
                return;
            }

            jwtService.removeRefresh(refreshToken);
        } catch (Exception e) {
            // 로그아웃은 멱등 처리한다.
            // 잘못된 토큰이어도 서버가 터지지 않게 하고, Front localStorage 삭제는 진행되게 둔다.
            System.out.println("Refresh token logout failed: " + e.getMessage());
        }
    }

    private String extractRefreshToken(HttpServletRequest request) {
        String refreshHeader = request.getHeader("refresh");

        if (refreshHeader != null && !refreshHeader.isBlank()) {
            return removeBearerPrefix(refreshHeader);
        }

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            return removeBearerPrefix(authorizationHeader);
        }

        return extractRefreshTokenFromBody(request);
    }

    private String extractRefreshTokenFromBody(HttpServletRequest request) {
        try {
            ServletInputStream inputStream = request.getInputStream();
            String body = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);

            if (body == null || body.isBlank()) {
                return null;
            }

            Pattern pattern = Pattern.compile("\"refreshToken\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(body);

            if (matcher.find()) {
                return matcher.group(1);
            }

            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private String removeBearerPrefix(String token) {
        if (token == null) {
            return null;
        }

        return token.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }
}

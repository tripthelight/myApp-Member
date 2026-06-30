package com.myapp.member.filter;

import com.myapp.member.util.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class JWTFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        if (isPublicRequest(requestURI, method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");

        if (authorization == null || authorization.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Authorization 헤더가 없습니다.\"}");
            return;
        }

        if (!authorization.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Bearer 토큰 형식이 아닙니다.\"}");
            return;
        }

        String accessToken = authorization.substring(7);

        try {
            if (!JWTUtil.isValid(accessToken, true)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"토큰 만료 또는 유효하지 않은 토큰\"}");
                return;
            }

            String username = JWTUtil.getUsername(accessToken);
            String role = JWTUtil.getRole(accessToken);

            if (username == null || username.isBlank()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"토큰에 username이 없습니다.\"}");
                return;
            }

            if (role == null || role.isBlank()) {
                role = "ROLE_USER";
            }

            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
            }

            List<GrantedAuthority> authorities =
                    Collections.singletonList(new SimpleGrantedAuthority(role));

            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"유효하지 않은 accessToken입니다.\"}");
        }
    }

    private boolean isPublicRequest(String requestURI, String method) {
        if (requestURI.equals("/hc")) {
            return true;
        }

        if (requestURI.equals("/env")) {
            return true;
        }

        if (requestURI.equals("/login")) {
            return true;
        }

        if (requestURI.equals("/jwt/exchange")) {
            return true;
        }

        if (requestURI.equals("/jwt/refresh")) {
            return true;
        }

        if (requestURI.equals("/jwt/logout")) {
            return true;
        }

        if (requestURI.equals("/user") && "POST".equalsIgnoreCase(method)) {
            return true;
        }

        if (requestURI.equals("/user/exist") && "POST".equalsIgnoreCase(method)) {
            return true;
        }

        return false;
    }
}

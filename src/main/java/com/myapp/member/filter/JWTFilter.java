package com.myapp.member.filter;

import com.myapp.member.util.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
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
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();

        if (HttpMethod.OPTIONS.matches(method)) {
            return true;
        }

        if (HttpMethod.POST.matches(method) && ("/login".equals(path) || "/member/login".equals(path))) {
            return true;
        }

        if ("/hc".equals(path) || "/env".equals(path)) {
            return true;
        }

        if ("/jwt/exchange".equals(path) || "/jwt/refresh".equals(path) || "/jwt/logout".equals(path)) {
            return true;
        }

        return HttpMethod.POST.matches(method) && ("/user".equals(path) || "/user/exist".equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }

        String accessToken = authorization.substring("Bearer ".length()).trim();

        if (!JWTUtil.isValid(accessToken, true)) {
            writeUnauthorized(response);
            return;
        }

        String username = JWTUtil.getUsername(accessToken);
        String role = JWTUtil.getRole(accessToken);

        List<GrantedAuthority> authorities =
                Collections.singletonList(new SimpleGrantedAuthority(role));

        Authentication auth =
                new UsernamePasswordAuthenticationToken(username, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"INVALID_ACCESS_TOKEN\"}");
    }
}
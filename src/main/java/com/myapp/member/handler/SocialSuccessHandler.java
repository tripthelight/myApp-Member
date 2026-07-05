package com.myapp.member.handler;

import com.myapp.member.config.HttpCookieOAuth2AuthorizationRequestRepository;
import com.myapp.member.domain.jwt.service.JwtService;
import com.myapp.member.util.JWTUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Qualifier("SocialSuccessHandler")
public class SocialSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Value("${app.oauth2.success-redirect-url:http://localhost:5173/cookie}")
    private String successRedirectUrl;

    public SocialSuccessHandler(
            JwtService jwtService,
            HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository
    ) {
        this.jwtService = jwtService;
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        String username = authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        String refreshToken = JWTUtil.createJWT(username, "ROLE_" + role, false);

        jwtService.addRefresh(username, refreshToken);

        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(10);

        response.addCookie(refreshCookie);
        response.sendRedirect(resolveSuccessRedirectUrl(request));
    }

    private String resolveSuccessRedirectUrl(HttpServletRequest request) {
        String requestedSuccessRedirectUrl = authorizationRequestRepository.loadSuccessRedirectUri(request);

        if (requestedSuccessRedirectUrl == null || requestedSuccessRedirectUrl.isBlank()) {
            return successRedirectUrl;
        }

        return requestedSuccessRedirectUrl;
    }
}
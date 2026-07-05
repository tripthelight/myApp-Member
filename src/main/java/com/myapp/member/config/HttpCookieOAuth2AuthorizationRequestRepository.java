package com.myapp.member.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import org.springframework.web.util.WebUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

@Component
public class HttpCookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    public static final String OAUTH2_SUCCESS_REDIRECT_URI_COOKIE_NAME = "oauth2_success_redirect_uri";
    private static final String SUCCESS_REDIRECT_URI_PARAM_NAME = "success_redirect_uri";
    private static final int COOKIE_EXPIRE_SECONDS = 180;

    private static final Set<String> ALLOWED_SUCCESS_REDIRECT_URIS = Set.of(
            "http://127.0.0.1:5173/cookie",
            "http://localhost:5173/cookie",
            "http://127.0.0.1:8080/cookie"
    );

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

        if (cookie == null) {
            return null;
        }

        return deserialize(cookie.getValue());
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            removeAuthorizationRequestCookies(response);
            return;
        }

        Cookie cookie = new Cookie(
                OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                serialize(authorizationRequest)
        );

        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);

        response.addCookie(cookie);
        saveSuccessRedirectUri(request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        removeAuthorizationRequestCookies(response);
        return authorizationRequest;
    }

    public void removeAuthorizationRequestCookies(HttpServletResponse response) {
        expireCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        expireCookie(response, OAUTH2_SUCCESS_REDIRECT_URI_COOKIE_NAME);
    }

    public String loadSuccessRedirectUri(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, OAUTH2_SUCCESS_REDIRECT_URI_COOKIE_NAME);

        if (cookie == null) {
            return null;
        }

        String successRedirectUri = decodeString(cookie.getValue());

        if (!ALLOWED_SUCCESS_REDIRECT_URIS.contains(successRedirectUri)) {
            return null;
        }

        return successRedirectUri;
    }

    private void saveSuccessRedirectUri(HttpServletRequest request, HttpServletResponse response) {
        String successRedirectUri = request.getParameter(SUCCESS_REDIRECT_URI_PARAM_NAME);

        if (successRedirectUri == null || !ALLOWED_SUCCESS_REDIRECT_URIS.contains(successRedirectUri)) {
            return;
        }

        Cookie cookie = new Cookie(
                OAUTH2_SUCCESS_REDIRECT_URI_COOKIE_NAME,
                encodeString(successRedirectUri)
        );

        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);

        response.addCookie(cookie);
    }

    private void expireCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        byte[] serialized = SerializationUtils.serialize(authorizationRequest);
        return Base64.getUrlEncoder().encodeToString(serialized);
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        Object deserialized = SerializationUtils.deserialize(decoded);
        return (OAuth2AuthorizationRequest) deserialized;
    }

    private String encodeString(String value) {
        return Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeString(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
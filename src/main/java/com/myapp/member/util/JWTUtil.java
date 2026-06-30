package com.myapp.member.util;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JWTUtil {

    private static SecretKey secretKey;

    private static final Long accessTokenExpiresIn = 3600L * 1000; // 1시간
    // private static final Long accessTokenExpiresIn = 50L * 1000; // 50초 - TEST
    private static final Long refreshTokenExpiresIn = 604800L * 1000; // 7일

    public JWTUtil(@Value("${jwt.secret}") String secretKeyString) {
        JWTUtil.secretKey = new SecretKeySpec(
                secretKeyString.getBytes(StandardCharsets.UTF_8),
                Jwts.SIG.HS256.key().build().getAlgorithm()
        );
    }

    public static String getUsername(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("sub", String.class);
    }

    public static String getRole(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public static Boolean isValid(String token, Boolean isAccess) {
        try {
            String category = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("category", String.class);

            if (isAccess) {
                return "access".equals(category);
            }

            return "refresh".equals(category);
        } catch (Exception e) {
            return false;
        }
    }

    public static String createJWT(String username, String role, Boolean isAccess) {
        Long expiresIn = isAccess ? accessTokenExpiresIn : refreshTokenExpiresIn;
        String category = isAccess ? "access" : "refresh";

        return Jwts.builder()
                .claim("category", category)
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiresIn))
                .signWith(secretKey)
                .compact();
    }
}


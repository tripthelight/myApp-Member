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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException, IOException {

	String requestURI = request.getRequestURI();
	String method = request.getMethod();

	boolean isPublicApi =
        	requestURI.equals("/hc")
        	|| requestURI.equals("/env")
        	|| requestURI.equals("/jwt/exchange")
        	|| requestURI.equals("/jwt/refresh")
		|| (requestURI.equals("/login") && method.equals("POST"))
        	|| (requestURI.equals("/user") && method.equals("POST"))
        	|| (requestURI.equals("/user/exist") && method.equals("POST"));

	if (isPublicApi) {
    	    filterChain.doFilter(request, response);
    	    return;
	}

	String authorization = request.getHeader("Authorization");
	if (authorization == null) {
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

        // 토큰 파싱
        String accessToken = authorization.split(" ")[1];

        if (JWTUtil.isValid(accessToken, true)) {

            String username = JWTUtil.getUsername(accessToken);
            String role = JWTUtil.getRole(accessToken);

            List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role));

            Authentication auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);

        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"토큰 만료 또는 유효하지 않은 토큰\"}");
            return;
        }

    }

}

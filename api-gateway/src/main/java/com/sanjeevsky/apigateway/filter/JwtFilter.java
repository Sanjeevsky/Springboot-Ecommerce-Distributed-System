package com.sanjeevsky.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.sanjeevsky.apigateway.utils.Constants.*;

public class JwtFilter extends GenericFilterBean {
    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        String path = request.getRequestURI();
        if (path.contains("/api/v1/user-service")) {
            filterChain.doFilter(request, response);
        } else {
            String authHeader = request.getHeader(AUTHORIZATION);
            log.info(AUTH_HEADER_LOG, authHeader);
            if ("OPTIONS".equals(request.getMethod())) {
                response.setStatus(200);
                filterChain.doFilter(request, response);
            } else {
                if (authHeader == null || !authHeader.startsWith(BEARER)) {
                    throw new ServletException(AUTH_HEADER_MISSING);
                }

                String token = authHeader.substring(7);
                Claims payload;
                try {
                    payload = (Claims) Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody();
                } catch (JwtException var11) {
                    throw new ServletException(INVALID_TOKEN);
                }

                request.setAttribute(USER, payload.getSubject());
            }

            filterChain.doFilter(request, response);
        }
    }
}

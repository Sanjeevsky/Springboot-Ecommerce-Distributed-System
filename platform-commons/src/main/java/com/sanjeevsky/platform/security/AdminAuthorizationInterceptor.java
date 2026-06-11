package com.sanjeevsky.platform.security;

import com.sanjeevsky.platform.mdc.MdcConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod method = (HandlerMethod) handler;
        boolean adminOnly = method.hasMethodAnnotation(AdminOnly.class)
                || method.getBeanType().isAnnotationPresent(AdminOnly.class);
        if (!adminOnly) {
            return true;
        }

        String role = request.getHeader(MdcConstants.HEADER_USER_ROLE);
        if (!"ADMIN".equalsIgnoreCase(role == null ? "" : role.trim())) {
            throw new AdminAccessDeniedException("Administrator role is required");
        }
        return true;
    }
}

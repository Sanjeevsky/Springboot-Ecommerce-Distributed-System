package com.sanjeevsky.platform.mdc;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Populates MDC with correlationId (from X-Correlation-ID header or generated)
 * and userId (from X-User header injected by the API gateway).
 * Runs before all other filters so every log line in the request carries the context.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader(MdcConstants.HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String userId = request.getHeader(MdcConstants.HEADER_USER);

        try {
            MDC.put(MdcConstants.CORRELATION_ID, correlationId);
            if (userId != null && !userId.isBlank()) {
                MDC.put(MdcConstants.USER_ID, userId);
            }
            response.addHeader(MdcConstants.HEADER_CORRELATION_ID, correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcConstants.CORRELATION_ID);
            MDC.remove(MdcConstants.USER_ID);
        }
    }
}

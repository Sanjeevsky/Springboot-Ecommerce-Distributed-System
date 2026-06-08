package com.sanjeevsky.platform.mdc;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Propagates correlationId and userId from MDC into outgoing Feign request headers
 * so the receiving service can continue the same logging context.
 */
public class CorrelationIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(MdcConstants.CORRELATION_ID);
        if (correlationId != null) {
            template.header(MdcConstants.HEADER_CORRELATION_ID, correlationId);
        }

        String userId = MDC.get(MdcConstants.USER_ID);
        if (userId != null) {
            template.header(MdcConstants.HEADER_USER, userId);
        }
    }
}

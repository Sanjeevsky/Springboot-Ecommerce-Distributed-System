package com.sanjeevsky.catalogservice.security;

import com.sanjeevsky.platform.security.AdminAccessDeniedException;
import com.sanjeevsky.platform.security.AdminAuthorizationInterceptor;
import com.sanjeevsky.platform.security.AdminOnly;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthorizationInterceptorTest {

    private final AdminAuthorizationInterceptor interceptor = new AdminAuthorizationInterceptor();

    @Test
    void preHandle_adminOnlyMethodWithoutRole_throwsForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                handler("adminAction")))
                .isInstanceOf(AdminAccessDeniedException.class)
                .hasMessage("Administrator role is required");
    }

    @Test
    void preHandle_adminOnlyMethodWithCustomerRole_throwsForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Role", "CUSTOMER");

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                handler("adminAction")))
                .isInstanceOf(AdminAccessDeniedException.class);
    }

    @Test
    void preHandle_adminOnlyMethodWithTrimmedAdminRole_allowsRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Role", " admin ");

        assertThat(interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                handler("adminAction"))).isTrue();
    }

    @Test
    void preHandle_publicMethod_allowsRequestWithoutRole() throws Exception {
        assertThat(interceptor.preHandle(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                handler("publicAction"))).isTrue();
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new TestHandler(), TestHandler.class.getMethod(methodName));
    }

    static class TestHandler {
        @AdminOnly
        public void adminAction() {
        }

        public void publicAction() {
        }
    }
}

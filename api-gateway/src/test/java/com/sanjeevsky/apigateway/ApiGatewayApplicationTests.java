package com.sanjeevsky.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"spring.boot.admin.client.enabled=false",
		"spring.zipkin.enabled=false",
		"spring.cloud.config.enabled=false",
		"spring.cloud.config.import-check.enabled=false",
		"spring.config.import=",
		"jwt.secret=test-jwt-secret"
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}

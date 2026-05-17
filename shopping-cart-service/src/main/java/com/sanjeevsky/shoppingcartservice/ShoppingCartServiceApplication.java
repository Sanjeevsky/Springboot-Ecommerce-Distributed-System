package com.sanjeevsky.shoppingcartservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients
public class ShoppingCartServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShoppingCartServiceApplication.class, args);
	}

}

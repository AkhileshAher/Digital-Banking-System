package dev.akhileshaher.transactionservice.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "dev.akhileshaher.transactionservice.client")
public class FeignConfig {
}

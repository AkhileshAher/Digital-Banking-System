package dev.akhileshaher.frauddetectionservice.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "dev.akhileshaher.frauddetectionservice")
public class FeignConfig {
}

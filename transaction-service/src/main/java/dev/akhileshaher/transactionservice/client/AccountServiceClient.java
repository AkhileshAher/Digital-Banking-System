package dev.akhileshaher.transactionservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "account-service",url = "${account-service.url}")
public interface AccountServiceClient {

    @PutMapping("/{accountNumber}/deduct")
    String deductBalance(@PathVariable String accountNumber, @RequestParam BigDecimal amount);

}

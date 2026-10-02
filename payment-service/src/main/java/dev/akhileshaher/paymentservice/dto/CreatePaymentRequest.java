package dev.akhileshaher.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {

    @NotBlank(message = "Account Number is Required")
    private String accountNumber;

    @NotNull(message = "Amount is Required")
    @Positive(message = "Amount must be Positive")
    private BigDecimal amount;

    private String description;

}

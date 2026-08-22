package com.aryavart.dairy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "is required") String name,
        @NotBlank(message = "is required") String phone,
        String email,
        String address,
        @NotBlank(message = "is required") @Size(min = 4, message = "must be at least 4 characters") String password) {
}

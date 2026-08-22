package com.aryavart.dairy.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "is required") String phone,
        @NotBlank(message = "is required") String password) {
}

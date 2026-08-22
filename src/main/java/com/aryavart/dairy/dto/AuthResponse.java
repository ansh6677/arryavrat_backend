package com.aryavart.dairy.dto;

public record AuthResponse(String token, String id, String name, String phone, String role) {
}

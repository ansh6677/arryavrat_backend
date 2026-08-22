package com.aryavart.dairy.dto;

/** Staff login create/edit by a full-access admin. role: ADMIN (full) or VIEWER (view only). */
public record StaffRequest(String name, String loginId, String password, String role, Boolean active) {
}

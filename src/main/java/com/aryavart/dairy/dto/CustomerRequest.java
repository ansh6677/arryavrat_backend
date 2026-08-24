package com.aryavart.dairy.dto;

/** Customer add/edit by admin. If the password is left blank on add, the phone number becomes the default password. */
public record CustomerRequest(String name, String phone, String email, String address, String password, Boolean active,
                              java.util.List<String> preferredProductIds) {
}

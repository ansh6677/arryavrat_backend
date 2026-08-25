package com.aryavart.dairy.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document("users")
public class User {

    @Id
    private String id;

    private String name;

    /** Login id: phone number for customers, SUPER_ADMIN_ID from .env for the super admin */
    @Indexed(unique = true)
    private String phone;

    private String email;
    private String address;

    /** BCrypt hash — never serialized in API responses */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** ADMIN | CUSTOMER */
    private String role;

    private boolean active = true;

    /**
     * The one .env-managed administrator. Locked everywhere: no endpoint may
     * edit, deactivate or delete this account — credentials change only via
     * the .env file (synced at startup).
     */
    private boolean superAdmin = false;

    /** How this customer joined: PAGE = self-registered on the website, ADF = added by the farm. */
    private String signupSource;

    /** Products this customer usually takes — pre-ticked in the daily entry sheet. */
    private java.util.List<String> preferredProductIds;

    /** Usual daily quantity per product (productId → qty); missing entries default to 1. */
    private java.util.Map<String, Double> preferredQuantities;

    /** Stamped on every successful sign-in — the staff table's "Last sign-in". */
    private Instant lastLoginAt;

    private Instant createdAt = Instant.now();
}

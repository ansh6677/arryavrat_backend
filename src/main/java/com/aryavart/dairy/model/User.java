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

    /** How this customer joined: PAGE = self-registered on the website, ADF = added by the farm. */
    private String signupSource;

    /** Products this customer usually takes — pre-ticked in the daily entry sheet. */
    private java.util.List<String> preferredProductIds;

    private Instant createdAt = Instant.now();
}

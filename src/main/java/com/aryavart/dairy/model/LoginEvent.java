package com.aryavart.dairy.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One successful sign-in. Powers the "who was here last" cards and the
 * recent-activity feed on the Login Management page.
 */
@Data
@Document("login_events")
public class LoginEvent {

    @Id
    private String id;

    private String userId;

    private String name;

    /** Login id — the phone for customers, the staff login id for management. */
    private String loginId;

    private String role;

    /** CUSTOMER or MANAGEMENT — which door they came through. */
    private String side;

    /** Friendly device summary, e.g. "Mobile · Chrome". */
    private String device;

    /** Kept for the record; not shown in the UI. */
    private String ip;

    @Indexed
    private Instant at = Instant.now();
}

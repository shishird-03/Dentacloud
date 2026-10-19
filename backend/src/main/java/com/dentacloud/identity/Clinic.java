package com.dentacloud.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic")
public class Clinic {
    @Id UUID id;
    String name;
    String slug;
    String phone;
    String email;
    String timezone = "Asia/Kolkata";
    String currency = "INR";
    String status = "TRIAL";
    Instant trialEndsAt;
    Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getTimezone() { return timezone; }
}

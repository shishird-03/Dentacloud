package com.dentacloud.shared;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/** JWT claim names and accessors for the authenticated staff member. */
public final class Claims {
    public static final String CLINIC_ID = "clinic_id";
    public static final String MEMBER_ID = "member_id";
    public static final String ROLE = "role";
    public static final String SCOPE = "scope";

    private Claims() {}

    public static UUID userId() {
        return UUID.fromString(jwt().getToken().getSubject());
    }

    /** The clinic_member id; appointments and visits reference members, not users. */
    public static UUID memberId() {
        return UUID.fromString(jwt().getToken().getClaimAsString(MEMBER_ID));
    }

    public static boolean hasScope(String scope) {
        return jwt().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SCOPE_" + scope));
    }

    private static JwtAuthenticationToken jwt() {
        return (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    }
}

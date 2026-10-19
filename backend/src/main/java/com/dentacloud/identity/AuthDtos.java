package com.dentacloud.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request and response bodies for /auth. */
final class AuthDtos {
    private AuthDtos() {}

    record SignupRequest(
            @NotBlank @Size(max = 120) String clinicName,
            @NotBlank @Size(max = 120) String ownerName,
            @NotBlank @Email String email,
            @Pattern(regexp = "\\+?[0-9]{7,15}") String phone,
            @NotBlank @Size(min = 8, max = 128) String password,
            String deviceName, String deviceId) {}

    record LoginRequest(@NotBlank String email, @NotBlank String password, UUID clinicId,
                        String deviceName, String deviceId) {}

    record RefreshRequest(@NotBlank String refreshToken) {}

    record AuthResponse(String accessToken, String refreshToken, long expiresIn,
                        UUID userId, String userName, UUID clinicId, String clinicName,
                        UUID memberId, String role, List<String> scopes) {}
}

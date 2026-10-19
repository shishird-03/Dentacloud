package com.dentacloud.identity;

import com.dentacloud.shared.AppProperties;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.Claims;
import com.dentacloud.shared.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** 15-minute access JWTs and rotating refresh tokens with reuse detection (PRD 11, 14). */
@Service
class TokenService {
    record Issued(String accessToken, String refreshToken, long expiresIn) {}

    record Session(UUID userId, UUID clinicId, UUID memberId, String role, Iterable<String> scopes) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final JwtEncoder encoder;
    private final RefreshTokenRepository tokens;
    private final AppProperties props;

    TokenService(JwtEncoder encoder, RefreshTokenRepository tokens, AppProperties props) {
        this.encoder = encoder;
        this.tokens = tokens;
        this.props = props;
    }

    @Transactional
    Issued issue(Session s, String deviceName, String deviceId) {
        return issue(s, Ids.next(), deviceName, deviceId);
    }

    private Issued issue(Session s, UUID familyId, String deviceName, String deviceId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(s.userId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .claim(Claims.CLINIC_ID, s.clinicId().toString())
                .claim(Claims.MEMBER_ID, s.memberId().toString())
                .claim(Claims.ROLE, s.role())
                .claim(Claims.SCOPE, String.join(" ", s.scopes()))
                .build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        RefreshToken t = new RefreshToken();
        t.id = Ids.next();
        t.userId = s.userId();
        t.clinicId = s.clinicId();
        t.tokenHash = hash(refresh);
        t.familyId = familyId;
        t.deviceName = deviceName;
        t.deviceId = deviceId;
        t.expiresAt = now.plus(props.refreshTtl());
        tokens.save(t);
        return new Issued(access, refresh, props.accessTtl().toSeconds());
    }

    /**
     * Validates and consumes a refresh token. Presenting one that was already used or revoked means
     * it leaked, so the whole family is revoked. Runs in its own transaction so that revocation
     * commits even though the caller then fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    RefreshToken consume(String refreshToken) {
        RefreshToken t = tokens.findByTokenHash(hash(refreshToken)).orElseThrow(TokenService::invalid);
        Instant now = Instant.now();
        if (t.revokedAt != null) {
            tokens.revokeFamily(t.familyId, now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "refresh_reused", "Session revoked; please log in again");
        }
        if (t.expiresAt.isBefore(now)) throw invalid();
        t.revokedAt = now;
        return t;
    }

    @Transactional
    Issued rotate(RefreshToken used, Session s) {
        return issue(s, used.familyId, used.deviceName, used.deviceId);
    }

    @Transactional
    void revoke(String refreshToken) {
        tokens.findByTokenHash(hash(refreshToken)).ifPresent(t -> tokens.revokeFamily(t.familyId, Instant.now()));
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "refresh_invalid", "Invalid or expired refresh token");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

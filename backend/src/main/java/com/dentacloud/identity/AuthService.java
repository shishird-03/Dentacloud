package com.dentacloud.identity;

import com.dentacloud.identity.AuthDtos.AuthResponse;
import com.dentacloud.identity.AuthDtos.LoginRequest;
import com.dentacloud.identity.AuthDtos.SignupRequest;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
class AuthService {
    record Membership(UUID memberId, UUID clinicId, String clinicName, UUID roleId, String roleName) {}

    private static final Duration TRIAL = Duration.ofDays(14);

    private final AppUserRepository users;
    private final ClinicRepository clinics;
    private final ClinicMemberRepository members;
    private final ChairRepository chairs;
    private final RoleRepository roles;
    private final TokenService tokens;
    private final PasswordEncoder passwords;
    private final EntityManager em;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final String dummyHash;

    AuthService(AppUserRepository users, ClinicRepository clinics, ClinicMemberRepository members,
                ChairRepository chairs, RoleRepository roles, TokenService tokens, PasswordEncoder passwords,
                EntityManager em, PlatformTransactionManager txManager, ApplicationEventPublisher events) {
        this.users = users;
        this.clinics = clinics;
        this.members = members;
        this.chairs = chairs;
        this.roles = roles;
        this.tokens = tokens;
        this.passwords = passwords;
        this.em = em;
        this.tx = new TransactionTemplate(txManager);
        this.events = events;
        this.dummyHash = passwords.encode("timing-equaliser");
    }

    /** Onboarding step 1: clinic, owner login and a first chair, in one transaction as the new tenant. */
    AuthResponse signup(SignupRequest req) {
        if (users.existsByEmail(AppUser.normalizeEmail(req.email()))) {
            throw ApiException.conflict("email_taken", "An account with this email already exists");
        }
        UUID clinicId = Ids.next();
        AppUser user = new AppUser();
        Membership m = TenantContext.callAs(clinicId, () -> tx.execute(status -> {
            user.id = Ids.next();
            user.email = AppUser.normalizeEmail(req.email());
            user.phone = req.phone();
            user.fullName = req.ownerName().trim();
            user.passwordHash = passwords.encode(req.password());
            users.save(user);

            Clinic c = new Clinic();
            c.id = clinicId;
            c.name = req.clinicName().trim();
            c.slug = slug(c.name) + "-" + clinicId.toString().substring(30);
            c.email = user.email;
            c.phone = req.phone();
            c.trialEndsAt = Instant.now().plus(TRIAL);
            clinics.save(c);

            ClinicMember owner = new ClinicMember();
            owner.id = Ids.next();
            owner.clinicId = clinicId;
            owner.userId = user.id;
            owner.roleId = Role.OWNER;
            owner.isDentist = true; // most new clinics are a solo dentist-owner
            members.save(owner);

            Chair chair = new Chair();
            chair.id = Ids.next();
            chair.clinicId = clinicId;
            chair.name = "Chair 1";
            chairs.save(chair);

            events.publishEvent(new ClinicCreated(clinicId));
            return new Membership(owner.id, clinicId, c.name, Role.OWNER, "OWNER");
        }));
        return session(user, m, req.deviceName(), req.deviceId());
    }

    AuthResponse login(LoginRequest req) {
        AppUser user = users.findByEmail(AppUser.normalizeEmail(req.email())).orElse(null);
        // Hash even for unknown emails so response time does not reveal which emails exist.
        String hash = user == null || user.passwordHash == null ? dummyHash : user.passwordHash;
        boolean ok = passwords.matches(req.password(), hash);
        if (user == null || !ok || !"ACTIVE".equals(user.status)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", "Wrong email or password");
        }
        Membership m = memberships(user.id).stream()
                .filter(x -> req.clinicId() == null || x.clinicId().equals(req.clinicId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "no_membership", "You are not a member of this clinic"));
        tx.executeWithoutResult(s -> users.findById(user.id).ifPresent(u -> u.lastLoginAt = Instant.now()));
        return session(user, m, req.deviceName(), req.deviceId());
    }

    AuthResponse refresh(String refreshToken) {
        RefreshToken used = tokens.consume(refreshToken);
        AppUser user = users.findById(used.userId).orElseThrow();
        Membership m = memberships(user.id).stream().filter(x -> x.clinicId().equals(used.clinicId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "no_membership", "Access to this clinic was removed"));
        List<String> scopes = scopes(m);
        TokenService.Issued issued = tokens.rotate(used, sessionOf(user, m, scopes));
        return response(issued, user, m, scopes);
    }

    void logout(String refreshToken) {
        tokens.revoke(refreshToken);
    }

    private AuthResponse session(AppUser user, Membership m, String deviceName, String deviceId) {
        List<String> scopes = scopes(m);
        TokenService.Issued issued = tokens.issue(sessionOf(user, m, scopes), deviceName, deviceId);
        return response(issued, user, m, scopes);
    }

    private static TokenService.Session sessionOf(AppUser user, Membership m, List<String> scopes) {
        return new TokenService.Session(user.id, m.clinicId(), m.memberId(), m.roleName(), scopes);
    }

    /** Read as the member's clinic so custom (clinic-owned) roles are visible under RLS. */
    private List<String> scopes(Membership m) {
        return TenantContext.callAs(m.clinicId(), () -> tx.execute(s ->
                roles.findById(m.roleId()).map(r -> List.copyOf(r.scopes)).orElse(List.of())));
    }

    @SuppressWarnings("unchecked")
    private List<Membership> memberships(UUID userId) {
        return tx.execute(s -> ((List<Object[]>) em.createNativeQuery("select * from auth_memberships(?1)")
                .setParameter(1, userId).getResultList()).stream()
                .map(r -> new Membership((UUID) r[0], (UUID) r[1], (String) r[2], (UUID) r[3], (String) r[4]))
                .toList());
    }

    private static AuthResponse response(TokenService.Issued t, AppUser u, Membership m, List<String> scopes) {
        return new AuthResponse(t.accessToken(), t.refreshToken(), t.expiresIn(), u.id, u.fullName,
                m.clinicId(), m.clinicName(), m.memberId(), m.roleName(), scopes);
    }

    static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return s.isEmpty() ? "clinic" : s.substring(0, Math.min(40, s.length()));
    }
}

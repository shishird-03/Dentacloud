package com.dentacloud.identity;

import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Clinic profile, staff, roles and chairs. Everything here is scoped to the caller's clinic by RLS. */
@RestController
@RequestMapping("/api/v1/clinic")
class ClinicController {
    record ClinicDto(UUID id, String name, String phone, String email, String timezone, String currency, String status) {}

    record ClinicPatch(@Size(min = 1, max = 120) String name, String phone, @Email String email) {}

    record MemberDto(UUID id, UUID userId, String fullName, String email, UUID roleId, String role,
                     boolean isDentist, String color, String status) {}

    record NewMember(@NotBlank String fullName, @NotBlank @Email String email,
                     @NotBlank @Size(min = 8) String tempPassword, @NotNull UUID roleId, boolean isDentist,
                     String registrationNo) {}

    record RoleDto(UUID id, String name, boolean system, List<String> scopes) {}

    record ChairDto(UUID id, String name, boolean active) {}

    record NewChair(@NotBlank @Size(max = 40) String name) {}

    private final ClinicRepository clinics;
    private final ClinicMemberRepository members;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final ChairRepository chairs;
    private final PasswordEncoder passwords;

    ClinicController(ClinicRepository clinics, ClinicMemberRepository members, AppUserRepository users,
                     RoleRepository roles, ChairRepository chairs, PasswordEncoder passwords) {
        this.clinics = clinics;
        this.members = members;
        this.users = users;
        this.roles = roles;
        this.chairs = chairs;
        this.passwords = passwords;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ClinicDto get() {
        return dto(current());
    }

    @PatchMapping
    @PreAuthorize("hasAuthority('SCOPE_clinic.admin')")
    @Transactional
    ClinicDto patch(@Valid @RequestBody ClinicPatch p) {
        Clinic c = current();
        if (p.name() != null) c.name = p.name().trim();
        if (p.phone() != null) c.phone = p.phone();
        if (p.email() != null) c.email = p.email();
        return dto(c);
    }

    @GetMapping("/members")
    @Transactional(readOnly = true)
    List<MemberDto> members() {
        List<ClinicMember> ms = members.findAllByOrderByIdAsc();
        Map<UUID, AppUser> byId = users.findAllById(ms.stream().map(m -> m.userId).toList()).stream()
                .collect(Collectors.toMap(u -> u.id, Function.identity()));
        Map<UUID, Role> roleById = roles.findAll().stream().collect(Collectors.toMap(r -> r.id, Function.identity()));
        return ms.stream().map(m -> {
            AppUser u = byId.get(m.userId);
            return new MemberDto(m.id, m.userId, u.fullName, u.email, m.roleId, roleById.get(m.roleId).name,
                    m.isDentist, m.color, m.status);
        }).toList();
    }

    /** Adds staff with a temporary password. ponytail: email invite flow comes with the SMS/email provider. */
    @PostMapping("/members")
    @PreAuthorize("hasAuthority('SCOPE_staff.manage')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    MemberDto addMember(@Valid @RequestBody NewMember req) {
        Role role = roles.findById(req.roleId()).orElseThrow(() -> ApiException.badRequest("unknown_role", "Unknown role"));
        AppUser u = users.findByEmail(AppUser.normalizeEmail(req.email())).orElseGet(() -> {
            AppUser n = new AppUser();
            n.id = Ids.next();
            n.email = AppUser.normalizeEmail(req.email());
            n.fullName = req.fullName().trim();
            n.passwordHash = passwords.encode(req.tempPassword());
            return users.save(n);
        });
        if (members.findAllByOrderByIdAsc().stream().anyMatch(m -> m.userId.equals(u.id))) {
            throw ApiException.conflict("already_member", "This person is already on the team");
        }
        ClinicMember m = new ClinicMember();
        m.id = Ids.next();
        m.clinicId = TenantContext.require();
        m.userId = u.id;
        m.roleId = role.id;
        m.isDentist = req.isDentist();
        m.registrationNo = req.registrationNo();
        members.save(m);
        return new MemberDto(m.id, u.id, u.fullName, u.email, role.id, role.name, m.isDentist, m.color, m.status);
    }

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    List<RoleDto> roles() {
        return roles.findAll().stream().map(r -> new RoleDto(r.id, r.name, r.isSystem, List.copyOf(r.scopes))).toList();
    }

    @GetMapping("/chairs")
    @Transactional(readOnly = true)
    List<ChairDto> chairs() {
        return chairs.findAllByOrderByNameAsc().stream().map(c -> new ChairDto(c.id, c.name, c.active)).toList();
    }

    @PostMapping("/chairs")
    @PreAuthorize("hasAuthority('SCOPE_clinic.admin')")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ChairDto addChair(@Valid @RequestBody NewChair req) {
        Chair c = new Chair();
        c.id = Ids.next();
        c.clinicId = TenantContext.require();
        c.name = req.name().trim();
        chairs.save(c);
        return new ChairDto(c.id, c.name, c.active);
    }

    private Clinic current() {
        return clinics.findById(TenantContext.require()).orElseThrow(() -> ApiException.notFound("Clinic"));
    }

    private static ClinicDto dto(Clinic c) {
        return new ClinicDto(c.id, c.name, c.phone, c.email, c.timezone, c.currency, c.status);
    }
}

package com.dentacloud.identity;

import com.dentacloud.shared.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.UUID;

/** What other modules may ask about staff, chairs and the current clinic (RLS-scoped lookups). */
@Component
public class StaffDirectory {
    private final ClinicMemberRepository members;
    private final ChairRepository chairs;
    private final ClinicRepository clinics;

    StaffDirectory(ClinicMemberRepository members, ChairRepository chairs, ClinicRepository clinics) {
        this.members = members;
        this.chairs = chairs;
        this.clinics = clinics;
    }

    @Transactional(readOnly = true)
    public boolean isActiveDentist(UUID memberId) {
        return memberId != null && members.findById(memberId)
                .filter(m -> m.isDentist && "ACTIVE".equals(m.status)).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID memberId) {
        return memberId != null && members.findById(memberId).filter(m -> "ACTIVE".equals(m.status)).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isActiveChair(UUID chairId) {
        return chairId != null && chairs.findById(chairId).filter(c -> c.active).isPresent();
    }

    /** Name and time zone of the clinic bound to this thread. */
    @Transactional(readOnly = true)
    public ClinicInfo currentClinic() {
        Clinic c = clinics.findById(TenantContext.require()).orElseThrow();
        return new ClinicInfo(c.id, c.name, ZoneId.of(c.timezone));
    }

    public record ClinicInfo(UUID id, String name, ZoneId zone) {}
}

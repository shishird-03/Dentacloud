package com.dentacloud.billing;

import com.dentacloud.identity.ClinicCreated;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.SyncSource;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

interface ProcedureRepository extends JpaRepository<Procedure, UUID> {
    List<Procedure> findAllByOrderByNameAsc();
}

/** API and sync shape; field names match the Android Room entity. */
record ProcedureDto(UUID id, UUID clinicId, String code, String name, long defaultPaise, int defaultMinutes,
                    int taxBasisPoints) {
    static ProcedureDto of(Procedure p) {
        return new ProcedureDto(p.id, p.clinicId, p.code, p.name, p.defaultPaise, p.defaultMinutes, p.taxBasisPoints);
    }
}

/** The price list: seeded for every new clinic, readable by all staff, synced read-only to devices. */
@RestController
@RequestMapping("/api/v1/procedures")
class ProcedureCatalog implements SyncSource {
    /** Indian placeholder prices to validate in clinic interviews (PRD 15). Dental care is GST-exempt. */
    private static final Object[][] DEFAULTS = {
            {"CONS", "Consultation", 300_00L, 15},
            {"SCAL", "Scaling and polishing", 1_200_00L, 30},
            {"FILL", "Composite filling", 1_500_00L, 30},
            {"RCT", "Root canal treatment", 6_000_00L, 60},
            {"EXT", "Extraction", 1_000_00L, 30},
            {"CRWN", "Crown (PFM)", 8_000_00L, 45},
            {"IOPA", "X-ray (IOPA)", 300_00L, 10},
    };

    private final ProcedureRepository repo;

    ProcedureCatalog(ProcedureRepository repo) {
        this.repo = repo;
    }

    /** Runs inside the signup transaction, with the new clinic already bound as tenant. */
    @EventListener
    void seed(ClinicCreated e) {
        for (Object[] d : DEFAULTS) {
            Procedure p = new Procedure();
            p.id = Ids.next();
            p.clinicId = e.clinicId();
            p.code = (String) d[0];
            p.name = (String) d[1];
            p.defaultPaise = (Long) d[2];
            p.defaultMinutes = (Integer) d[3];
            repo.save(p);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<ProcedureDto> list() {
        return repo.findAllByOrderByNameAsc().stream().map(ProcedureDto::of).toList();
    }

    @Override
    public String entity() {
        return "procedure";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Object> load(Collection<UUID> ids) {
        Map<UUID, Object> out = new LinkedHashMap<>();
        repo.findAllById(ids).forEach(p -> out.put(p.id, ProcedureDto.of(p)));
        return out;
    }

    @Override
    public void upsert(JsonNode payload) {
        throw new ApiException(HttpStatus.FORBIDDEN, "read_only", "The price list is edited on the server only");
    }
}

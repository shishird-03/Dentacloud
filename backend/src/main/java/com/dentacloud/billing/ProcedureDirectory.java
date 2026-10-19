package com.dentacloud.billing;

import com.dentacloud.shared.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** What other modules may ask about the price list. */
@Component
public class ProcedureDirectory {
    public record ProcedureInfo(UUID id, String name, long defaultPaise, int defaultMinutes, int taxBasisPoints) {}

    private final ProcedureRepository repo;

    ProcedureDirectory(ProcedureRepository repo) {
        this.repo = repo;
    }

    /** Default chair time for a procedure; 30 minutes when none is chosen. */
    @Transactional(readOnly = true)
    public int minutesFor(UUID procedureId) {
        return procedureId == null ? 30 : get(procedureId).defaultMinutes();
    }

    @Transactional(readOnly = true)
    public ProcedureInfo get(UUID procedureId) {
        return repo.findById(procedureId)
                .map(p -> new ProcedureInfo(p.id, p.name, p.defaultPaise, p.defaultMinutes, p.taxBasisPoints))
                .orElseThrow(() -> ApiException.badRequest("unknown_procedure", "Unknown procedure"));
    }
}

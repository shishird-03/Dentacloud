package com.dentacloud.clinical;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Visits, the FDI chart and treatment plans: entities, repositories and API/sync shapes. */
final class ClinicalModel {
    private ClinicalModel() {}

    /** Chart conditions the app can draw. */
    static final Set<String> CONDITIONS = Set.of("SOUND", "CARIES", "FILLED", "CROWN", "MISSING", "RCT",
            "EXTRACT_PLANNED", "IMPLANT", "FRACTURED", "BRIDGE");

    /** FDI two-digit notation: quadrants 1-4 adult (teeth 1-8), 5-8 child (teeth 1-5). */
    static boolean validFdi(int fdi) {
        int q = fdi / 10, n = fdi % 10;
        return (q >= 1 && q <= 4 && n >= 1 && n <= 8) || (q >= 5 && q <= 8 && n >= 1 && n <= 5);
    }

    @Entity
    @Table(name = "visit")
    static class Visit {
        @Id UUID id;
        UUID clinicId;
        UUID patientId;
        UUID appointmentId;
        UUID dentistId;
        Instant visitedAt = Instant.now();
        byte[] complaintEnc;
        byte[] diagnosisEnc;
        byte[] notesEnc;
        Instant updatedAt = Instant.now();
        @Version int version;
    }

    @Entity
    @Table(name = "tooth_record")
    static class ToothRecord {
        @Id UUID id;
        UUID clinicId;
        UUID patientId;
        UUID visitId;
        short toothFdi;
        String surface;
        String condition;
        String note;
        UUID recordedBy;
        Instant recordedAt = Instant.now();
    }

    @Entity
    @Table(name = "treatment_plan")
    static class Plan {
        @Id UUID id;
        UUID clinicId;
        UUID patientId;
        String title;
        String status = "PROPOSED";
        long estimatePaise;
        UUID createdBy;
        Instant createdAt = Instant.now();
        Instant updatedAt = Instant.now();
    }

    @Entity
    @Table(name = "plan_item")
    static class PlanItem {
        @Id UUID id;
        UUID clinicId;
        UUID planId;
        UUID procedureId;
        Short toothFdi;
        String surface;
        long estPaise;
        String status = "PLANNED";
        Integer sequence;
        UUID doneVisitId;
    }

    // --- API and sync shapes (Android Room field names, epoch-millis times) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VisitDto(UUID id, UUID clinicId, @NotNull UUID patientId, UUID appointmentId, UUID dentistId, long visitedAt,
                    @Size(max = 2000) String complaint, @Size(max = 2000) String diagnosis,
                    @Size(max = 8000) String notes, long updatedAt, Integer version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToothRecordDto(UUID id, UUID clinicId, UUID patientId, UUID visitId, int toothFdi, String surface,
                          @NotBlank String condition, @Size(max = 500) String note, UUID recordedBy, long recordedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlanItemDto(UUID id, UUID clinicId, UUID planId, @NotNull UUID procedureId, Integer toothFdi, String surface,
                       @PositiveOrZero long estPaise, String status, Integer sequence, UUID doneVisitId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlanDto(UUID id, UUID clinicId, @NotNull UUID patientId, @Size(max = 120) String title, String status,
                   long estimatePaise, long createdAt, long updatedAt, List<@Valid PlanItemDto> items) {}

    record PlanItemPatch(@NotBlank String status, UUID doneVisitId) {}

    record PlanStatus(@NotBlank String status) {}

    /** Bill chosen items (all unbilled DONE/PLANNED items when empty). */
    record InvoicePlan(List<UUID> itemIds, @PositiveOrZero long discountPaise) {}

    interface VisitRepository extends JpaRepository<Visit, UUID> {
        List<Visit> findByPatientIdOrderByVisitedAtDesc(UUID patientId);
    }

    interface ToothRecordRepository extends JpaRepository<ToothRecord, UUID> {
        List<ToothRecord> findByPatientIdOrderByRecordedAtAsc(UUID patientId);
    }

    interface PlanRepository extends JpaRepository<Plan, UUID> {
        List<Plan> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
    }

    interface PlanItemRepository extends JpaRepository<PlanItem, UUID> {
        List<PlanItem> findByPlanIdInOrderBySequenceAsc(Collection<UUID> planIds);
    }
}

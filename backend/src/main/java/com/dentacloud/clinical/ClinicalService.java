package com.dentacloud.clinical;

import com.dentacloud.audit.AuditService;
import com.dentacloud.billing.BillingDtos.InvoiceDto;
import com.dentacloud.billing.BillingDtos.InvoiceLineDto;
import com.dentacloud.billing.BillingDtos.NewInvoice;
import com.dentacloud.billing.InvoiceService;
import com.dentacloud.billing.ProcedureDirectory;
import com.dentacloud.clinical.ClinicalModel.InvoicePlan;
import com.dentacloud.clinical.ClinicalModel.Plan;
import com.dentacloud.clinical.ClinicalModel.PlanDto;
import com.dentacloud.clinical.ClinicalModel.PlanItem;
import com.dentacloud.clinical.ClinicalModel.PlanItemDto;
import com.dentacloud.clinical.ClinicalModel.PlanItemRepository;
import com.dentacloud.clinical.ClinicalModel.PlanRepository;
import com.dentacloud.clinical.ClinicalModel.ToothRecord;
import com.dentacloud.clinical.ClinicalModel.ToothRecordDto;
import com.dentacloud.clinical.ClinicalModel.ToothRecordRepository;
import com.dentacloud.clinical.ClinicalModel.Visit;
import com.dentacloud.clinical.ClinicalModel.VisitDto;
import com.dentacloud.clinical.ClinicalModel.VisitRepository;
import com.dentacloud.identity.StaffDirectory;
import com.dentacloud.patient.PatientDirectory;
import com.dentacloud.shared.ApiException;
import com.dentacloud.shared.Claims;
import com.dentacloud.shared.FieldCrypto;
import com.dentacloud.shared.Ids;
import com.dentacloud.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class ClinicalService {
    private static final Set<String> PLAN_STATUSES = Set.of("PROPOSED", "ACCEPTED", "IN_PROGRESS", "DONE", "DEFERRED");
    private static final Set<String> ITEM_STATUSES = Set.of("PLANNED", "DONE", "DEFERRED");
    private static final Set<String> SURFACES = Set.of("M", "O", "D", "B", "L");

    private final VisitRepository visits;
    private final ToothRecordRepository chart;
    private final PlanRepository plans;
    private final PlanItemRepository items;
    private final PatientDirectory patients;
    private final StaffDirectory staff;
    private final ProcedureDirectory procedures;
    private final InvoiceService invoices;
    private final FieldCrypto crypto;
    private final AuditService audit;

    ClinicalService(VisitRepository visits, ToothRecordRepository chart, PlanRepository plans, PlanItemRepository items,
                    PatientDirectory patients, StaffDirectory staff, ProcedureDirectory procedures,
                    InvoiceService invoices, FieldCrypto crypto, AuditService audit) {
        this.visits = visits;
        this.chart = chart;
        this.plans = plans;
        this.items = items;
        this.patients = patients;
        this.staff = staff;
        this.procedures = procedures;
        this.invoices = invoices;
        this.crypto = crypto;
        this.audit = audit;
    }

    // --- visits (notes are last-writer-wins; the audit log keeps who changed what and when) ---

    @Transactional
    VisitDto saveVisit(VisitDto in) {
        Visit v = in.id() == null ? null : visits.findById(in.id()).orElse(null);
        if (v == null) {
            requirePatient(in.patientId());
            v = new Visit();
            v.id = in.id() == null ? Ids.next() : in.id();
            v.clinicId = TenantContext.require();
            v.patientId = in.patientId();
            v.dentistId = in.dentistId() == null ? Claims.memberId() : in.dentistId();
            if (!staff.isActiveDentist(v.dentistId)) throw ApiException.badRequest("unknown_dentist", "Not an active dentist of this clinic");
            v.appointmentId = in.appointmentId();
            if (in.visitedAt() > 0) v.visitedAt = Instant.ofEpochMilli(in.visitedAt());
            audit.record("CREATE", "visit", v.id, null);
        } else {
            audit.record("UPDATE", "visit", v.id, Map.of("fields", List.of("complaint", "diagnosis", "notes")));
        }
        v.complaintEnc = crypto.encrypt(in.complaint());
        v.diagnosisEnc = crypto.encrypt(in.diagnosis());
        v.notesEnc = crypto.encrypt(in.notes());
        v.updatedAt = Instant.now();
        visits.saveAndFlush(v);
        return dto(v);
    }

    @Transactional(readOnly = true)
    VisitDto visit(UUID id) {
        return dto(visits.findById(id).orElseThrow(() -> ApiException.notFound("Visit")));
    }

    @Transactional(readOnly = true)
    List<VisitDto> visitsOf(UUID patientId) {
        return visits.findByPatientIdOrderByVisitedAtDesc(patientId).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    List<VisitDto> loadVisits(Collection<UUID> ids) {
        return visits.findAllById(ids).stream().map(this::dto).toList();
    }

    // --- chart: append-only; the current state of a tooth is its latest records ---

    @Transactional
    List<ToothRecordDto> addRecords(UUID patientId, List<ToothRecordDto> in) {
        requirePatient(patientId);
        List<ToothRecordDto> out = new ArrayList<>();
        for (ToothRecordDto r : in) {
            if (r.id() != null && chart.existsById(r.id())) continue; // retried upload
            if (!ClinicalModel.validFdi(r.toothFdi())) throw ApiException.badRequest("bad_tooth", "Not an FDI tooth number: " + r.toothFdi());
            if (r.surface() != null && !SURFACES.contains(r.surface())) throw ApiException.badRequest("bad_surface", "Surface must be M, O, D, B or L");
            if (!ClinicalModel.CONDITIONS.contains(r.condition())) throw ApiException.badRequest("bad_condition", "Unknown condition " + r.condition());
            ToothRecord t = new ToothRecord();
            t.id = r.id() == null ? Ids.next() : r.id();
            t.clinicId = TenantContext.require();
            t.patientId = patientId;
            t.visitId = r.visitId();
            t.toothFdi = (short) r.toothFdi();
            t.surface = r.surface();
            t.condition = r.condition();
            t.note = r.note();
            t.recordedBy = Claims.memberId();
            if (r.recordedAt() > 0) t.recordedAt = Instant.ofEpochMilli(r.recordedAt());
            chart.save(t);
            out.add(dto(t));
        }
        chart.flush();
        if (!out.isEmpty()) audit.record("CHART", "patient", patientId, Map.of("records", out.size()));
        return out;
    }

    @Transactional(readOnly = true)
    List<ToothRecordDto> chartOf(UUID patientId) {
        return chart.findByPatientIdOrderByRecordedAtAsc(patientId).stream().map(ClinicalService::dto).toList();
    }

    @Transactional(readOnly = true)
    List<ToothRecordDto> loadRecords(Collection<UUID> ids) {
        return chart.findAllById(ids).stream().map(ClinicalService::dto).toList();
    }

    // --- treatment plans ---

    /** Creates or replaces a plan and its items. Items already billed keep their price. */
    @Transactional
    PlanDto savePlan(PlanDto in) {
        requirePatient(in.patientId());
        Plan p = in.id() == null ? null : plans.findById(in.id()).orElse(null);
        if (p == null) {
            p = new Plan();
            p.id = in.id() == null ? Ids.next() : in.id();
            p.clinicId = TenantContext.require();
            p.patientId = in.patientId();
            p.createdBy = Claims.memberId();
        }
        p.title = in.title();
        if (in.status() != null) setPlanStatus(p, in.status());
        plans.save(p); // before its items, so the plan row exists when they are inserted
        long estimate = 0;
        int seq = 0;
        for (PlanItemDto d : in.items() == null ? List.<PlanItemDto>of() : in.items()) {
            PlanItem it = d.id() == null ? null : items.findById(d.id()).orElse(null);
            if (it == null) {
                it = new PlanItem();
                it.id = d.id() == null ? Ids.next() : d.id();
                it.clinicId = p.clinicId;
                it.planId = p.id;
            }
            procedures.get(d.procedureId()); // must exist in this clinic's price list
            if (d.toothFdi() != null && !ClinicalModel.validFdi(d.toothFdi())) throw ApiException.badRequest("bad_tooth", "Not an FDI tooth number");
            it.procedureId = d.procedureId();
            it.toothFdi = d.toothFdi() == null ? null : d.toothFdi().shortValue();
            it.surface = d.surface();
            it.estPaise = d.estPaise();
            it.status = d.status() != null && ITEM_STATUSES.contains(d.status()) ? d.status() : it.status;
            it.sequence = d.sequence() == null ? seq : d.sequence();
            it.doneVisitId = d.doneVisitId();
            items.save(it);
            estimate += it.estPaise;
            seq++;
        }
        p.estimatePaise = estimate;
        p.updatedAt = Instant.now();
        plans.saveAndFlush(p);
        items.flush();
        audit.record("SAVE", "treatment_plan", p.id, Map.of("items", seq));
        return dto(p);
    }

    @Transactional
    PlanDto updatePlanStatus(UUID planId, String status) {
        Plan p = plans.findById(planId).orElseThrow(() -> ApiException.notFound("Treatment plan"));
        setPlanStatus(p, status);
        p.updatedAt = Instant.now();
        return dto(p);
    }

    @Transactional
    PlanDto updateItem(UUID itemId, String status, UUID doneVisitId) {
        if (!ITEM_STATUSES.contains(status)) throw ApiException.badRequest("bad_status", "Unknown item status " + status);
        PlanItem it = items.findById(itemId).orElseThrow(() -> ApiException.notFound("Plan item"));
        it.status = status;
        it.doneVisitId = doneVisitId;
        Plan p = plans.findById(it.planId).orElseThrow();
        p.updatedAt = Instant.now();
        if ("DONE".equals(status) && "ACCEPTED".equals(p.status)) p.status = "IN_PROGRESS";
        items.flush();
        return dto(p);
    }

    /** F8: invoice from plan items. Each item can be billed once (unique plan_item_id on invoice lines). */
    @Transactional
    InvoiceDto invoicePlan(UUID planId, InvoicePlan req, String idempotencyKey) {
        Plan p = plans.findById(planId).orElseThrow(() -> ApiException.notFound("Treatment plan"));
        List<PlanItem> chosen = items.findByPlanIdInOrderBySequenceAsc(List.of(planId)).stream()
                .filter(i -> req.itemIds() == null || req.itemIds().isEmpty() ? !"DEFERRED".equals(i.status) : req.itemIds().contains(i.id))
                .toList();
        if (chosen.isEmpty()) throw ApiException.badRequest("nothing_to_bill", "No plan items to bill");
        List<InvoiceLineDto> lines = chosen.stream().map(i -> {
            var proc = procedures.get(i.procedureId);
            String what = i.toothFdi == null ? proc.name() : proc.name() + " (tooth " + i.toothFdi + ")";
            return new InvoiceLineDto(null, null, i.id, what, 1, i.estPaise, proc.taxBasisPoints());
        }).toList();
        return invoices.issue(new NewInvoice(null, p.patientId, req.discountPaise(), lines), idempotencyKey);
    }

    @Transactional(readOnly = true)
    List<PlanDto> plansOf(UUID patientId) {
        return plans.findByPatientIdOrderByCreatedAtDesc(patientId).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    List<PlanDto> loadPlans(Collection<UUID> ids) {
        return plans.findAllById(ids).stream().map(this::dto).toList();
    }

    // --- mapping ---

    private void setPlanStatus(Plan p, String status) {
        if (!PLAN_STATUSES.contains(status)) throw ApiException.badRequest("bad_status", "Unknown plan status " + status);
        p.status = status;
    }

    private void requirePatient(UUID patientId) {
        if (!patients.exists(patientId)) throw ApiException.badRequest("unknown_patient", "Unknown patient");
    }

    private VisitDto dto(Visit v) {
        return new VisitDto(v.id, v.clinicId, v.patientId, v.appointmentId, v.dentistId, v.visitedAt.toEpochMilli(),
                crypto.decrypt(v.complaintEnc), crypto.decrypt(v.diagnosisEnc), crypto.decrypt(v.notesEnc),
                v.updatedAt.toEpochMilli(), v.version);
    }

    private static ToothRecordDto dto(ToothRecord t) {
        return new ToothRecordDto(t.id, t.clinicId, t.patientId, t.visitId, t.toothFdi, t.surface, t.condition, t.note,
                t.recordedBy, t.recordedAt.toEpochMilli());
    }

    private PlanDto dto(Plan p) {
        Map<UUID, List<PlanItem>> byPlan = items.findByPlanIdInOrderBySequenceAsc(List.of(p.id)).stream()
                .collect(Collectors.groupingBy(i -> i.planId));
        List<PlanItemDto> its = byPlan.getOrDefault(p.id, List.of()).stream()
                .map(i -> new PlanItemDto(i.id, i.clinicId, i.planId, i.procedureId, i.toothFdi == null ? null : (int) i.toothFdi,
                        i.surface, i.estPaise, i.status, i.sequence, i.doneVisitId))
                .toList();
        return new PlanDto(p.id, p.clinicId, p.patientId, p.title, p.status, p.estimatePaise,
                p.createdAt.toEpochMilli(), p.updatedAt.toEpochMilli(), its);
    }
}

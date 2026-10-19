package com.dentacloud.billing;

import com.dentacloud.identity.StaffDirectory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;

/** Dashboard numbers (F11), in the clinic's own time zone. */
@RestController
@RequestMapping("/api/v1/reports")
class ReportController {
    record DailyCollection(LocalDate date, long collectedPaise, long refundedPaise, int payments) {}

    record Dues(long duePaise, int openInvoices) {}

    private final JdbcTemplate jdbc;
    private final StaffDirectory staff;

    ReportController(JdbcTemplate jdbc, StaffDirectory staff) {
        this.jdbc = jdbc;
        this.staff = staff;
    }

    @GetMapping("/daily-collection")
    @PreAuthorize("hasAuthority('SCOPE_report.read')")
    @Transactional(readOnly = true)
    DailyCollection dailyCollection(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ZoneId zone = staff.currentClinic().zone();
        LocalDate day = date == null ? LocalDate.now(zone) : date;
        return jdbc.queryForObject("select coalesce(sum(amount_paise) filter (where kind = 'PAYMENT'), 0),"
                        + " coalesce(sum(amount_paise) filter (where kind = 'REFUND'), 0), count(*)"
                        + " from payment_entry where recorded_at >= ? and recorded_at < ?",
                (rs, i) -> new DailyCollection(day, rs.getLong(1), rs.getLong(2), rs.getInt(3)),
                Timestamp.from(day.atStartOfDay(zone).toInstant()), Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant()));
    }

    @GetMapping("/dues")
    @PreAuthorize("hasAuthority('SCOPE_report.read')")
    @Transactional(readOnly = true)
    Dues dues() {
        return jdbc.queryForObject("select coalesce(sum(i.total_paise - coalesce(p.paid, 0)), 0), count(*) from invoice i"
                        + " left join (select invoice_id, sum(case when kind = 'PAYMENT' then amount_paise else -amount_paise end) paid"
                        + " from payment_entry group by invoice_id) p on p.invoice_id = i.id"
                        + " where i.status in ('ISSUED', 'PARTIAL')",
                (rs, i) -> new Dues(rs.getLong(1), rs.getInt(2)));
    }
}

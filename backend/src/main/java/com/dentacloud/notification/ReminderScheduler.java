package com.dentacloud.notification;

import com.dentacloud.scheduling.AppointmentScheduled;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * F10: SMS reminders at T-24 h and T-2 h, plus a sync ping to staff devices. Runs inside the booking
 * transaction. A reschedule simply adds new reminders: the worker drops any whose time no longer matches.
 */
@Component
class ReminderScheduler {
    static final List<Duration> LEADS = List.of(Duration.ofHours(24), Duration.ofHours(2));

    private final Outbox outbox;

    ReminderScheduler(Outbox outbox) {
        this.outbox = outbox;
    }

    @EventListener
    void on(AppointmentScheduled e) {
        Instant now = Instant.now();
        for (Duration lead : LEADS) {
            Instant at = e.startAt().minus(lead);
            if (at.isAfter(now)) {
                outbox.enqueue(Outbox.REMINDER_SMS, Map.of("appointmentId", e.appointmentId().toString(),
                        "startAt", e.startAt().toEpochMilli(), "lead", lead.toHours() + "h"), at);
            }
        }
        outbox.enqueue(Outbox.SYNC_PUSH, Map.of("reason", "appointment"), now);
    }
}

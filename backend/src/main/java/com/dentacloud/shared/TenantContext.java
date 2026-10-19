package com.dentacloud.shared;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The clinic the current thread works for. Set only from the verified JWT (TenantFilter) or
 * explicitly by background jobs; TenantTransactionManager copies it into Postgres for RLS.
 */
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static UUID current() {
        return CURRENT.get();
    }

    public static UUID require() {
        UUID id = CURRENT.get();
        if (id == null) throw new IllegalStateException("No tenant bound to this thread");
        return id;
    }

    static void set(UUID clinicId) {
        CURRENT.set(clinicId);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** Runs work as the given clinic, restoring whatever was bound before. */
    public static <T> T callAs(UUID clinicId, Supplier<T> work) {
        UUID previous = CURRENT.get();
        CURRENT.set(clinicId);
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static void runAs(UUID clinicId, Runnable work) {
        callAs(clinicId, () -> {
            work.run();
            return null;
        });
    }
}

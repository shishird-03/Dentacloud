package com.dentacloud.shared;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/** UUIDv7 everywhere (PRD 10), so ids sort by creation time and clients can mint them offline. */
public final class Ids {
    private Ids() {}

    public static UUID next() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}

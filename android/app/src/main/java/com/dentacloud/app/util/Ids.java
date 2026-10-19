package com.dentacloud.app.util;

import com.github.f4b6a3.uuid.UuidCreator;

/** Client-generated UUIDv7 ids so records can be created offline without collisions (PRD 9). */
public final class Ids {
    private Ids() {}

    public static String uuid7() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }
}

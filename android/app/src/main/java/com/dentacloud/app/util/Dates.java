package com.dentacloud.app.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Storage is epoch millis (UTC); display is in the device zone. */
public final class Dates {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMM, h:mm a");

    private Dates() {}

    public static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    public static long startOfDay(LocalDate d) {
        return d.atStartOfDay(zone()).toInstant().toEpochMilli();
    }

    public static long toMillis(LocalDateTime t) {
        return t.atZone(zone()).toInstant().toEpochMilli();
    }

    public static LocalDateTime toLocal(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone());
    }

    public static String day(LocalDate d) {
        return DAY.format(d);
    }

    public static String time(long millis) {
        return TIME.format(toLocal(millis));
    }

    public static String dayTime(long millis) {
        return DAY_TIME.format(toLocal(millis));
    }
}

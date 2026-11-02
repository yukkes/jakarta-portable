package com.example.app.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * The application's time zone. Order dates, billing months, audit timestamps and session expiry are all decided in it.
 *
 * <p>{@code LocalDate.now()} and friends use the JVM default zone, which differs between runtimes (UTC in containers,
 * the host setting on a developer PC). The same request would then get a different order date or "last month" per
 * runtime, so the zone is configured: the system property {@code app.time-zone}, then the environment variable
 * {@code APP_TIME_ZONE}, otherwise {@value #DEFAULT_ZONE}.
 *
 * <p>Entity callbacks ({@link Auditable}) use it too, so it is static rather than a CDI bean.
 */
public final class BusinessTime {

    static final String DEFAULT_ZONE = "Asia/Tokyo";

    private static volatile ZoneId zone;

    private BusinessTime() {}

    /**
     * Returns the configured zone, read once and then cached.
     *
     * @return the zone
     */
    public static ZoneId zone() {
        ZoneId value = zone;
        if (value == null) {
            value = resolve(BusinessTime::lookup);
            zone = value;
        }
        return value;
    }

    /**
     * Returns the current time in {@link #zone()}; use it instead of {@code LocalDateTime.now()}.
     *
     * @return the current date and time
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(zone());
    }

    /**
     * Returns today in {@link #zone()}; use it instead of {@code LocalDate.now()}.
     *
     * @return the current date
     */
    public static LocalDate today() {
        return LocalDate.now(zone());
    }

    /**
     * Returns this month in {@link #zone()}; use it instead of {@code YearMonth.now()}.
     *
     * @return the current month
     */
    public static YearMonth currentMonth() {
        return YearMonth.now(zone());
    }

    /** Reads {@code time-zone} from {@code source}. {@value #DEFAULT_ZONE} if unset. */
    static ZoneId resolve(Function<String, Optional<String>> source) {
        String id = source.apply("time-zone").orElse(DEFAULT_ZONE);
        try {
            return ZoneId.of(id.trim());
        } catch (DateTimeException exception) {
            throw new IllegalStateException("app.time-zone is not a valid zone id: " + id, exception);
        }
    }

    private static Optional<String> lookup(String key) {
        String value = System.getProperty("app." + key);
        if (value == null || value.isBlank()) {
            value = System.getenv("APP_" + key.toUpperCase(Locale.ROOT).replace('-', '_'));
        }
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}

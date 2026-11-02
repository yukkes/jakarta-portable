package com.example.app.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BusinessTimeTest {

    @Test
    void defaultsToTokyoRatherThanTheJvmZone() {
        assertEquals(ZoneId.of("Asia/Tokyo"), BusinessTime.resolve(key -> Optional.empty()));
    }

    @Test
    void takesTheConfiguredZone() {
        assertEquals(ZoneId.of("UTC"), BusinessTime.resolve(key -> Optional.of(" UTC ")));
        assertEquals(ZoneId.of("Europe/Berlin"), BusinessTime.resolve(key -> Optional.of("Europe/Berlin")));
    }

    @Test
    void rejectsAnUnknownZoneAtStartRatherThanFallingBack() {
        assertThrows(IllegalStateException.class, () -> BusinessTime.resolve(key -> Optional.of("Mars/Olympus")));
    }
}

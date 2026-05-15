package com.llf.assistant.semantic;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiAssistantTimeResolverTest {

    private final AiAssistantTimeResolver resolver = new AiAssistantTimeResolver();

    @Test
    void resolve_lastWeek_shouldUsePreviousMondayToCurrentMonday() {
        AiAssistantIntentFields fields = resolve("上周我参加了哪些会议");
        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);

        assertEquals("last_week", fields.getTimeRangeLabel());
        assertEquals(thisMonday.minusDays(7) + " 00:00:00", fields.getDateFrom());
        assertEquals(thisMonday + " 00:00:00", fields.getDateTo());
    }

    @Test
    void resolve_nextWeek_shouldUseNextMondayToFollowingMonday() {
        AiAssistantIntentFields fields = resolve("下周我有哪些日程");
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        assertEquals("next_week", fields.getTimeRangeLabel());
        assertEquals(nextMonday + " 00:00:00", fields.getDateFrom());
        assertEquals(nextMonday.plusDays(7) + " 00:00:00", fields.getDateTo());
    }

    @Test
    void resolve_specificDate_shouldUseWholeDayAndNotParseDateAsClockRange() {
        AiAssistantIntentFields fields = resolve("2026-05-15有哪些会议");

        assertEquals("2026-05-15", fields.getMeetingDate());
        assertEquals("2026-05-15 00:00:00", fields.getDateFrom());
        assertEquals("2026-05-16 00:00:00", fields.getDateTo());
    }

    @Test
    void resolve_tomorrowPointHourRange_shouldUseExactWindow() {
        AiAssistantIntentFields fields = resolve("明天9点到11点有哪些会议室可以用");
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertEquals(tomorrow.toString(), fields.getMeetingDate());
        assertEquals("09:00", fields.getStartClock());
        assertEquals("11:00", fields.getEndClock());
        assertEquals(tomorrow + " 09:00:00", fields.getDateFrom());
        assertEquals(tomorrow + " 11:00:00", fields.getDateTo());
    }

    private AiAssistantIntentFields resolve(String text) {
        AiAssistantIntentFields fields = new AiAssistantIntentFields();
        resolver.resolve(text, text, fields);
        return fields;
    }
}

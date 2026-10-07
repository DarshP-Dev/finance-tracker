package com.financetracker.backend.services.market;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UsEquityMarketSessionTests {
    @ParameterizedTest
    @CsvSource({
        "2026-10-07T13:29:59Z,false", "2026-10-07T13:30:00Z,true",
        "2026-10-07T19:59:59Z,true", "2026-10-07T20:00:00Z,false",
        "2026-10-10T16:00:00Z,false", "2026-10-11T16:00:00Z,false",
        "2026-01-05T14:29:59Z,false", "2026-01-05T14:30:00Z,true",
        "2026-01-05T20:59:59Z,true", "2026-01-05T21:00:00Z,false",
        "2026-01-01T16:00:00Z,false", "2026-01-19T16:00:00Z,false",
        "2026-02-16T16:00:00Z,false", "2026-04-03T16:00:00Z,false",
        "2026-05-25T16:00:00Z,false", "2026-06-19T16:00:00Z,false",
        "2026-07-03T16:00:00Z,false", "2026-09-07T16:00:00Z,false",
        "2026-11-26T16:00:00Z,false", "2026-12-25T16:00:00Z,false",
        "2026-11-27T17:59:59Z,true", "2026-11-27T18:00:00Z,false",
        "2026-12-24T17:59:59Z,true", "2026-12-24T18:00:00Z,false",
        "2027-06-18T16:00:00Z,false", "2027-07-05T16:00:00Z,false",
        "2027-12-24T16:00:00Z,false", "2027-12-31T16:00:00Z,true",
        "2028-07-03T16:59:59Z,true", "2028-07-03T17:00:00Z,false"
    })
    void regularSessionRespectsEasternTimeDstHolidaysAndEarlyClosures(String timestamp, boolean expected) {
        assertThat(UsEquityMarketSession.isOpen(Instant.parse(timestamp))).isEqualTo(expected);
    }
}

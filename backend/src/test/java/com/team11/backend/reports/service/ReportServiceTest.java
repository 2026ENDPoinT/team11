package com.team11.backend.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    @Test
    void regularity_identicalBedtimes_is100() {
        assertThat(ReportService.regularityPct(List.of(1380, 1380, 1380))).isEqualTo(100);
    }

    @Test
    void regularity_averageDeviationOf30Minutes_is50() {
        // 23:00(1380), 00:00(1440) → 중앙값 1410, 평균 절대편차 30 → 100 − 50
        assertThat(ReportService.regularityPct(List.of(1380, 1440))).isEqualTo(50);
    }

    @Test
    void regularity_deviationOverAnHour_clampsToZero() {
        assertThat(ReportService.regularityPct(List.of(1320, 1560, 1320, 1560))).isEqualTo(0); // ±120분
    }

    @Test
    void regularity_outlierDoesNotDominateMedian() {
        // 6일 23:00 + 1일 03:00(1620): 중앙값 1380, MAD = 240/7 ≈ 34 → 43
        assertThat(ReportService.regularityPct(List.of(1380, 1380, 1380, 1380, 1380, 1380, 1620))).isEqualTo(43);
    }

    @Test
    void regularity_fewerThanTwoLogs_isNull() {
        assertThat(ReportService.regularityPct(List.of())).isNull();
        assertThat(ReportService.regularityPct(List.of(1380))).isNull();
    }
}

package com.team11.backend.timetable.config;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 학기 기간은 자주 안 바뀌어서 DB 대신 고정 설정값으로 둔다. 바뀌면 이 값만 고치면 됨(application.yaml app.semesters). */
@ConfigurationProperties(prefix = "app")
public record SemesterProperties(List<SemesterConfig> semesters) {

    public record SemesterConfig(String code, LocalDate startDate, LocalDate endDate) {
    }

    /** date가 속한 학기, 없으면 date 이후 가장 가까운 학기(방학 중 다음 학기 시간표 미리보기용), 둘 다 없으면 null. */
    public String resolveCodeFor(LocalDate date) {
        return semesters.stream()
                .filter(c -> !date.isBefore(c.startDate()) && !date.isAfter(c.endDate()))
                .findFirst()
                .or(() -> semesters.stream()
                        .filter(c -> c.startDate().isAfter(date))
                        .min(Comparator.comparing(SemesterConfig::startDate)))
                .map(SemesterConfig::code)
                .orElse(null);
    }
}

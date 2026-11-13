package com.team11.backend.timetable.config;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 학기 기간은 자주 안 바뀌어서 DB 대신 고정 설정값(application.yaml app.semesters). */
@ConfigurationProperties(prefix = "app")
public record SemesterProperties(List<SemesterConfig> semesters) {

    public record SemesterConfig(String code, LocalDate startDate, LocalDate endDate) {

        public boolean contains(LocalDate date) {
            return !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }

    public Optional<SemesterConfig> find(String code) {
        return semesters.stream().filter(c -> c.code().equals(code)).findFirst();
    }

    /** date가 속한 학기, 없으면 date 이후 가장 가까운 학기(방학 중 다음 학기 미리보기용). */
    public Optional<SemesterConfig> resolveFor(LocalDate date) {
        return semesters.stream().filter(c -> c.contains(date)).findFirst()
                .or(() -> semesters.stream()
                        .filter(c -> c.startDate().isAfter(date))
                        .min(Comparator.comparing(SemesterConfig::startDate)));
    }

    /** 방학 중엔 수업이 없으므로 실제로 그 날짜를 포함하는 학기만. 플랜의 첫 수업 계산용. */
    public Optional<SemesterConfig> containing(LocalDate date) {
        return semesters.stream().filter(c -> c.contains(date)).findFirst();
    }
}

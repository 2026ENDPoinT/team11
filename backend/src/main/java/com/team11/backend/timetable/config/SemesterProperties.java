package com.team11.backend.timetable.config;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 학기 기간은 자주 안 바뀌어서 DB 대신 고정 설정값으로 둔다. 바뀌면 이 값만 고치면 됨(application.yaml app.semesters). */
@ConfigurationProperties(prefix = "app")
public record SemesterProperties(List<SemesterConfig> semesters) {

    public record SemesterConfig(String code, LocalDate startDate, LocalDate endDate) {
    }
}

package com.team11.backend.logs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** 앱이 HealthKit(HKCategoryTypeIdentifierSleepAnalysis)에서 읽어 밤 단위로 묶은 취침/기상 목록. */
public record SleepLogImportRequest(@NotNull @Size(min = 1, max = 100) List<@Valid Night> nights) {

    public record Night(@NotNull OffsetDateTime bedAt, @NotNull OffsetDateTime wakeAt) {
    }
}

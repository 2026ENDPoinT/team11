package com.team11.backend.logs.dto;

import com.team11.backend.logs.domain.SleepStageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

/** 애플워치(HealthKit)/갤럭시워치(Health Connect) 등 앱이 기기에서 읽어 올린 수면 데이터를 한 번에 올리는 배치 동기화. */
public record SyncWearableSleepLogsRequest(
        @NotEmpty @Valid List<Entry> logs
) {

    public record Entry(
            @NotNull OffsetDateTime bedAt,
            @NotNull OffsetDateTime wakeAt,
            @Min(1) @Max(5) Integer quality,
            /** 실제 수면 단계 구간(각성/램/비램). 기기가 단계를 못 주면 비워도 된다. */
            @Valid List<Stage> stages
    ) {
        public List<Stage> stagesOrEmpty() {
            return stages != null ? stages : List.of();
        }
    }

    public record Stage(
            @NotNull SleepStageType stage,
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt
    ) {
    }
}

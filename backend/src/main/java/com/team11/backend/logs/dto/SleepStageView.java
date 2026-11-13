package com.team11.backend.logs.dto;

import com.team11.backend.logs.domain.SleepStage;
import com.team11.backend.logs.domain.SleepStageType;
import java.time.OffsetDateTime;

public record SleepStageView(SleepStageType stage, OffsetDateTime startAt, OffsetDateTime endAt) {

    public static SleepStageView from(SleepStage stage) {
        return new SleepStageView(stage.getStage(), stage.getStartAt(), stage.getEndAt());
    }
}

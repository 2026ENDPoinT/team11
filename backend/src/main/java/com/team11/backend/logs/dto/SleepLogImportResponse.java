package com.team11.backend.logs.dto;

import java.util.List;

/** imported = 새로 저장, skipped = 이미 그 기상일 기록이 있어 건너뜀(수동 입력 우선), invalid = 시간 범위 오류 인덱스. */
public record SleepLogImportResponse(int imported, int skipped, List<Integer> invalid) {
}

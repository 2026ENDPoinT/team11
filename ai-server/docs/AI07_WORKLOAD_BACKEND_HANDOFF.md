# AI-07 2.0 백엔드 변경 인계

모델 `exam-sleep-workload-2.0.0`, 정책 `workload-balanced-2`. 통합 AI 서버의 `/ai/v5/plan/exam-sleep`, `/ai/v5/plan/exam-sleep/from-backend` 경로와 인증·sourceRevision·격리 실행은 그대로다. [전체 API 계약](../dummy/docs/AI-07_BACKEND_CONTRACT.md), [OpenAPI](AI-07_OPENAPI.json), [연구·설계 근거](../dummy/docs/AI07_WORKLOAD_REVISION.md)를 참고한다.

## 필수 적용

1. **최소 5시간 검증·클라이언트 필터 제거.** minSleepMin/minMainSleepOpportunityMin은 이전 요청 호환용이며 새 모델이 무시한다. 신규 요청에서는 생략 또는 0을 권장한다. 별도의 명시적 사용자 수면 제한이 꼭 필요한 제품이라면 이 필드를 재사용하지 말고 계약을 별도로 정의해야 한다.
2. 준비 상태 enum에 `STARTING_HEAVY`를 추가한다. 문구 예: “거의 시작하지 않았고, 남은 내용이 많거나 어렵다.” 기존 STARTING은 “거의 시작하지 않았다.” 별도 난이도 설문 없이 준비 선택 화면의 한 항목만 추가한다. `readiness-copy-2`를 저장한다. 기존 enum 값과 copy-1 요청도 계속 처리한다.
3. 응답 status에 `TRADEOFF`, selected.strategy에 `NO_SLEEP`, `NAP_ONLY`를 허용한다. 새 실행은 LIMITED_WINDOW를 만들지 않는다. NO_SLEEP도 selected가 null이 아닐 수 있다. 저장·렌더링 시 수면 구간 유무를 실제 timeline에서 확인한다.
4. selected와 alternative의 `workloadAssessment`, 루트 `workload`, 추가 quality/diagnostics/warnings를 수용한다. ALL/SOME/NO_ASSUMPTIONS는 공학적 작업 예산의 가정별 결과다. “완주 확률 80%”, “다 외울 수 있음”으로 바꾸지 않는다.
5. NO_SLEEP면 수면 알람을 새로 만들지 않는다. 추천 요청 자체가 기존 알람 취소·일정 변경을 뜻하지 않는다. 기존 apply 트랜잭션에서 사용자 선택·revision을 확인한다. 짧은 수면·긴 각성·미교정 경고를 누락하지 않는다.
6. modelVersion과 policyVersion을 저장하고 이전 추천 캐시와 분리한다. worstCaseRegret의 척도가 절대 차이에서 시나리오 최선 대비 상대 차이로 바뀌어 이전 값과 직접 비교하지 않는다.

## 선택적으로 전달할 수 있는 정보

`selection.remainingStudyHours`를 백엔드 스냅샷에 추가했다. 새 필수 입력이 아니다. 기존 학습/할 일 시스템 등에서 이미 추정한 값이 있을 때만 사용하고, 웨어러블 수면만으로 이 값을 생성하지 않는다.

```json
{
  "preparationState": "STARTING_HEAVY",
  "examNature": "RECALL",
  "assessedAt": "2026-11-17T01:00:00+09:00",
  "wordingVersion": "readiness-copy-2",
  "remainingStudyHours": {
    "lower": 4,
    "upper": 8,
    "source": "BACKEND_ESTIMATE"
  }
}
```

단위는 휴식·집중 상태에서의 남은 작업시간이다. 범위는 0.1–200시간, lower≤upper, source는 BACKEND_ESTIMATE 또는 PRIOR_SESSION_ESTIMATE다. 없으면 준비 상태별 넓은 공학적 범위로 비교하고 WORKLOAD_NOT_MEASURED를 반환한다. assessedAt이 6시간보다 오래되면 이 범위를 사용하지 않고 STALE_WORKLOAD_ESTIMATE_IGNORED를 반환한다. DTO에서 임의의 실제 측정 확률로 해석하지 않는다.

정규화 경로에서는 동일 객체를 `preparation.remainingStudyHours`로 보낸다. 시험 길이는 `target.durationMin`(1–480분, 기본 60)이며 네이티브 경로는 시험 endAt에서 자동으로 계산한다. 추천 timeline은 계속 시험 시작 전까지만 반환한다.

## 주요 응답 의미

| 항목 | 처리 |
|---|---|
| workload.source | BACKEND_RANGE / READINESS_ENGINEERING_RANGE |
| workload.measured | 항상 false. 현재 모델의 구간은 실측 완료량이 아님 |
| workload.completionProbability | null. 확률 UI로 바꾸지 않음 |
| workload.postExamRecoveryCredited | false. 시험 뒤 회복을 시험 전 이익으로 넣지 않음 |
| workloadAssessment.remainingFocusedHoursRange | 가정별 남은 집중 작업시간. 벽시계 시간 아님 |
| workloadAssessment.newMaterialCoverageRange | 신규 자료를 다룬 분량의 모델 내 범위. 숙달·정답률 아님 |
| quality.shortSleepDoseCalibration | NOT_IDENTIFIED. 특히 짧은 수면의 개인 최적량 미교정 |
| diagnostics.searchComplete | false. 연속 시간 전체의 최적해 보증이 아님 |
| diagnostics.maxWakeHoursAssumingScheduledSleep | 수면 기회에 잠든다고 가정한 최대 각성 간격. 실제 각성 시간의 보증 아님 |
| LEGACY_SLEEP_MINIMUM_IGNORED | 구 최소 수면 필드는 무시됐음 |
| LONG_WAKE_TRADEOFF_NOT_SAFETY_VALIDATED | 긴 각성이 포함된 안. 실행 가능성·안전성 미검증 |
| SHORT_SLEEP_DOSE_NOT_IDENTIFIED | 선택된 짧은 수면 기회에 대한 정밀 용량 효과 미교정 |

## 저장과 마이그레이션

추천 JSON을 저장하면 새 필드는 보존하고, enum 컬럼이면 새 값들을 migration에 추가한다. 준비 선택 필드, wordingVersion, 선택적 추정 범위·출처·assessedAt, modelVersion/policyVersion은 기존 revision과 함께 저장한다. 범위가 없다고 0시간으로 채우지 않는다. 정확한 테이블·DDL은 실제 Spring/DB 스키마에서 결정해야 한다.

이 저장소에는 Spring/모바일/실 DB 소스가 없어 위 DTO·DDL·화면 변경을 적용하지 못했다. 구현 완료한 범위는 **통합 AI 서버의 실제 입력 어댑터·모델·정규화 API·OpenAPI·예제·HTTP 검증**이다. 별도 신규 서버나 외부 LLM 호출은 필요 없다.

이전 [백엔드 인계서](../dummy/docs/exam_sleep_v3/03_BACKEND_HANDOFF.md)의 revision·멱등·알림·삭제 처리 등 공통 적용 사항은 유지하되 최소 수면·상태 enum 설명은 이 문서와 현재 API 계약을 우선한다.

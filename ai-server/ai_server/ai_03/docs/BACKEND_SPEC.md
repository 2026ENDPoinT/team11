# AI-03 백엔드 연동 명세

`POST /ai/v1/plan/exam-sleep`에 `Content-Type: application/json`과 서버 내부 인증 헤더 `X-Internal-Key`를 보낸다. 요청 추적용 `X-Request-Id`는 선택이다. 본문 한도는 1 MiB, AI-03 동시 계산 슬롯은 1개, 기본 계산 제한은 10초다.

Spring이 일정·설정·수면 기록을 조회하고 **계산용 스냅샷**을 구성한다. AI 서버는 전달된 스냅샷으로 계산하고 결과 JSON을 반환한다. 기존 AI-03 요청 형식에는 HTTP 410을 반환한다.

## Spring Swagger 연동

공개 Swagger의 DTO를 확인했다. Spring은 각 응답의 `data` 안에 있는 실제 객체와 목록을 모아 `SLEEPSYNC_EXAM_SNAPSHOT_V1`로 보낸다. `data`·`error` 응답 봉투는 스냅샷에 포함하지 않는다. 아래 DTO는 지원 필드가 채워진다는 가정으로 연결했다. [Spring Swagger](https://team11-h10w.onrender.com/swagger-ui/index.html#/) · [AI-03 OpenAPI](../../../docs/AI-03_OPENAPI.json)

| Spring 공개 경로 | 확인된 DTO와 전달 위치 |
|---|---|
| GET `/api/v1/users/me` | `UserProfileResponse` → `userProfile`. `settings`의 수면 필요량·입면·통학·준비·버퍼와 상위 `prepMinEstimate`를 사용한다. |
| GET `/api/v1/events` | `EventView` 목록 → `events`. 시험 종류, 시작·종료, 종일 및 반복 정보를 사용한다. |
| GET `/api/v1/timetable`, GET `/api/v1/semesters` | `TimetableEntryView` → `timetableEntries`, `SemesterView` → `semesters`. `semester`로 학기 기간을 연결한다. 대상 학기 수업만 보낼 때는 Spring에서 `semester`를 생략할 수 있다. |
| GET `/api/v1/sleep-logs` | `SleepLogView` 목록 → `sleepLogs`. 종료된 세션과 `SleepStageView`의 단계·시작·종료를 사용한다. `durationMin`의 기준이 DTO에 표시되지 않아 실수면 총량으로 해석하지 않는다. |
| POST `/api/v1/users/me/onboarding` | `OnboardingRequest`에 `weekdayBedTime`·`weekdayWakeTime`과 휴일 시각이 있다. 공개 프로필 조회 DTO에는 이 시각이 없다. Spring이 보유한 시각을 보내려면 `usualSleep: {usualBedtime, usualWakeTime}`을 사용한다. 생략하면 적격 세션에서 추정한다. |

**Swagger의 데이터만으로 모든 입력을 채울 수는 없다.** `preparationState`와 `examNature`는 공개 DTO에 없어 추천을 요청할 때 함께 받아야 한다. `asOfAt`, `sourceRevision`, `selection.assessedAt`은 Spring이 요청 문맥에서 생성한다. 공개 API에서 성인 확인을 검증하지 못하면 `populationScope`는 기본값 `ADULT_UNVERIFIED`가 적용되고 주수면 기준안을 우선한다.

공개 수면 조회 DTO에는 관측 완전성, 기기 계열, 수면 시간의 측정 기준이 없다. 관측 범위를 따로 확인한 경우 `historyCoverage`를 보낼 수 있다. 생략하면 최근 28일 범위에서 기록 있음은 `PARTIAL`, 없음은 `NONE`으로 처리한다. 기기 계열이 필요한 개인 수면 단계 기상 조정이나 측정 기준이 명확한 실수면 총량을 쓰려면 해당 정보를 확보해 아래 `EXAM_SLEEP_BACKEND_V1` 계약으로 보낸다. 미래 낮잠 허용 창과 개별 카페인 섭취의 시각·mg도 공개 DTO에서 확인되지 않았다. 낮잠 창은 실제 가능한 시간대를 확보한 경우에만 추가한다. 온보딩의 `caffeinePerDay`는 시각과 용량이 있는 관측으로 변환하지 않는다.

**호출 순서**는 Spring 집계 → `POST /ai/v1/plan/exam-sleep` → Swagger DTO 필드 추출 → 기존 백엔드 스냅샷 전처리 → AI-03 모델 → 응답 JSON이다. Spring에서는 `loginId`, `nickname`, 수업명과 일정 메모처럼 계산에 쓰지 않는 필드를 생략할 수 있다. [실행 가능한 Spring 호출 JSON](../../../docs/examples/ai03-spring-snapshot-request.json) · [Swagger DTO 변환 코드](../spring_snapshot.py)

```powershell
curl.exe --fail-with-body -X POST http://127.0.0.1:8000/ai/v1/plan/exam-sleep -H "Content-Type: application/json" -H "X-Internal-Key: $env:AI_INTERNAL_KEY" --data-binary "@docs/examples/ai03-spring-snapshot-request.json"
```

## 1. Spring이 넘기는 JSON

아래는 기존 `EXAM_SLEEP_BACKEND_V1` 계약이다. `asOfAt`, `sourceRevision`, `selection`, `events`, `sleepLogs`, `historyCoverage`가 필수다. 타임스탬프는 오프셋을 포함한 RFC 3339 형식으로 보낸다. 대상 시험은 요청 시각 이후 최대 72시간 안에 있어야 한다.

| 데이터 | 보낼 필드 |
|---|---|
| 시험 준비 | `selection.preparationState`, `examNature`, `assessedAt`. 선택적으로 `wordingVersion`, `remainingStudyHours: {lower, upper, source}` |
| 시험과 일정 | `events[]`의 `eventId`, `type`, `startAt`. 선택적으로 `endAt`, `allDay`, `repeat`, `occurrenceKey`. 선택적으로 루트의 `targetEventId`, `targetOccurrenceKey` |
| 수업·반복 예외 | 선택 `timetableEntries[]`의 `entryId`, `dayOfWeek`, `startTime`, `endTime`, 선택 학기 범위. `eventExceptions[]`의 `eventId`, `occurrenceDate`, 선택 취소·변경 시각 |
| 사용자 설정 | 선택 `profile`의 `usualBedtime`, `usualWakeTime`, `sleepNeedMin`, `sleepLatencyMin`, `commuteMin`, `prepMin` 또는 `prepMinEstimate`, `bufferMin` |
| 수면 기록 | `sleepLogs[]`의 `sleepLogId`, `bedAt`, `wakeAt`. 선택적으로 `durationMin`, `durationBasis`, `sessionKind`, `deviceFamily`, `stages[]`, 중복 판별용 공급자·원본 ID·수정·삭제 시각. 기록이 없으면 빈 배열 |
| 관측 상태 | `historyCoverage: {startAt, endAt, state}`, 선택 `lastSyncedAt`. 상태는 `COMPLETE`, `PARTIAL`, `NONE`. 선택 `calendarState`, `populationScope`, `randomSeed` |
| 낮잠 | 선택 `includeNaps`, `napWindows[]: {startAt, endAt}`, 과거 `napLogs[]` |

준비 상태는 `STARTING`, `STARTING_HEAVY`, `SEEN_NOT_RECALLABLE`, `PATCHING_GAPS`, `FINAL_REVIEW` 중 하나다. 시험 성격은 `RECALL`, `MIXED`, `PROBLEM_SOLVING` 중 하나다. 남은 공부시간 범위는 이미 추정값이 있는 경우에만 보낸다. `source`는 `BACKEND_ESTIMATE` 또는 `PRIOR_SESSION_ESTIMATE`다.

다음 JSON은 [실행 가능한 전체 요청](../../../docs/examples/ai07-backend-request.json)에서 선택 필드를 줄인 예시다.

```json
{
  "contractVersion": "EXAM_SLEEP_BACKEND_V1",
  "asOfAt": "2026-11-13T17:00:00+09:00",
  "sourceRevision": 43,
  "populationScope": "ADULT_VERIFIED",
  "selection": {
    "preparationState": "SEEN_NOT_RECALLABLE",
    "examNature": "RECALL",
    "assessedAt": "2026-11-13T16:59:00+09:00"
  },
  "profile": {
    "usualBedtime": "00:30", "usualWakeTime": "07:30",
    "commuteMin": 40, "prepMinEstimate": 50, "bufferMin": 10
  },
  "events": [
    {"eventId": 101, "type": "EXAM", "startAt": "2026-11-14T09:00:00+09:00"}
  ],
  "sleepLogs": [],
  "historyCoverage": {
    "startAt": "2026-11-13T00:00:00+09:00",
    "endAt": "2026-11-13T17:00:00+09:00",
    "state": "NONE"
  },
  "includeNaps": false
}
```

## 2. 모델 입력으로 전처리

| 백엔드 입력 | 정규화 후 모델 입력 |
|---|---|
| `events`와 대상 ID | 반복 일정과 예외를 적용한다. 지정한 시험 또는 가장 가까운 유효 시험을 `target`으로 선택한다. 시험 종료가 없으면 시험 길이를 60분으로 둔다. |
| 일정·시간표·준비·통학 | 대상 시험 전까지 겹치는 부분을 잘라 `fixedIntervals`로 만든다. `prepMinEstimate`가 있으면 `prepMin`보다 우선한다. 예시의 준비·통학·여유 합계 100분은 11/14 07:20–09:00에 한 번 예약된다. |
| `selection` | `preparation.state`와 `target.examNature`로 매핑한다. 평가 시각이 6시간보다 오래되면 남은 분량 추정은 사용하지 않고 준비 상태의 불확실성을 넓힌다. |
| `sleepLogs`·`napLogs` | 같은 원본의 최신 기록을 남기고 삭제 기록과 겹치는 세션을 정리한다. 겹치면 웨어러블을 우선한다. `CORE`는 `LIGHT`로 매핑하고 `INBED`는 단계에서 제외한다. `ASLEEP_ESTIMATE`로 표시한 시간은 `asleepDurationMin`이 된다. |
| `includeNaps`·`napWindows` | 허용 설정과 창이 모두 있으면 `ALLOWED`, 허용 설정만 있으면 `OPTIONAL_ONLY`, 그 외는 `DISABLED`로 변환한다. 실제 낮잠은 `ALLOWED`일 때 창 안에 배치한다. |

반복 일정은 `NONE`, `DAILY`, `WEEKLY`를 지원한다. 종일 일정은 계산에서 제외하며 월별 반복은 Spring에서 발생 건으로 펼쳐 보낸다. 일정 제목, 사용자 식별정보와 원시 센서는 스냅샷에 넣을 필요가 없다. 상세 검증과 배열 제한은 OpenAPI 및 어댑터를 참고한다.

Spring이 이미 정규화한 경우 같은 경로에 `EXAM_SLEEP_MINIMAL_V1`을 보낼 수 있다. 이때는 `target`, `preparation`, `sleepHistory`, `historyCoverage`, `fixedIntervals`, `constraints`를 직접 구성한다. 준비·통학 시간도 `fixedIntervals`에 포함해야 한다. 이 계약의 선택 필드 `optionalObservations`에서 과거 카페인 관측을 전달할 수 있다. [정규화 요청 JSON](../../../docs/examples/ai07-request.json) · [변환 코드](../backend.py) · [입력 검증](../schemas.py)

## 3. 백엔드가 받는 JSON

응답은 `{modelVersion, result}`다. 아래는 **전체 백엔드 요청 예제**를 현재 라우팅 함수에 넣어 얻은 응답에서 주요 필드와 수면 구간만 발췌한 것이다. 실제 `timeline`에는 요청부터 시험 시작까지 모든 구간이 시간순으로 들어간다.

```json
{
  "modelVersion": "exam-sleep-workload-2.0.0",
  "result": {
    "contractVersion": "EXAM_SLEEP_MINIMAL_V1",
    "sourceRevision": 43,
    "status": "READY",
    "selected": {
      "candidateKey": "candidate-26",
      "strategy": "MAIN_ONLY",
      "timeline": [
        {
          "kind": "MAIN_SLEEP",
          "startAt": "2026-11-13T19:00:00+09:00",
          "endAt": "2026-11-13T23:00:00+09:00"
        }
      ],
      "totals": {
        "sleepOpportunityMin": 240.0,
        "studyAvailableMin": 530.0,
        "fixedMin": 160.0,
        "wakeBufferMin": 30.0,
        "restMin": 0.0
      }
    },
    "predictedExamScore": null,
    "individualSuccessProbability": null
  }
}
```

| 응답 필드 | 처리 |
|---|---|
| `status` | `READY`는 수면 계획 선택, `BASELINE`은 기준안 선택, `TRADEOFF`는 추가 수면 없는 안 선택, `NO_FEASIBLE_PLAN`은 선택안 없음이다. 마지막 두 상태도 HTTP 200으로 올 수 있다. |
| `selected`, `alternative` | 각각 독립적인 계획 또는 `null`이다. 알람은 사용자가 적용한 계획의 `MAIN_SLEEP`·`NAP` 종료 시각에서 구성한다. `NO_SLEEP` 또는 `selected: null`에는 새 수면 알람을 만들지 않는다. |
| `timeline`, `totals` | `MAIN_SLEEP`, `NAP`, `FIXED`, `STUDY_AVAILABLE`, `WAKE_BUFFER`, `REST`를 구분한다. 수면 합계는 배정 시간이며 공부 합계는 일정상 확보한 시간이다. |
| `workload`, `workloadAssessment`, `comparison` | 가정별 작업시간 범위와 기준안 비교를 표시한다. 시나리오 범위는 개인의 확률이나 성적을 뜻하지 않는다. |
| `reasons`, `reasonCodes`, `warnings` | 선택 이유와 수면·기록 관련 주의사항을 전달한다. `quality`, `diagnostics`, `evidence`와 해시는 검증·추적에 사용할 수 있다. |

대표 오류는 입력 형식 `400`, 인증 실패 `401`, 구 계약 `410`, 72시간 이내 대상 시험 부재 등 `422`, 계산 슬롯 부족 또는 모델 장애 `503`, 계산 제한 초과 `504`다. 오류 응답의 `error.code`로 분기한다. [응답 조립 코드](../service.py) · [계산 플로우](FLOW.md)

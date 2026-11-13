# AI-03 Spring 연동 인계서

Spring에서 대상 시험, 사용자 설정, 일정, 시간표, 수면 기록을 모아 **`POST /ai/v1/plan/exam-sleep` 한 번**으로 보낸다. 헤더는 `Content-Type: application/json`, `X-Internal-Key`이며 `X-Request-Id`는 선택이다. 본문 한도는 1 MiB, AI 계산 기본 제한은 10초다.

## 1. Spring에서 추가할 필드

| 필드 | 형식과 범위 | 값의 출처 |
|---|---|---|
| `contractVersion` | 문자열 고정값 `SLEEPSYNC_EXAM_SNAPSHOT_V1` | Spring 호출 코드 |
| `selection.preparationState` | `STARTING` 거의 처음, `STARTING_HEAVY` 거의 처음이고 분량이 많거나 어려움, `SEEN_NOT_RECALLABLE` 봤지만 기억이 불확실, `PATCHING_GAPS` 빈틈 복습, `FINAL_REVIEW` 최종 확인 | 추천을 요청할 때 사용자 선택 |
| `selection.examNature` | `RECALL` 암기, `MIXED` 혼합, `PROBLEM_SOLVING` 문제풀이 | 추천을 요청할 때 사용자 선택 |
| `selection.assessedAt` | 오프셋 포함 RFC 3339 문자열. `asOfAt` 이하여야 한다. | 두 선택을 받은 시각 |
| `asOfAt` | 오프셋 포함 RFC 3339 문자열 | Spring이 입력을 모은 기준 시각 |
| `sourceRevision` | 정수 `0..9007199254740991` | Spring이 입력 묶음에 붙이는 번호. 응답에서 같은 값으로 돌려준다. |

선택 필드 `targetEventId`는 기존 시험 ID를 사용한다. 특정 시험에서 시작한 호출에는 그 ID를 보낸다. 생략 시 72시간 안의 가장 가까운 시각 지정 시험을 선택한다. `populationScope`는 성인이 확인되면 `ADULT_VERIFIED`를 보내고, 생략하면 `ADULT_UNVERIFIED`로 계산해 기준안을 우선한다.

`selection.remainingStudyHours`는 기존 추정치가 있을 때만 `{"lower":1.5,"upper":3,"source":"BACKEND_ESTIMATE"}` 형태로 보낸다. 범위는 `0.1 ≤ lower ≤ upper ≤ 200`시간, 출처는 `BACKEND_ESTIMATE` 또는 `PRIOR_SESSION_ESTIMATE`다. 평가 후 6시간을 넘긴 추정치는 계산에서 제외한다.

## 2. 요청 데이터

아래 배열은 Spring 조회 응답의 `data`에서 꺼낸 목록이다. 필수 배열에 해당 자료가 없으면 `[]`를 보낸다. DTO의 이름·로그인 ID·일정 메모는 생략할 수 있다.

| JSON 필드 | 필수 여부와 범위 |
|---|---|
| `userProfile.settings` | 객체 필수. 분 단위 설정은 `sleepNeedMin` 0–720, `sleepLatencyMin` 0–120, `commuteMin`·`prepMin` 0–240, `bufferMin` 0–120. 상위 `prepMinEstimate`가 있으면 준비시간에 우선 적용한다. |
| `events[]` | 필수 배열, 최대 500개. 각 객체는 `eventId`, `type`, `startAt` 필수. `endAt`, `allDay`, `repeat: {freq, interval, until}` 선택. 대상 시험은 `EXAM`이고 요청 기준 이후 72시간 이내여야 한다. 반복은 `NONE`, `DAILY`, `WEEKLY`를 지원한다. |
| `timetableEntries[]` | 필수 배열, 최대 100개. `entryId`, `dayOfWeek`(`MON`–`SUN`), `startTime`, `endTime`(`HH:mm` 또는 `HH:mm:ss`) 필수. `semester`가 있으면 대응하는 학기 객체가 필요하다. |
| `semesters[]` | 최대 100개. 학기 연결 시 `semester`, `startDate`, `endDate`를 보낸다. 학기 날짜는 `YYYY-MM-DD`다. |
| `sleepLogs[]` | 필수 배열, 최대 200개. 종료 세션의 `sleepLogId`, `bedAt`, `wakeAt`을 보낸다. `source`, `status`, `stages[]: {stage,startAt,endAt}`를 함께 보낼 수 있다. 단계는 `AWAKE`, `LIGHT`, `DEEP`, `REM`이며 세션 안에서 서로 겹치지 않아야 한다. 최근 28일을 사용하며 정규화 후 최대 100세션·단계 총 10,000개다. |
| `usualSleep` | 선택. 평소 시각을 확보했으면 `{usualBedtime, usualWakeTime}`을 `HH:mm`으로 보낸다. |
| `historyCoverage` | 선택. `{startAt,endAt,state}`에서 `state`는 `COMPLETE`, `PARTIAL`, `NONE`이다. 생략하면 기록 유무에 따라 `PARTIAL` 또는 `NONE`으로 처리한다. |
| `includeNaps`, `napWindows[]` | 선택. 낮잠을 허용할 때 `true`와 실제 가능한 `{startAt,endAt}` 목록을 함께 보낸다. 최대 30개다. |

Spring이 기존 DTO를 담아 보내는 전체 JSON과 해당 요청으로 생성한 응답이다. [전체 요청 JSON](../../../docs/examples/ai03-spring-snapshot-request.json) · [전체 실제 응답 JSON](../../../docs/examples/ai03-spring-snapshot-response.json)

## 3. 응답 처리

응답은 `{ "modelVersion": "exam-sleep-workload-2.0.0", "result": { ... } }` 구조다. `result.contractVersion`은 정규화 계약인 `EXAM_SLEEP_MINIMAL_V1`이며 `result.sourceRevision`은 요청값을 반환한다.

| 필드 | 형식과 처리 |
|---|---|
| `result.status` | `READY` 추천안, `BASELINE` 기준안, `TRADEOFF` 추가 수면 없는 안, `NO_FEASIBLE_PLAN` 선택안 없음. 네 상태 모두 정상 계산이면 HTTP 200이다. |
| `result.selected`, `result.alternative` | 각각 별도의 계획 객체 또는 `null`. 계획 객체에 `candidateKey`, `strategy`, `cycleMode`, `timeline`, `totals`가 들어간다. 전략은 `MAIN_ONLY`, `MAIN_NAP`, `NAP_ONLY`, `NO_SLEEP`이다. |
| `timeline[]` | `{kind,startAt,endAt}`. 시각은 오프셋 포함 문자열이며 `kind`는 `MAIN_SLEEP`, `NAP`, `STUDY_AVAILABLE`, `FIXED`, `WAKE_BUFFER`, `REST` 중 하나다. |
| `totals` | 숫자형 분 단위 합계 `sleepOpportunityMin`, `studyAvailableMin`, `fixedMin`, `wakeBufferMin`, `restMin`. |
| `reasons`, `reasonCodes`, `warnings` | 문자열 배열. 사용자 설명에는 `reasons`를 사용한다. |
| `comparison`, `workload`, `selected.workloadAssessment` | 기준안 비교와 가정별 작업시간 범위. 계획이나 평가 값이 없는 경우 일부 필드는 빠질 수 있다. |

오류 응답은 `{"error":{"code":"INVALID_INPUT","message":"…"}}` 형태이며 `details`가 추가될 수 있다. 입력 오류 400, 내부 인증 실패 401, 대상 시험 없음 422, 모델 사용 불가·슬롯 부족 503, 계산 제한 초과 504를 구분한다.

[상세 전처리와 계약](BACKEND_SPEC.md) · [OpenAPI](../../../docs/AI-03_OPENAPI.json)

# AI-03 백엔드 연결 계약 — 현재 실행본

모델 `exam-sleep-workload-2.0.0`, 정책 `workload-balanced-2`, 정규화 계약 `EXAM_SLEEP_MINIMAL_V1`, 변환 계약 `EXAM_SLEEP_BACKEND_V1`. 기존 요청 필드는 계속 받지만 최소 수면 필드는 더 이상 제약으로 적용하지 않는다. [변경 인계서](AI07_WORKLOAD_BACKEND_HANDOFF.md)를 함께 적용한다.

## 호출

| 경로 | 용도 |
|---|---|
| POST `/ai/v1/plan/exam-sleep` | 두 계약 모두 지원. 구 AI-03 요청 형식은 410 RETIRED_CONTRACT |

두 새 경로 모두 기존 통합서버에서 제공한다. 헤더 `Content-Type: application/json`, `X-Internal-Key`, 선택 `X-Request-Id`를 사용한다. 본문 1MiB, 동시 1개, worker 기본 10초(`SLEEP_MEMORY_TIMEOUT_SECONDS`), Spring read timeout은 12초 이상으로 둔다. 인증키는 서버 환경에 저장하며 앱에 넣지 않는다.

AI는 DB 조회·저장·외부 API 호출을 하지 않는다. Spring이 DB에서 자동 정보를 집계해 한 번에 보낸다. 어댑터는 필드를 변환하는 순수 함수이며 실제 Spring DTO/DB migration을 대신 적용하지 않는다.

OpenAPI 3.1: [스키마](AI-07_OPENAPI.json). 두 요청 예제와 정규화·네이티브 입력 타입을 포함한다. 시각 범위·시간 보존 등 교차 필드 규칙은 실행 검증기가 추가 검사한다.

## 사용자 입력

학습 관련 수동 입력은 다음 두 개뿐이다.

- 준비 상태: STARTING / STARTING_HEAVY / SEEN_NOT_RECALLABLE / PATCHING_GAPS / FINAL_REVIEW. STARTING_HEAVY는 “거의 처음이고 분량이 많거나 어렵다”라는 한 선택지다.
- 시험 성격: RECALL / MIXED / PROBLEM_SOLVING.

진도·숙련도·난이도·범위·공부시간·문항 진단을 요구하지 않는다. 기존 준비/이동 설정·워치·일정 연동 값을 자동으로 사용한다. 새 문구는 wordingVersion=readiness-copy-2로 저장하고 기존 copy-1도 받는다. 이미 백엔드에 유효한 남은 공부시간 추정 범위가 있을 때만 선택적으로 전달한다. 이를 위해 별도 사용자 설문을 만들 필요는 없다.

## 정규화 요청

[실행 가능한 전체 요청](examples/ai07-request.json) · [그 요청의 실제 응답](examples/ai07-response.json).

| 필드 | 필수·의미 |
|---|---|
| contractVersion | EXAM_SLEEP_MINIMAL_V1 |
| asOfAt / timezone | offset 포함 RFC3339, Asia/Seoul |
| populationScope | ADULT_VERIFIED 또는 ADULT_UNVERIFIED. 미확인 시 기본 기준안만 사용 |
| sourceRevision | 0–2^53−1 정수. Spring 입력 snapshot revision |
| target | key(가명 문자열), startsAt, examNature. 현재 이후 ≤72시간. durationMin 선택(1–480, 기본 60): 시험 중간 시점 수행 상태 평가에 사용 |
| preparation | state, assessedAt, wordingVersion. 선택 remainingStudyHours={lower,upper,source}: 휴식·집중 상태 기준 남은 작업시간 범위. 각 0.1–200시간, lower≤upper. source=BACKEND_ESTIMATE/PRIOR_SESSION_ESTIMATE |
| profile | 선택. usualBedtime/WakeTime, sleepNeedMin, sleepLatencyMin, minMainSleepOpportunityMin, commuteMin/prepMin/bufferMin, provenance |
| historyCoverage | startAt/endAt/state(COMPLETE/PARTIAL/NONE), 선택 lastSyncedAt. 과거 28일 |
| sleepHistory | 최대 100세션. key/startAt/endAt/basis, 선택 asleepDurationMin/sessionKind/deviceFamily/stageCoverage/stages |
| fixedIntervals | 최대 500개. key/startAt/endAt/kind. 준비·이동도 이미 포함 |
| constraints | napPolicy, policyVersion=workload-balanced-2, calendarState, 선택 napWindows/experimentalSplit=false. 기존 minimal-balanced-1도 수신하나 새 정책으로 계산하고 경고 |
| optionalObservations | 선택 caffeineState, caffeineEvents[{at,mg}]. 과거 기록만, 새 복용 추천 없음 |
| randomSeed | 선택. 기본 20261113 |

모든 객체의 알 수 없는 필드를 거절한다. 이름·로그인 ID·자유 메모·일정 제목·원 wearable id를 모델에 보낼 필요 없다. profile의 nullable 값은 기본값으로 보완한다. 기본 sleepNeedMin=480은 개인 필요량을 학습한 값이 아니다.

**5시간 하한과 20시간 연속 각성에 의한 후보 탈락을 제거했다.** minMainSleepOpportunityMin은 0–480분을 수신하지만 폐기 예정인 호환 필드로만 보존하며 양수이면 LEGACY_SLEEP_MINIMUM_IGNORED를 반환한다. 수면 없는 안과 1시간부터 시작하는 짧은 주수면 기회를 비교하며, 허용 낮잠은 20분부터 비교한다. 이 이산 후보 길이는 의학적 하한·수면 주기가 아니다. fixedIntervals를 이미 준 경우 준비/이동은 중복 예약하지 않는다. 실제 수면 후보는 기상부터 시험까지 최소 30분을 확보하며 준비/이동도 이 여유에 포함한다.

세션 basis는 ASLEEP_ESTIMATE/OPPORTUNITY/SESSION_SPAN/UNKNOWN. asleepDurationMin은 ASLEEP_ESTIMATE에서만 허용하며 실제 집계가 없으면 null. 이미 실수면인 값에 잠복기를 재차 빼지 않는다. stage는 AWAKE/LIGHT/DEEP/REM/ASLEEP_UNSPECIFIED/UNKNOWN이며 전체 10,000개 이하, 세션 안에서 겹침 없이 시간순이다. inBed는 단계로 더하지 않는다. 상충 세션은 이중 집계하지 않고 경고와 함께 제외한다.

napPolicy는 DISABLED/OPTIONAL_ONLY/ALLOWED. 선택과 대안 모두 낮잠은 ALLOWED와 실제 napWindows 안에서만 가능하다. OPTIONAL_ONLY에서는 창을 임의로 만들지 않는다. MAIN_SLEEP는 별도의 주수면 기회이며 짧게 줄여질 수도 있다. 주수면 장소를 확보했다는 기존 서비스 전제는 여전히 필요하다.

## 기존 백엔드 스냅샷 변환 경로

[전체 네이티브 요청 예제](examples/ai07-backend-request.json), 변환 함수 `ai_server.ai_07.backend.build_request`. `calculate_backend`가 변환 후 동일 엔진을 호출한다.

필수 root: contractVersion=EXAM_SLEEP_BACKEND_V1, asOfAt, sourceRevision, selection, events, sleepLogs, historyCoverage.

| 필드 | 입력 구조·변환 |
|---|---|
| selection | preparationState, examNature, assessedAt, 선택 wordingVersion, remainingStudyHours(정규화와 같은 구조) |
| targetEventId / targetOccurrenceKey | 선택. 없으면 72시간 내 가장 가까운 시각 있는 EXAM 자동 선택 |
| profile | 기존 sleepNeedMin/sleepLatencyMin/minSleepMin/commuteMin/prepMin/prepMinEstimate/bufferMin/usualBedtime/usualWakeTime |
| events | eventId/type/startAt/endAt/allDay/repeat/occurrenceKey만 전달. title·note는 Spring에서 제거 |
| eventExceptions | eventId/occurrenceDate/cancelled/startAt/endAt |
| timetableEntries | entryId/dayOfWeek/startTime/endTime, 선택 semesterStartDate/semesterEndDate |
| sleepLogs | sleepLogId/bedAt/wakeAt, 선택 durationMin/durationBasis/provider/externalId/originApp/sourceUpdatedAt/deletedAt/source/deviceFamily/sessionKind/stages |
| napLogs | napLogId/startAt/endAt, 선택 durationMin/canonicalSleepLogId |
| includeNaps / napWindows | 기존 설정·실제 창으로 napPolicy 생성. 허용 설정만 있고 창 없으면 OPTIONAL_ONLY |
| calendarState / populationScope / randomSeed | 선택. 미상 상태로 처리 가능 |

반복은 NONE/DAILY/WEEKLY, interval 1–52, until 날짜를 지원한다. 월별 반복은 이 어댑터에서 거절하며 Spring이 발생 건으로 펼쳐서 보낸다. allDay는 기존 계약처럼 계산 제외다. endAt 없는 일정·시험은 60분으로 근사한다. 대상 시험 endAt은 준비 시간표의 종료를 바꾸지는 않지만 durationMin으로 변환해 시험 중간 시점의 수행 상태 평가에 사용한다(최대 480분).

profile.prepMinEstimate가 있으면 prepMin 대신 사용한다. minSleepMin→minMainSleepOpportunityMin은 호환 보존만 하며 하한으로 적용하지 않는다. 시험 직전 commute+prep+buffer 구간을 한 번만 예약한다. 수면·일정 id는 요청별 가명 key로 바꾸며 target에 DB id를 돌려주지 않는다. Spring은 원 context와 sourceRevision으로 연결한다. assessedAt이 6시간보다 오래되면 remainingStudyHours 추정은 사용하지 않고 준비 상태 범위를 확대한다.

동일 provider/originApp/externalId 중 최신 수정본을 선택하고 최신 tombstone은 제거한다. 웨어러블과 수동 기록의 겹침은 웨어러블 우선 canonical view로 선택한다. 같은 수면과 겹친 napLog는 더하지 않는다. DB 원본은 변경하지 않는다. 구 span만 있는 durationMin은 실수면으로 승격하지 않고 durationBasis=UNKNOWN을 사용한다.

이 경로는 백엔드 스냅샷 전용이다. HealthKit의 겹친 inBed·세부 샘플 원문, 원시 센서, 권한 토큰, 전체 export를 그대로 전송하는 API가 아니다. Spring/모바일이 공급자별 세션화·단계 겹침 정리를 마친 뒤 보낸다. 예외로 옮긴 반복 일정이 요청 window 밖의 원발생 건에 속하면 Spring에서 발생 건으로 펼쳐 보내는 것이 가장 정확하다.

## 반환

공통 envelope `{result,modelVersion}`. 실제 필드는 [예제 응답](examples/ai07-response.json)과 같다.

- status: READY / BASELINE / TRADEOFF / NO_FEASIBLE_PLAN. 이전 LIMITED_WINDOW는 새 실행본에서 생성하지 않는다.
- selected: 후보·전략·cycleMode·전체 timeline·총합·가정별 실수면 범위·내부 효용 범위·상대 worstCaseRegret·workloadAssessment. 전략은 MAIN_ONLY/MAIN_NAP/NAP_ONLY/NO_SLEEP.
- alternative: 독립 대안 timeline 또는 null. 선택 timeline과 합치지 않는다.
- comparison: 기준안·차이 범위·불확실 상태. scenarioShareIsProbability=false.
- reasonCodes/reasons/warnings: 계산과 연결한 정형 한국어 설명.
- quality: 단계 보조 사용 가능 여부·밤 수·모형 hash·타깃 미검증 상태.
- diagnostics: 후보 수·256 시나리오·15분 기억 적분·선택 규칙·유한 탐색 범위.
- evidence: 실제 source 재학습 정보·원도메인 지표·라이선스·전이 가정.
- workload: 사용한 남은 집중 공부시간 범위·추정 출처·measured=false·completionProbability=null·postExamRecoveryCredited=false.
- inputHash/artifactHash/parameterHash/scenarioHash/sourceRevision/policyVersion: 저장·재현 기준.
- predictedExamScore/individualSuccessProbability/estimatedRecalledItems: null.

MAIN_SLEEP/NAP은 기회이며 실수면과 구분한다. STUDY_AVAILABLE은 앞으로 공부할 수 있는 시간이지 실제 공부나 숙련 증가가 아니다. NO_FEASIBLE_PLAN은 200의 계산 결과다. selected=null이면 수면 계획으로 저장하지 않는다. TRADEOFF/NO_SLEEP는 비교에서 수면 없는 안이 선택된 것으로 수면 구간·알람을 만들지 않으며, 건강상 안전성이나 성적 향상이 검증된 것으로 표시하지 않는다.

workloadAssessment.status=ALL_ASSUMPTIONS/SOME_ASSUMPTIONS/NO_ASSUMPTIONS는 각 공학적 가정에서 **작업 시간 예산**을 채울 수 있는지를 말한다. 숙달·범위 완주·정답률의 실측 예측이 아니다. remainingFocusedHoursRange, effectiveStudyHoursRange, newMaterialCoverageRange 역시 가정별 범위이며 확률로 렌더링하지 않는다. 기존 worstCaseRegret과 새 값은 척도가 다르므로 버전을 분리한다.

## 오류·운영 확인

INVALID_INPUT 400, UNSUPPORTED_HORIZON/UNSUPPORTED_POLICY/NO_UPCOMING_TIMED_EXAM 422, RETIRED_CONTRACT 410, MODEL_UNAVAILABLE/SERVER_BUSY 503, MODEL_TIMEOUT 504. 기존 내부 키·요청 ID·중복 JSON 거절·프로세스 종료/슬롯 복구를 공유한다.

```powershell
python -B -S -X utf8 -m ai_server --host 127.0.0.1 --port 8000
python -B -S -X utf8 tools/check_backend_contract.py http://127.0.0.1:8000/ai/v1 --include-ai06 --include-ai07
python -B -S -X utf8 tools/check_ai07_http.py
```

환경에 AI_INTERNAL_KEY를 먼저 주입한다. health의 AI-03 버전과 ready.startupInference.models.ai03을 확인한다. resource manifest는 배포 전 생성하며 요청 시 자동으로 고치지 않는다.

## Spring 팀이 별도로 적용할 사항

두 선택 저장, recommendation group/segments 저장, 동기화 원출처·삭제·단계 확장, revision·멱등·apply 트랜잭션, 알림 교체는 [백엔드 인계서](../dummy/docs/exam_sleep_v3/03_BACKEND_HANDOFF.md)에 구체화했다. 현재 저장소에서 구현 완료한 것은 **입력 변환 함수·HTTP·모델·공통서버 연결**이다. Spring DTO/DDL/모바일 앱은 실제 소스가 제공되지 않아 미적용이다.


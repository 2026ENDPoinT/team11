# SLEEP 통합 AI 서버 백엔드 연동 명세

Spring이 사용자 소유권과 데이터 접근 권한을 확인하고, 해당 모델에 필요한 조회 결과를 한 번의 JSON 스냅샷으로 보냅니다. Python AI 서버는 요청에 담긴 데이터로 계산합니다. 결과 저장, 알림, 일정 변경과 앱 공개 응답 조립은 Spring이 담당합니다.

## 연결과 인증

SLEEP 폴더에서 Python 3.12 환경으로 실행합니다. AI 서버와 Spring에 같은 `AI_INTERNAL_KEY`를 환경으로 주입합니다.

```powershell
python -m pip install -r ai_server/ai_06/requirements.txt
python -B -X utf8 -m ai_server --host 127.0.0.1 --port 8000
python -B -X utf8 tools/check_backend_contract.py http://127.0.0.1:8000/ai/v1
```

- Spring은 `GET /ai/v1/health` 외의 모든 호출에 `X-Internal-Key`를 보냅니다. 운영 환경에서는 HTTPS를 사용하고, 사설 CA를 쓰면 Spring과 점검 도구에 신뢰 CA를 설정합니다.
- POST는 UTF-8 `application/json`, 단일 `Content-Length`, 최대 1 MiB입니다. 선택 `X-Request-Id`는 영숫자·`-_.` 1~128자이며 응답 헤더에 반환됩니다.
- `now`와 `asOfAt`은 오프셋을 포함하고, 같은 스냅샷 재시도에는 같은 값을 보냅니다. 날짜·요일·로컬 시각은 서울 기준입니다. 사용자 프로필·일정 등의 Spring 응답에서 `data` 안의 객체나 배열을 꺼내 AI DTO로 만듭니다. 빈 배열은 실제 조회 결과가 비었을 때 사용합니다.
- 기본 계산 예산은 AI-02·04·05가 2.5초, AI-03·06이 10초입니다. Spring 읽기 제한은 프로세스 정리와 네트워크 여유를 포함해 각각 4초, 12초 이상에서 검증합니다.
- 성공한 POST는 `{result,modelVersion}`입니다. 오류는 `{"error":{"code":"INVALID_INPUT","message":"…","details":[{"field":"…","reason":"…"}]}}` 형식이며 `details`는 선택입니다. 400 입력, 401 인증, 404 경로, 405 메서드, 410 폐기 계약, 413 본문 크기, 415 미디어 타입, 422 도메인 오류, 429 호출 한도, 503 모델·슬롯·서킷, 504 계산 제한을 구분합니다.

## 호출 목록

| 모듈 | 경로 | 요청 데이터 |
| --- | --- | --- |
| AI-01 | GET /ai/v1/health | 본문 없음, 인증 없음 |
| 운영 | GET /ai/v1/ready | 본문 없음, 내부 인증 |
| 운영 | GET /ai/v1/metrics | 본문 없음, 내부 인증 |
| AI-02 | POST /ai/v1/plan/tonight | 사용자 설정, 완료 수면, 시간표·일정 |
| AI-03 | POST /ai/v1/plan/exam-sleep | 시험, 준비 상태·시험 유형, 수면, 시간표·일정. DTO 3종 |
| AI-04 | POST /ai/v1/sleep-debt/calculate | 일별 수면·목표, 설정, 미래 일정 |
| AI-05 | POST /ai/v1/insights/risks | 일정 배열, 과목 사전, 사용자 규칙 |
| AI-06 | POST /ai/v1/plan/focus-sleep | 최종 기상 마감, 침대 체류 범위, 주수면 이력 |
| AI-04 직접 호출 | POST /ai/v1/plan/sleep-recovery | 수면 회복 모델의 원시 snake_case 입력 |
| AI-05 직접 호출 | POST /ai/v1/schedule/label | 일정 한 건의 원시 입력 |

공개 앱의 `GET /api/v1/sleep-debt`, `GET /api/v1/insights/upcoming-risks`는 Spring에서 응답합니다. 내부 AI POST를 호출하고 공개 DTO를 조립하는 필드 매핑은 별도 인계서에 있습니다. [공개 Swagger 매핑](../docs/AI-04_AI-05_SLEEPSYNC_HANDOFF.md)

## 운영 GET의 응답

`GET /ai/v1/health`의 `models`에는 현재 AI-02·03·06 버전이 표시됩니다. 시작 추론 전체를 확인할 때는 `ready`의 `startupInference.models`를 확인합니다.

```json
{"status":"ok","modelVersion":"ai02-normal-v1","models":{"AI-02":"ai02-normal-v1","AI-03":"exam-sleep-workload-2.0.0","AI-06":"focus-sleep-pvt-1.0.0"}}
```

`GET /ai/v1/ready`는 성공 200, 준비 실패 503입니다.

```json
{"ready":true,"resources":{"ok":true,"changedResources":[]},"startupInference":{"ok":true,"models":{"ai02":true,"ai03":true,"ai04":true,"ai05":true,"ai06":true}}}
```

`GET /ai/v1/metrics`는 밀리초 단위 지연과 호출 수를 반환합니다. 숫자는 형식 예시입니다.

```json
{"requests":7,"serverErrors":0,"connectionRejected":0,"connectionsActive":1,"elapsedMsTotal":350,"elapsedMsMax":90,"execution":{"accepted":3,"rate_limited":0,"circuit_rejected":0,"completed":3,"failed":0,"timeouts":0,"busy":0,"openCircuits":0},"executionMode":"process"}
```

## Spring에서 준비할 데이터와 결과 처리

### AI-02 평상시 수면

수면 목표·입면·준비·이동·여유 분 설정, 최근 최대 35일의 완료 수면, 기상일에 관련된 시간표와 일정 발생 건을 모읍니다. 요청은 `now,targetDate,profile,sleepHistory,timetable,events`가 필요합니다. `targetDate`는 서울 기준 오늘 또는 내일입니다. 기록에는 `sleepLogId,date,bedAt,wakeAt,durationMin,quality,status,source`와 확보한 단계 구간을 전달합니다. 시간표는 요일과 시작·종료, 일정은 시작·종료·종일 여부와 유형을 사용합니다. 추천 응답의 `sleepMin`은 분, `breakdown`은 일정이 없을 때 null일 수 있습니다. 실행 가능한 계획이 없으면 422 `SOLVER_INFEASIBLE`입니다. [필드 명세](../docs/AI-02_BACKEND_CONTRACT.md)

### AI-03 벼락치기

시험 ID·시각, 사용자 설정, 최근 28일의 완료 수면, 일정·시간표·연결 학기를 확보합니다. 사용자 선택으로 준비 상태와 시험 유형을 받아 평가 시각과 함께 `selection`에 넣고, 기준 시각 `asOfAt`과 스냅샷 번호 `sourceRevision`을 붙입니다. 특정 시험에서 호출하면 `targetEventId`를 보내고, 생략 시 향후 72시간 내 시각 지정 시험을 선택합니다. 낮잠을 허용하는 경우 실제 가능한 창을 보냅니다. 기존 공부 시간 추정이 있을 때만 `remainingStudyHours`를 전달합니다.

Spring DTO를 그대로 모으는 진입점은 `SLEEPSYNC_EXAM_SNAPSHOT_V1`입니다. 네이티브 `EXAM_SLEEP_BACKEND_V1`은 설정을 `profile`로 전달하며 백엔드 어댑터가 시험과 일정을 정규화합니다. `EXAM_SLEEP_MINIMAL_V1`은 이미 시험을 확정하고 수면 기록과 고정 일정을 정규화한 호출에 사용합니다. 성공 응답은 정규화 계약으로 돌아오며 `sourceRevision`으로 요청과 대조합니다. `selected`와 `alternative`는 각각 별도 계획이며, `NO_FEASIBLE_PLAN`이면 HTTP 200과 `selected: null`입니다. 시간표에서 `MAIN_SLEEP`과 `NAP`은 수면 기회, `STUDY_AVAILABLE`은 공부 가능한 시간입니다. [Spring DTO 상세](ai_03/docs/SPRING_HANDOFF.md) · [세 가지 계약 상세](../docs/AI-03_BACKEND_CONTRACT.md)

### AI-04 수면 회복

성인 확인, 수면 목표·준비·이동·입면·선호 시각, 기상일당 하나의 수면 기록, 미래 일정 발생 건을 조회합니다. 요청은 `now,asOfDate,preferences,sleepRecords,events`가 필요합니다. 선택 `windowDays,planDays` 기본값은 각각 7일·3일입니다. 시각은 오프셋과 분 정밀도로 전달합니다. 미관측 날은 결측으로 다루며, 사용자 확인이 있는 밤샘만 실제 수면 0분으로 보냅니다. `shortfallMinutes`는 일별 야간 수면 부족분의 합입니다. 공개 Spring의 `currentDebtMin`과 매핑할 때 계산 의미를 유지해야 합니다. `recoveryPlan`에는 기상일별 계획, 정책, 조건부 일정 조정 확인이 들어갑니다. [필드 명세](../docs/AI-04_BACKEND_CONTRACT.md)

### AI-05 일정 일괄 분류

일정 ID와 원문 텍스트, 가능한 시작·마감·중요도·집중력 요구를 모읍니다. 사용자별 과목·별칭과 저장 규칙은 선택 입력입니다. `events`는 최대 100건, `userRules`는 최대 500건입니다. `courses` 생략 또는 null은 기본 과목 사전, 빈 배열은 빈 과목 사전입니다. 응답 `analyses[]`는 요청 순서이며 `eventId`로 연결합니다. `sleepPriority`와 `assessment.urgency`는 별도 값입니다. 공개 앱의 위험 항목 포함 여부와 `severity`는 Spring 정책으로 조립합니다. [필드 명세](../docs/AI-05_BACKEND_CONTRACT.md)

### AI-06 집중 모드

기준 시각 이후 36시간 이내의 최종 기상 마감, 최소·최대 침대 체류 분, 최근 90일의 서로 다른 기상일 주수면 3~90건을 확보합니다. 준비·이동은 Spring이 기상 마감에 미리 반영합니다. `asleepMinutes`는 실제 수면 추정 분입니다. 잠복기는 `latencyMinutes`와 `sleepOnset` 중 하나를 사용합니다. 응답의 `sleep_opportunity_hours`, `estimated_actual_sleep_hours`는 시간 단위입니다. `prediction_used_for_ranking`과 `cycle_fallback.used`가 추천 방식을 구분합니다. PVT 점수가 null이면 표시할 점수가 없는 상태입니다. [필드 명세](../docs/AI-06_FOCUS_BACKEND_CONTRACT.md) · [OpenAPI](../docs/AI-06_FOCUS_OPENAPI.json)

### 모델 직접 호출

AI-04 원시 경로에는 snake_case 설정과 일별 수면·일정을 보냅니다. 원시 일정 ID와 시각은 `id,start,end`입니다. AI-05 원시 경로는 `text` 한 건과 선택 `user_rules,options`를 받습니다. `options`는 `reference_at,starts_at,deadline_at` 등 원시 필드를 사용합니다. [직접 호출 상세](../deploy/README.md)

## 요청·응답 JSON 예제

아래 요청은 현재 등록된 서비스로 실행했습니다. 일부 응답은 구조를 읽기 쉽게 대표 필드만 발췌했습니다. 전체 필드는 위 계약서와 링크의 JSON에서 확인할 수 있습니다.


### AI-02 평상시

`POST /ai/v1/plan/tonight`

[저장소 요청 예제](../docs/examples/ai02-request.json) · [저장소 응답 예제](../docs/examples/ai02-response.json)

요청 JSON

```json
{
  "now": "2026-11-10T21:00:00+09:00",
  "targetDate": "2026-11-11",
  "profile": {
    "sleepNeedMin": 465,
    "sleepLatencyMin": 15,
    "prepMin": 55,
    "prepMinEstimate": 50,
    "commuteMin": 40,
    "bufferMin": 10,
    "minSleepMin": 300
  },
  "timetable": [
    {
      "dayOfWeek": "WED",
      "startTime": "09:00",
      "endTime": "10:15"
    }
  ],
  "events": [],
  "sleepHistory": []
}
```

응답 JSON

```json
{
  "result": {
    "recommendedBedAt": "2026-11-10T23:15:00+09:00",
    "recommendedWakeAt": "2026-11-11T07:15:00+09:00",
    "sleepMin": 465,
    "breakdown": {
      "commuteMin": 40,
      "prepMin": 50,
      "bufferMin": 10,
      "sleepNeedMin": 465,
      "sleepLatencyMin": 15
    },
    "reasons": [
      "첫 일정과 준비·통학 시간을 고려했어요.",
      "가능한 시간 안에서 목표 수면시간을 우선했어요."
    ],
    "warnings": [
      "관측되지 않은 수면 중 각성은 추정하지 않았어요.",
      "최근 관측일이 부족해 규칙 계획을 사용했어요."
    ]
  },
  "modelVersion": "ai02-normal-v1"
}
```

### AI-03 Spring 스냅샷

`POST /ai/v1/plan/exam-sleep`

[저장소 요청 예제](../docs/examples/ai03-spring-snapshot-request.json) · [저장소 응답 예제](../docs/examples/ai03-spring-snapshot-response.json)

요청 JSON

```json
{
  "contractVersion": "SLEEPSYNC_EXAM_SNAPSHOT_V1",
  "asOfAt": "2026-11-13T17:00:00+09:00",
  "sourceRevision": 43,
  "populationScope": "ADULT_VERIFIED",
  "targetEventId": 101,
  "selection": {
    "preparationState": "SEEN_NOT_RECALLABLE",
    "examNature": "RECALL",
    "assessedAt": "2026-11-13T16:59:00+09:00"
  },
  "userProfile": {
    "onboardingDone": true,
    "prepMinEstimate": 50,
    "settings": {
      "sleepNeedMin": 465,
      "sleepLatencyMin": 15,
      "minSleepMin": 300,
      "commuteMin": 40,
      "prepMin": 55,
      "bufferMin": 10,
      "chronotype": "NEUTRAL"
    }
  },
  "events": [
    {
      "eventId": 101,
      "type": "EXAM",
      "startAt": "2026-11-14T09:00:00+09:00",
      "endAt": "2026-11-14T10:00:00+09:00",
      "allDay": false
    }
  ],
  "timetableEntries": [
    {
      "entryId": 202,
      "dayOfWeek": "FRI",
      "startTime": "18:00:00",
      "endTime": "19:00:00",
      "semester": "2026-2",
      "courseName": "실습"
    }
  ],
  "semesters": [
    {
      "semester": "2026-2",
      "startDate": "2026-09-01",
      "endDate": "2026-12-20",
      "current": true
    }
  ],
  "sleepLogs": [
    {
      "sleepLogId": 3001,
      "status": "CLOSED",
      "source": "WEARABLE",
      "bedAt": "2026-11-13T00:00:00+09:00",
      "wakeAt": "2026-11-13T07:00:00+09:00",
      "durationMin": 390,
      "stages": [
        {
          "stage": "LIGHT",
          "startAt": "2026-11-13T00:15:00+09:00",
          "endAt": "2026-11-13T01:00:00+09:00"
        }
      ]
    }
  ]
}
```

응답 JSON 발췌

```json
{
  "result": {
    "contractVersion": "EXAM_SLEEP_MINIMAL_V1",
    "sourceRevision": 43,
    "status": "READY",
    "reasonCodes": [
      "ROBUST_SLEEP_STUDY_BALANCE",
      "WORKLOAD_UNCERTAIN",
      "SHORT_SLEEP_TRADEOFF",
      "SHIFTED_SLEEP_TIMING",
      "EXAM_PREPARATION_RESERVED"
    ],
    "selected": {
      "candidateKey": "duration-end-15",
      "strategy": "MAIN_ONLY",
      "totals": {
        "sleepOpportunityMin": 285.0,
        "studyAvailableMin": 485.0,
        "fixedMin": 160.0,
        "wakeBufferMin": 30.0,
        "restMin": 0.0
      },
      "timeline": [
        {
          "kind": "STUDY_AVAILABLE",
          "startAt": "2026-11-13T17:00:00+09:00",
          "endAt": "2026-11-13T18:00:00+09:00"
        },
        {
          "kind": "FIXED",
          "startAt": "2026-11-13T18:00:00+09:00",
          "endAt": "2026-11-13T19:00:00+09:00"
        }
      ]
    },
    "alternative": {
      "candidateKey": "candidate-0",
      "strategy": "MAIN_ONLY",
      "totals": {
        "sleepOpportunityMin": 465.0,
        "studyAvailableMin": 315.0,
        "fixedMin": 160.0,
        "wakeBufferMin": 20.0,
        "restMin": 0.0
      }
    }
  },
  "modelVersion": "exam-sleep-workload-2.0.0"
}
```

### AI-03 백엔드 DTO

`POST /ai/v1/plan/exam-sleep`

[저장소 요청 예제](../docs/examples/ai07-backend-request.json) · [저장소 응답 예제](../docs/examples/ai07-backend-response.json)

저장된 응답과 현재 엔진의 세부 추천 값은 다를 수 있습니다. 아래 값은 현재 실행 결과입니다.

요청 JSON

```json
{
  "contractVersion": "EXAM_SLEEP_BACKEND_V1",
  "asOfAt": "2026-11-13T17:00:00+09:00",
  "sourceRevision": 43,
  "populationScope": "ADULT_VERIFIED",
  "selection": {
    "preparationState": "SEEN_NOT_RECALLABLE",
    "examNature": "RECALL",
    "assessedAt": "2026-11-13T16:59:00+09:00",
    "wordingVersion": "readiness-copy-1"
  },
  "profile": {
    "sleepNeedMin": 465,
    "sleepLatencyMin": 15,
    "minSleepMin": 0,
    "commuteMin": 40,
    "prepMin": 55,
    "prepMinEstimate": 50,
    "bufferMin": 10,
    "usualBedtime": "00:30",
    "usualWakeTime": "07:30"
  },
  "events": [
    {
      "eventId": 101,
      "type": "EXAM",
      "startAt": "2026-11-14T09:00:00+09:00",
      "endAt": "2026-11-14T10:00:00+09:00",
      "allDay": false
    },
    {
      "eventId": 102,
      "type": "PERSONAL",
      "startAt": "2026-11-13T18:00:00+09:00",
      "endAt": "2026-11-13T19:00:00+09:00"
    }
  ],
  "sleepLogs": [
    {
      "sleepLogId": 3001,
      "bedAt": "2026-11-13T00:00:00+09:00",
      "wakeAt": "2026-11-13T07:00:00+09:00",
      "durationMin": 390,
      "durationBasis": "ASLEEP_ESTIMATE",
      "source": "WEARABLE",
      "provider": "HEALTH_KIT",
      "originApp": "com.apple.health",
      "externalId": "native-1",
      "deviceFamily": "APPLE_WATCH",
      "sessionKind": "MAIN",
      "stages": [
        {
          "stage": "LIGHT",
          "startAt": "2026-11-13T00:15:00+09:00",
          "endAt": "2026-11-13T01:00:00+09:00"
        }
      ]
    }
  ],
  "historyCoverage": {
    "startAt": "2026-10-30T00:00:00+09:00",
    "endAt": "2026-11-13T17:00:00+09:00",
    "state": "PARTIAL",
    "lastSyncedAt": "2026-11-13T16:30:00+09:00"
  },
  "calendarState": "SYNCED",
  "includeNaps": false
}
```

응답 JSON 발췌

```json
{
  "result": {
    "contractVersion": "EXAM_SLEEP_MINIMAL_V1",
    "sourceRevision": 43,
    "status": "READY",
    "reasonCodes": [
      "ROBUST_SLEEP_STUDY_BALANCE",
      "WORKLOAD_UNCERTAIN",
      "SHORT_SLEEP_TRADEOFF",
      "SHIFTED_SLEEP_TIMING",
      "EXAM_PREPARATION_RESERVED"
    ],
    "selected": {
      "candidateKey": "candidate-26",
      "strategy": "MAIN_ONLY",
      "totals": {
        "sleepOpportunityMin": 240.0,
        "studyAvailableMin": 530.0,
        "fixedMin": 160.0,
        "wakeBufferMin": 30.0,
        "restMin": 0.0
      },
      "timeline": [
        {
          "kind": "STUDY_AVAILABLE",
          "startAt": "2026-11-13T17:00:00+09:00",
          "endAt": "2026-11-13T18:00:00+09:00"
        },
        {
          "kind": "FIXED",
          "startAt": "2026-11-13T18:00:00+09:00",
          "endAt": "2026-11-13T19:00:00+09:00"
        }
      ]
    },
    "alternative": {
      "candidateKey": "candidate-0",
      "strategy": "MAIN_ONLY",
      "totals": {
        "sleepOpportunityMin": 465.0,
        "studyAvailableMin": 335.0,
        "fixedMin": 160.0,
        "wakeBufferMin": 0.0,
        "restMin": 0.0
      }
    }
  },
  "modelVersion": "exam-sleep-workload-2.0.0"
}
```

### AI-03 정규화 DTO

`POST /ai/v1/plan/exam-sleep`

[저장소 요청 예제](../docs/examples/ai07-request.json) · [저장소 응답 예제](../docs/examples/ai07-response.json)

저장된 응답과 현재 엔진의 세부 추천 값은 다를 수 있습니다. 아래 값은 현재 실행 결과입니다.

요청 JSON

```json
{
  "contractVersion": "EXAM_SLEEP_MINIMAL_V1",
  "asOfAt": "2026-11-13T17:00:00+09:00",
  "timezone": "Asia/Seoul",
  "populationScope": "ADULT_VERIFIED",
  "sourceRevision": 43,
  "target": {
    "key": "exam-x1",
    "startsAt": "2026-11-14T09:00:00+09:00",
    "examNature": "RECALL"
  },
  "preparation": {
    "state": "SEEN_NOT_RECALLABLE",
    "assessedAt": "2026-11-13T16:59:00+09:00",
    "wordingVersion": "readiness-copy-1"
  },
  "profile": {
    "usualBedtime": "00:30",
    "usualWakeTime": "07:30",
    "sleepNeedMin": 465,
    "sleepLatencyMin": 15,
    "minMainSleepOpportunityMin": 0,
    "commuteMin": 40,
    "prepMin": 50,
    "bufferMin": 10,
    "provenance": {
      "habitualNights": 12,
      "basis": "OBSERVED_SLEEP_TIMING",
      "sleepNeedBasis": "LEGACY_SETTING"
    }
  },
  "historyCoverage": {
    "startAt": "2026-10-30T00:00:00+09:00",
    "endAt": "2026-11-13T17:00:00+09:00",
    "state": "PARTIAL",
    "lastSyncedAt": "2026-11-13T16:30:00+09:00"
  },
  "sleepHistory": [
    {
      "key": "sleep-x1",
      "startAt": "2026-11-13T00:00:00+09:00",
      "endAt": "2026-11-13T07:00:00+09:00",
      "basis": "ASLEEP_ESTIMATE",
      "asleepDurationMin": 390,
      "sessionKind": "MAIN",
      "deviceFamily": "APPLE_WATCH",
      "stageCoverage": 0.1071428571,
      "stages": [
        {
          "stage": "LIGHT",
          "startAt": "2026-11-13T00:15:00+09:00",
          "endAt": "2026-11-13T01:00:00+09:00"
        }
      ]
    }
  ],
  "fixedIntervals": [
    {
      "key": "obligation-x1",
      "startAt": "2026-11-13T18:00:00+09:00",
      "endAt": "2026-11-13T19:00:00+09:00",
      "kind": "OBLIGATION"
    },
    {
      "key": "exam-prep-x1",
      "startAt": "2026-11-14T07:20:00+09:00",
      "endAt": "2026-11-14T09:00:00+09:00",
      "kind": "PREPARATION_TRAVEL"
    }
  ],
  "constraints": {
    "napPolicy": "OPTIONAL_ONLY",
    "napWindows": [],
    "policyVersion": "workload-balanced-2",
    "experimentalSplit": false,
    "calendarState": "SYNCED"
  },
  "optionalObservations": {
    "caffeineState": "UNKNOWN",
    "caffeineEvents": []
  },
  "randomSeed": 20261113
}
```

응답 JSON 발췌

```json
{
  "result": {
    "contractVersion": "EXAM_SLEEP_MINIMAL_V1",
    "sourceRevision": 43,
    "status": "READY",
    "reasonCodes": [
      "ROBUST_SLEEP_STUDY_BALANCE",
      "WORKLOAD_UNCERTAIN",
      "SHORT_SLEEP_TRADEOFF",
      "SHIFTED_SLEEP_TIMING",
      "EXAM_PREPARATION_RESERVED"
    ],
    "selected": {
      "candidateKey": "candidate-26",
      "strategy": "MAIN_ONLY",
      "totals": {
        "sleepOpportunityMin": 240.0,
        "studyAvailableMin": 530.0,
        "fixedMin": 160.0,
        "wakeBufferMin": 30.0,
        "restMin": 0.0
      },
      "timeline": [
        {
          "kind": "STUDY_AVAILABLE",
          "startAt": "2026-11-13T17:00:00+09:00",
          "endAt": "2026-11-13T18:00:00+09:00"
        },
        {
          "kind": "FIXED",
          "startAt": "2026-11-13T18:00:00+09:00",
          "endAt": "2026-11-13T19:00:00+09:00"
        }
      ]
    },
    "alternative": {
      "candidateKey": "candidate-0",
      "strategy": "MAIN_ONLY",
      "totals": {
        "sleepOpportunityMin": 465.0,
        "studyAvailableMin": 335.0,
        "fixedMin": 160.0,
        "wakeBufferMin": 0.0,
        "restMin": 0.0
      }
    }
  },
  "modelVersion": "exam-sleep-workload-2.0.0"
}
```

### AI-04 회복

`POST /ai/v1/sleep-debt/calculate`

[저장소 요청 예제](../docs/examples/ai04-request.json) · [저장소 응답 예제](../docs/examples/ai04-response.json)

요청 JSON

```json
{
  "schemaVersion": "0.1",
  "now": "2026-11-09T18:00:00+09:00",
  "asOfDate": "2026-11-09",
  "preferences": {
    "adultConfirmed": true,
    "targetMinutes": 480,
    "preparationCommuteMinutes": 90,
    "sleepLatencyMinutes": 20,
    "preferredBedtime": "23:00",
    "preferredWakeTime": "07:00"
  },
  "sleepRecords": [
    {
      "date": "2026-11-05",
      "targetMinutes": 480,
      "actualSleepMinutes": 360
    },
    {
      "date": "2026-11-06",
      "targetMinutes": 480,
      "actualSleepMinutes": 420
    },
    {
      "date": "2026-11-07",
      "targetMinutes": 480,
      "sleepStart": "2026-11-06T23:00:00+09:00",
      "sleepEnd": "2026-11-07T07:30:00+09:00",
      "awakeMinutes": 0
    },
    {
      "date": "2026-11-08",
      "targetMinutes": 480,
      "actualSleepMinutes": 390,
      "napMinutes": 20
    },
    {
      "date": "2026-11-09",
      "targetMinutes": 480,
      "actualSleepMinutes": 450
    }
  ],
  "events": [
    {
      "title": "중간고사 대비 스터디",
      "leadMinutes": 0,
      "userOverride": {
        "movability": "flexible"
      },
      "eventId": "study-1",
      "startAt": "2026-11-09T23:00:00+09:00",
      "endAt": "2026-11-10T01:00:00+09:00"
    },
    {
      "title": "자료구조 중간고사",
      "labels": {
        "movability": "fixed"
      },
      "eventId": "exam-1",
      "startAt": "2026-11-10T09:00:00+09:00",
      "endAt": "2026-11-10T11:00:00+09:00"
    },
    {
      "title": "오후 수업",
      "labels": {
        "movability": "fixed"
      },
      "eventId": "class-1",
      "startAt": "2026-11-11T13:00:00+09:00",
      "endAt": "2026-11-11T15:00:00+09:00"
    },
    {
      "title": "인턴 면접",
      "labels": {
        "movability": "fixed"
      },
      "eventId": "interview-1",
      "startAt": "2026-11-12T09:00:00+09:00",
      "endAt": "2026-11-12T10:00:00+09:00"
    }
  ]
}
```

응답 JSON 발췌

```json
{
  "result": {
    "schemaVersion": "0.1",
    "ruleVersion": "opportunity-v4-history-calendar",
    "asOfDate": "2026-11-09",
    "warnings": [
      "NOT_A_PHYSIOLOGICAL_DEBT_MODEL",
      "CALENDAR_COVERAGE_NOT_VERIFIED",
      "ADDITIONAL_SLEEP_DEFERRED_INCOMPLETE_HISTORY",
      "INCOMPLETE_SLEEP_RECORDS",
      "SOME_LABELS_ARE_NOT_LLM_OUTPUT"
    ],
    "sleepSummary": {
      "metric": "target_night_sleep_shortfall",
      "dataStatus": "partial",
      "shortfallMinutes": 300,
      "missingDates": [
        "2026-11-03",
        "2026-11-04"
      ]
    },
    "recoveryPlan": {
      "policy": {
        "mode": "baseline_only",
        "requestedExtraMinutesPerNight": null
      },
      "plans": [
        {
          "wakeDate": "2026-11-10",
          "status": "requires_rescheduling",
          "bedtime": "2026-11-09T22:30+09:00",
          "wakeTime": "2026-11-10T07:00+09:00",
          "timeInBedMinutes": 510,
          "assumedSleepLatencyMinutes": 20,
          "plannedSleepCapacityMinutes": 490,
          "targetMinutes": 480,
          "desiredSleepCapacityMinutes": 480,
          "requestedAdditionalOpportunityMinutes": null,
          "additionalSleepStatus": "deferred_insufficient_history",
          "movabilityConfirmationRequests": [],
          "plannedTargetShortfallMinutes": 0,
          "additionalOpportunityMinutes": 10,
          "unmetAdditionalOpportunityMinutes": null,
          "gridRoundingExtraMinutes": 10,
          "priorityEventIds": [
            "exam-1"
          ],
          "requiresReschedulingIds": [
            "study-1"
          ],
          "ruleIds": [
            "E_HISTORY",
            "E_NO_LINEAR_REPAYMENT",
            "PRODUCT_TARGET_FIRST",
            "PRODUCT_FLEXIBLE_EVENT_COST",
            "PRODUCT_PREFERENCE_TIEBREAK",
            "PRODUCT_HISTORY_REQUIRED_FOR_EXTRA"
          ]
        }
      ]
    }
  },
  "modelVersion": "sleep-coach-v4-history-calendar"
}
```

### AI-05 일괄 분류

`POST /ai/v1/insights/risks`

[저장소 요청 예제](../docs/examples/ai05-request.json) · [저장소 응답 예제](../docs/examples/ai05-response.json)

요청 JSON

```json
{
  "now": "2026-11-10T18:00:00+09:00",
  "courses": [
    {
      "name": "자료구조",
      "aliases": [
        "자구",
        "DS"
      ]
    },
    {
      "name": "운영체제",
      "aliases": [
        "운체",
        "OS"
      ]
    }
  ],
  "userRules": [],
  "events": [
    {
      "eventId": 101,
      "text": "자구 과제 제출",
      "startAt": "2026-11-11T09:00:00+09:00",
      "deadlineAt": "2026-11-11T12:00:00+09:00",
      "importance": "높음",
      "focus": "보통"
    },
    {
      "eventId": 102,
      "text": "운체 중간고사",
      "startAt": "2026-11-12T10:00:00+09:00"
    },
    {
      "eventId": 103,
      "text": "미분류 표현"
    }
  ]
}
```

응답 JSON 발췌

```json
{
  "result": {
    "analyses": [
      {
        "eventId": 101,
        "label": "과제·제출",
        "sleepPriority": "높음",
        "assessment": {
          "basePriority": "보통",
          "importance": "높음",
          "focus": "보통",
          "finalPriority": "높음",
          "prioritySource": "explicit_signal",
          "urgency": "24시간 이내",
          "startStatus": "24시간 이내 시작",
          "factors": [
            "기본 분류 중요도: 보통",
            "사용자 중요도: 높음",
            "집중력 요구: 보통",
            "마감: 24시간 이내 (숙면 중요도에는 가산하지 않음)",
            "시작: 24시간 이내 시작 (숙면 중요도에는 가산하지 않음)"
          ],
          "startsAt": "2026-11-11T09:00:00+09:00",
          "deadlineAt": "2026-11-11T12:00:00+09:00",
          "referenceAt": "2026-11-10T18:00:00+09:00",
          "missingInputs": []
        }
      }
    ],
    "warnings": [
      "RULE_BASED_SLEEP_PRIORITY_NOT_RISK_PROBABILITY",
      "KNOWN_ABBREVIATION_FALSE_POSITIVES"
    ]
  },
  "modelVersion": "schedule-labeler-1.2.0"
}
```

### AI-06 집중 모드

`POST /ai/v1/plan/focus-sleep`

[저장소 요청 예제](../docs/examples/ai06-focus-request.json) · [저장소 응답 예제](../docs/examples/ai06-focus-response.json)

요청 JSON

```json
{
  "contractVersion": "FOCUS_SLEEP_V1",
  "asOfAt": "2026-11-13T23:00:00+09:00",
  "timezone": "Asia/Seoul",
  "wakeDeadlineAt": "2026-11-14T07:00:00+09:00",
  "minInBedMinutes": 360,
  "maxInBedMinutes": 480,
  "history": [
    {
      "wakeDate": "2026-11-10",
      "bedtime": "23:00",
      "wakeTime": "07:00",
      "asleepMinutes": 420,
      "latencyMinutes": 30
    },
    {
      "wakeDate": "2026-11-11",
      "bedtime": "23:00",
      "wakeTime": "07:00",
      "asleepMinutes": 420,
      "latencyMinutes": 30
    },
    {
      "wakeDate": "2026-11-12",
      "bedtime": "23:00",
      "wakeTime": "07:00",
      "asleepMinutes": 420,
      "latencyMinutes": 30
    },
    {
      "wakeDate": "2026-11-13",
      "bedtime": "23:00",
      "wakeTime": "07:00",
      "asleepMinutes": 420,
      "latencyMinutes": 30
    }
  ]
}
```

응답 JSON 발췌

```json
{
  "result": {
    "contractVersion": "FOCUS_SLEEP_V1",
    "planning_now": "2026-11-13T23:00:00+09:00",
    "wake_deadline": "2026-11-14T07:00:00+09:00",
    "selection_policy": "lowest_predicted_pvt_experimental",
    "prediction_used_for_ranking": true,
    "cycle_fallback": {
      "used": false,
      "nominal_cycle_minutes": 90,
      "cycle_length_is_measured": false,
      "assumed_latency_minutes": 30.0,
      "latency_missing": false,
      "reference_opportunity_hours": 8.0,
      "reference_actual_sleep_hours": 7.0,
      "cycle_basis": "estimated_actual_sleep",
      "nominal_cycle_count": 4,
      "alignment_error_minutes": 5.625,
      "validated_for_pvt": false,
      "note": "90분은 계산용 가정입니다. 실제 수면주기·REM·각성 단계나 인지 개선을 예측하지 않습니다. 중간 각성으로 주기가 끊기는 현상도 반영하지 않습니다.",
      "source": "https://www.nhlbi.nih.gov/health/sleep/stages-of-sleep"
    },
    "recommendation": {
      "bedtime": "2026-11-13T23:00:00+09:00",
      "wake_time": "2026-11-14T07:00:00+09:00",
      "sleep_opportunity_hours": 8.0,
      "estimated_actual_sleep_hours": 7.0,
      "predicted_pvt_lapse_score": 2.4568945322239126
    }
  },
  "modelVersion": "focus-sleep-pvt-1.0.0"
}
```

### AI-04 직접 호출

`POST /ai/v1/plan/sleep-recovery`

요청 JSON

```json
{
  "schema_version": "0.1",
  "now": "2026-11-09T18:00:00+09:00",
  "as_of_date": "2026-11-09",
  "preferences": {
    "adult_confirmed": true,
    "target_minutes": 480
  },
  "sleep_records": [],
  "events": []
}
```

응답 JSON 발췌

```json
{
  "result": {
    "schema_version": "0.1",
    "rule_version": "opportunity-v4-history-calendar",
    "as_of_date": "2026-11-09",
    "warnings": [
      "NOT_A_PHYSIOLOGICAL_DEBT_MODEL",
      "CALENDAR_COVERAGE_NOT_VERIFIED",
      "ADDITIONAL_SLEEP_DEFERRED_INCOMPLETE_HISTORY",
      "INCOMPLETE_SLEEP_RECORDS"
    ],
    "sleep_summary": {
      "metric": "target_night_sleep_shortfall",
      "data_status": "no_records",
      "shortfall_minutes": 0
    },
    "recovery_plan": {
      "policy": {
        "mode": "baseline_only",
        "requested_extra_minutes_per_night": null
      }
    }
  },
  "modelVersion": "opportunity-v4-history-calendar"
}
```

### AI-05 직접 호출

`POST /ai/v1/schedule/label`

요청 JSON

```json
{
  "text": "자료구조 중간고사",
  "user_rules": [],
  "options": {
    "reference_at": "2026-11-10T18:00:00+09:00"
  }
}
```

응답 JSON

```json
{
  "result": {
    "label": "시험",
    "sleep_priority": "높음",
    "reason": "평가를 받는 시험 일정으로 집중력과 컨디션이 중요합니다.",
    "matched_rule": "exam",
    "matched_text": "중간고사",
    "source": "rule",
    "needs_user_input": false,
    "matches": [
      {
        "label": "시험",
        "sleep_priority": "높음",
        "reason": "평가를 받는 시험 일정으로 집중력과 컨디션이 중요합니다.",
        "matched_rule": "exam",
        "matched_text": "중간고사"
      }
    ],
    "excluded": [],
    "rule_version": "1.2.0",
    "course": "자료구조",
    "course_candidates": [
      "자료구조"
    ],
    "course_matches": [
      {
        "course": "자료구조",
        "expression": "자료구조",
        "is_alias": false
      }
    ],
    "needs_course_selection": false,
    "activity": "exam",
    "assessment": {
      "base_priority": "높음",
      "importance": null,
      "focus": null,
      "final_priority": "높음",
      "priority_source": "base_rule",
      "urgency": "미입력",
      "start_status": "미입력",
      "factors": [
        "기본 분류 중요도: 높음",
        "사용자 중요도: 미입력",
        "집중력 요구: 미입력"
      ],
      "starts_at": null,
      "deadline_at": null,
      "reference_at": "2026-11-10T18:00:00+09:00",
      "missing_inputs": [
        "importance",
        "focus",
        "starts_at",
        "deadline_at"
      ]
    }
  },
  "modelVersion": "1.2.0"
}
```

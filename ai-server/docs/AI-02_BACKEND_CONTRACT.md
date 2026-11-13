# AI-02 백엔드 요청 JSON 변경 명세

현재 AI-02는 평상시 전용입니다. 시험 추천은 [AI-03 계약](AI-03_BACKEND_CONTRACT.md)을 사용하세요. examNextDay는 검증 후 false로 처리하고 EXAM 일정은 일반 고정 일정으로 취급하여 시험 가산을 적용하지 않습니다. 원시 모델의 기존 계산식은 유지합니다.

## 호출 방식

`POST {AI_BASE_URL}/ai/v1/plan/tonight`의 경로와 `{result, modelVersion}` 성공 응답은 유지합니다. Spring이 필요한 전체 원시 데이터 스냅샷을 전달하고 AI 서버는 Spring API를 재호출하지 않습니다.

Spring은 DB/서비스 계층에서 사용자 소유권과 HEALTH_DATA 동의를 확인하고 전체 페이지를 병합해야 합니다. 사용자 Bearer 토큰·DB 인증·이름·로그인 ID·이메일은 전달하지 않습니다. 조회 실패를 빈 배열로 바꾸지 마세요. 빈 배열은 실제 데이터가 없음을 뜻합니다.

| 항목 | 규칙 |
| --- | --- |
| 인증 | `X-Internal-Key: {AI_INTERNAL_KEY}` |
| 추적 | 선택 `X-Request-Id`: 영숫자·`-_.`, 1~128자. 그대로 응답에 반환. 없으면 생성 |
| 본문 | UTF-8 JSON, `Content-Type: application/json`, 최대 1MiB, 단일 Content-Length |
| 시각 | 오프셋 포함 ISO 8601. 날짜·요일·시계 시각은 KST 기준 |
| 타임아웃 | 서버 기본 계산 예산2.5초 + 정리·네트워크 여유를 포함해 Spring 제한 설정(예:4초부터 검증) |
| 확장 필드 | 알 수 없는 최상위 필드는 무시 |

## 필수·선택 데이터

GET 응답의 `{data,error}` 봉투가 아니라 아래 원시 배열·객체를 전달합니다. 이전의 firstEventStartAt/recentSleep 중심 요청에 필수 스냅샷을 추가해야 합니다.

| 필드 | 필수 | 내용·Spring 데이터 출처 |
| --- | --- | --- |
| now | O | Spring이 고정한 평가 시각. 재시도 시 동일한 값 사용 |
| targetDate | O | KST 기상일. now 기준 오늘/내일만 지원 |
| profile | O | USER-01의 data.settings와 최상위 data.prepMinEstimate를 병합 |
| sleepHistory | O | LOG-05/DB의 완료 수면 기록. now−35일부터 now 직전까지 기상한 전체 기록. 없으면 [] |
| timetable | O | TT-02의 data.entries. 전날·기상일에 적용되는 학기 수업 전체. 없으면 [] |
| events | O | EVT-01의 data.events. 전날·기상일과 겹치는 모든 일정. 반복 일정은 실제 발생 시각으로 펼침. 없으면 [] |
| examNextDay | X | 호환 필드. API에서 시험 가산을 적용하지 않음. 시험 추천은 AI-03 사용 |
| avgWakeTime | X | 자유일 기준 기상 HH:mm. 없으면 최근 14일 기상 원형 평균, 기록도 없으면 09:00 |
| userKey | X | 익명 키. 현재 계산에 사용·저장하지 않음 |
| firstEventStartAt | X | 기존 호환 필드. 기상일의 시각 또는 null. 전체 일정에서 도출 가능하므로 일반적으로 생략 |
| eveningEvents | X | 기존 호환 추가 일정 배열. 일반적으로 events에 합치고 생략 |
| recentSleep | X | 기존 호환 완료 수면 배열. 일반적으로 sleepHistory만 사용. 동일 세션은 sleepHistory 우선 |

현재 피로에는 최근 7일, 평소 습관에는 그보다 앞선 28일을 사용합니다. 평소 입력은 최소 7개 기상일과 질 평가 최소 3건이 있어야 모두 만들 수 있습니다. 기록이 부족해도 요청은 가능하며 기존 부분 계산·규칙 폴백을 적용합니다. 최근 7일만 보내면 평소 모델이 빠질 수 있습니다. 모델 자체의 기록 정제와 파생 특성 계산은 변경하지 않았습니다.

### profile 범위

| 필드 | 규칙 |
| --- | --- |
| sleepNeedMin | 필수 정수 300~600 |
| sleepLatencyMin | 필수 정수 0~60 |
| commuteMin | 필수 정수 0~240 |
| bufferMin | 필수 정수 0~60 |
| prepMin | 정수 0~240. prepMinEstimate가 없으면 필수 |
| prepMinEstimate | 선택/null, 정수 0~360. 있으면 prepMin보다 우선 |
| minSleepMin | 선택 정수 240~480, 기본 300. sleepNeedMin 이하 |
| chronotype | 전달 가능. 현재 계산기는 직접 사용하지 않음 |

### 수면 기록 형식 유지

`sleepLogId`(가능하면 전달), `date`(KST 기상일), `bedAt`, `wakeAt`, `durationMin`, `quality`, `status`, `source`를 전달합니다. `status=CLOSED`, `source=TAP/MANUAL/WEARABLE`, `quality=1~5 또는 null`입니다. `createdAt/updatedAt`이 있으면 함께 전달해 평가 시각 이후 생성·수정 기록의 혼입을 막습니다.

수동·탭 durationMin이 취침~기상 간격과 같으면 기존 로직이 침대 체류시간으로 보고 입면시간을 한 번 차감합니다. 이미 짧게 산출된 실제 수면량은 중복 차감하지 않습니다. WEARABLE은 실제 durationMin 또는 `stages: [{stage,startAt,endAt}]`가 필요합니다. 단계는 AWAKE/LIGHT/DEEP/REM이며 비어 있지 않은 단계 배열이 우선됩니다. 단계 사이 공백은 미관측으로 유지합니다.

원시 durationMin이나 quality를 Spring에서 임의로 재가공하지 마세요. Fatigue용 품질 0~4 변환, 최근/평소 창 분리는 기존 내부 함수가 담당합니다. 오류·중복·겹침 기록의 제외와 폴백도 기존 모델 동작을 유지합니다.

### 시간표·일정 형식

- 시간표: dayOfWeek(MON~SUN), startTime/endTime(HH:mm), 시작 < 종료. 추가 필드 허용.
- 일정: startAt, endAt(null 가능), allDay(기본 false), type(EXAM/ASSIGNMENT/PERSONAL). ID·제목·tag 등 추가 필드 허용.
- 종일 일정은 시간 제약에서 제외하고 종료가 null인 일정은 시작 시점 제약만 적용합니다.
- 날짜 경계를 넘는 일정도 누락하면 안 됩니다. 단순 시작일 필터로 놓치는 일정은 Spring에서 보완해야 합니다.

## 요청 예제

아직 수면 기록이 없는 사용자의 완전한 요청입니다. 실제 기록이 있으면 sleepHistory에 위 범위 전체 기록을 채웁니다.

```json
{
  "now": "2026-11-10T21:00:00+09:00",
  "targetDate": "2026-11-11",
  "profile": {
    "sleepNeedMin": 465,
    "sleepLatencyMin": 15,
    "commuteMin": 40,
    "prepMin": 55,
    "prepMinEstimate": 50,
    "bufferMin": 10,
    "minSleepMin": 300
  },
  "sleepHistory": [],
  "timetable": [
    {"dayOfWeek": "WED", "startTime": "09:00", "endTime": "10:15"}
  ],
  "events": [],
  "examNextDay": false
}
```

[기록을 포함한 전체 요청](examples/ai02-request.json)과 [그 요청의 실제 응답](examples/ai02-response.json)을 함께 제공합니다.

## 응답

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
    "reasons": ["첫 일정과 준비·통학 시간을 고려했어요.", "가능한 시간 안에서 목표 수면시간을 우선했어요."],
    "warnings": ["관측되지 않은 수면 중 각성은 추정하지 않았어요.", "최근 관측일이 부족해 규칙 계획을 사용했어요."]
  },
  "modelVersion": "ai02-normal-v1"
}
```

위 응답은 빈 수면 이력 예제에 대응합니다. sleepMin은 취침~기상 간격에서 입면시간을 뺀 값입니다. breakdown은 실제 적용한 입력 설정이며 자유일이면 null입니다. 15분 격자로 기상이 마감보다 일찍 잡혀도 그 차이를 bufferMin에 더하지 않습니다. 따라서 기존 API 예시의 bufferMin=13 대신 실제 설정 10을 반환합니다.

모델은 계획·내부 진단을 계산하고 서비스가 위 JSON을 조립합니다. 피로 점수·비용·ablation은 외부 응답에 포함하지 않습니다. reasons/warnings는 한국어 안내 배열이며 분기용 enum이 아닙니다. modelVersion은 계산 번들 버전입니다. 내부 RULE/OPTIMIZED는 앱 PlanMode와 다르며, 내부 규칙 계획도 정상 성공 응답으로 반환합니다. Spring이 generatedBy·PlanMode·저장·알림을 담당합니다.

## 실패와 기존 문서 대비 유의점

| HTTP | code | 내용 |
| --- | --- | --- |
| 400 | INVALID_INPUT | 필수 필드 누락·형식·설정·시각·JSON 오류 |
| 401 | UNAUTHORIZED | 내부 인증 실패 |
| 404 | NOT_FOUND | 미등록 엔드포인트 |
| 413 / 415 | INVALID_INPUT | 본문 크기 / Content-Type 오류 |
| 422 | SOLVER_INFEASIBLE | AI-02도 현재 시각 이후 가능한 최소 수면 계획이 없을 수 있음 |
| 503 | MODEL_UNAVAILABLE | 모델 파일 등 실행 자원 사용 불가 |
| 500 | INTERNAL_ERROR | 예상하지 못한 처리 오류 |

```json
{"error":{"code":"INVALID_INPUT","message":"필수 스냅샷 필드가 없습니다.","details":[{"field":"sleepHistory","reason":"필수 스냅샷 필드가 없습니다."}]}}
```

- 원본 문서는 SOLVER_INFEASIBLE를 AI-03 전용으로 설명하지만 현 AI-02에도 해가 없는 경우가 있어 422로 명시합니다. Spring은 이를 구분하고 규칙 폴백을 수행하더라도 시간·최소 수면 제약을 다시 검증해야 합니다. 400·401은 수정 없이 재시도하지 마세요.
- 원본 공통 규칙의 '요청 이전 취침도 반환'과 달리 기존 계산기는 현재 이후 실행 가능한 계획을 탐색합니다. 계산식을 보존하기 위해 그대로 유지했습니다.
- 모델 내부 수면 부족량은 관측일별 부족분 합계이며 백엔드의 초과 수면 상쇄형 부채와 다릅니다. 해당 값을 입력받거나 외부로 반환하지 않습니다.
- 시험기간 다일 최적화·학습/낮잠 슬롯·AI-04 이후 호출·Fatigue 개인화 적용은 이번 AI-02 연결에 포함하지 않습니다.

## 공통 서버 규칙

인증·JSON 중복 키/비유한 수/깊이 제한·메서드405·본문 수신408·실행/배포 제약은 [통합 서버 명세](INTEGRATED_SERVER_SPEC.md)를 함께 적용합니다. 호출별 필수값·계산 의미는 이 문서를 따릅니다.

입력 개수와 실행 제한도 [통합 서버 명세](INTEGRATED_SERVER_SPEC.md)를 적용합니다. 시간 초과504, 호출량429, 과부하/회로 차단503은 기존 도메인400/422와 구분하세요.

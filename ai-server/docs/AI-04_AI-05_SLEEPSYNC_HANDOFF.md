# SleepSync 공개 Swagger와 AI-04·05 내부 연동

공개 OpenAPI에서 확인한 앱→Spring 계약과 저장소에서 검증한 Spring→AI 계약을 연결하기 위한 인계 문서다. [배포 Swagger UI](https://team11-h10w.onrender.com/swagger-ui/index.html#/) · [OpenAPI JSON](https://team11-h10w.onrender.com/v3/api-docs)

| 기능 | 앱 → Spring 공개 API | Spring → Python 내부 AI |
| --- | --- | --- |
| 수면 부채·회복 | `GET /api/v1/sleep-debt?range=…`, 필수 문자열 `range` | `POST /ai/v1/sleep-debt/calculate`, 인증된 일별 수면·일정 스냅샷 |
| 예정 일정 위험 안내 | `GET /api/v1/insights/upcoming-risks?days=…`, 선택 정수 `days` | `POST /ai/v1/insights/risks`, 인증된 일정 분류 배치 |

공개 API의 응답 봉투는 `{data,error}`이고, 내부 AI는 성공 시 `{result,modelVersion}`을 반환한다. 두 공개 GET은 조회 범위만 받으므로 Spring이 사용자 식별 및 조회를 수행하고 내부 POST의 본문을 구성해야 한다.

## AI-04 수면 부채와 회복

1. Spring에서 해당 사용자의 기록과 목표를 조회한다. 종료된 기록의 기상일을 서울 날짜로 정리해 내부 계약의 `asOfDate`, `preferences`, `sleepRecords`, `events`를 구성한다.
2. 공개 `SleepDebtResponse`에는 `range`, `sleepNeedMin`, `currentDebtMin`, `missingDays`, `daily`, `recovery`, `insufficientData`가 있다. 공개 일별 항목의 `deltaMin`과 `cumulativeDebtMin`은 Spring의 기존 부채 집계 정의를 따른다. AI의 `sleepSummary.shortfallMinutes`는 기록된 날짜의 `max(0, 목표−야간수면)` 합이므로 `currentDebtMin`에 그대로 대입하면 기존 의미가 달라진다.
3. `recoveryPlan`을 공개 `recovery`에 반영하려면 Spring에서 공개 DTO로 변환한다. 기록 부족·계획 불가·일정 변경 전제의 상태를 먼저 처리한다. [내부 요청·응답 계약](AI-04_BACKEND_CONTRACT.md)

## AI-05 예정 일정 위험 안내

1. Spring에서 조회 범위 안의 일정과 사용자 설정을 모은다. 공개 일정 조회 경로는 `GET /api/v1/events`이며 날짜 `from`, `to`를 받는다. 내부 호출에는 일정 ID와 텍스트, 조회 기준 `now`, 가능한 경우 시각과 과목·사용자 규칙을 보낸다.
2. AI는 `analyses[]`를 반환한다. 공개 `UpcomingRisksResponse`는 `risks[]`이고 각 항목은 `date`, `type`, `severity`, `title`, `message`, `relatedEventId`, `suggestion`으로 구성된다. 원본 일정과 분류 결과는 `eventId`로 연결한다. 공개 `relatedEventId`는 int64이므로 공개 응답에 연결할 일정은 Spring의 숫자 ID를 사용한다.
3. 어떤 분류 결과를 공개 위험 항목에 포함할지와 `severity`, `suggestion`의 의미는 Spring의 정책에서 결정한다. 공개 명세에서 그 변환 규칙은 확인되지 않았다. AI의 `sleepPriority`는 숙면 중요도이고, `assessment.urgency`는 일정 시각에 대한 판정이다. [내부 요청·응답 계약](AI-05_BACKEND_CONTRACT.md)

공개 Swagger는 Spring의 외부 계약을 확인하는 자료다. Spring 소스와 인증된 서비스 실행 환경에서 각 GET 핸들러가 실제 내부 POST를 호출하는지 확인해야 한다.

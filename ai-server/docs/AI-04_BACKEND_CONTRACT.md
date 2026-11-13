# AI-04 수면 부채 집계·회복 기회 계산

[SleepSync 공개 Swagger와 Spring 연동](AI-04_AI-05_SLEEPSYNC_HANDOFF.md)

## 역할과 경로

**POST /ai/v1/sleep-debt/calculate**. AI-04의 기존 준비시간 추정 역할은 폐기하고 이 계약으로 교체합니다. `/ai/v1/prep-time/estimate`는 제공하지 않습니다. 준비시간·통학시간이 필요한 기존 AI-02는 백엔드가 보유한 설정·추정값을 계속 전달해야 합니다.

공통 규칙은 AI-02/AI-03과 동일합니다: X-Internal-Key 인증, 선택 X-Request-Id 반환, UTF-8 application/json, 본문 최대1MiB, 성공 `{result,modelVersion}`, 실패 `{error:{code,message,details?}}`. 모델 버전은 sleep-coach-v4-history-calendar입니다. AI 내부에서는 백엔드 조회·DB 접근·LLM HTTP 호출을 하지 않습니다.

## 채택 버전과 수면 부채의 의미

두 전달본 중 `sleep-coach-ai-demo-v4-history-calendar`를 채택했습니다. v3 backend-mts-review와 shortfall.py 및 performance_model.py의 원본 바이트가 같고, v4가 기록 부족 시 추가 수면 판단 보류와 변경 불명 일정 확인을 보완합니다. 핵심 계산·그룹 UMP 계수·규칙을 보존했습니다. 학습된 개인별 가중치 파일은 원본에 없습니다.

**shortfallMinutes = 기록된 각 날의 max(0, 목표−실제 야간 수면) 합계**입니다. 초과 수면·낮잠은 별도로 집계하며 부족분에서 1:1 차감하지 않습니다. 원래 백엔드 API 문서 STAT-01의 초과 수면 상쇄형 누적 부채와 다른 정의입니다. 백엔드가 기존 `/sleep-debt` 응답을 유지하려면 이 값을 기존 잔액과 같은 값으로 취급하면 안 됩니다. `metric=target_night_sleep_shortfall`, 기록 범위와 결측일을 함께 노출하도록 API 의미를 수정하거나 별도 필드로 제공하세요.

physiologicalDebtMinutes와 estimatedRecoveryCompletion은 null이며 개인별 생리적 부채·회복 완료 예측을 제공하지 않습니다. UMP의 무차원 debt 상태와 분 단위 부족량도 서로 다른 값입니다.

## 요청: Spring이 전체 원시 스냅샷을 구성

| 필드 | 필수·기본 | 의미 |
| --- | --- | --- |
| now | 필수 | 평가 시각. 오프셋 포함 **분 정밀도**(초·마이크로초0) |
| asOfDate | 필수 | 집계 마지막 기상일 yyyy-MM-dd. now의 한국 날짜 이하 |
| preferences | 필수 객체 | 성인 확인·목표·준비/통학·입면·선호 시각 |
| sleepRecords | 필수 배열 | 날짜당 한 건인 일별 기록, 최대90개. 없으면 [] |
| events | 필수 배열 | 향후 계획기간과 겹치는 전체 수업·일정, 최대100개. 없으면 [] |
| windowDays | 선택, 7 | 집계 기간1~60일 |
| planDays | 선택, 3 | 미래 회복 기회 계획1~7일 |
| schemaVersion | 선택, "0.1" | 원본 내부 계약 버전 |

preferences:

- adultConfirmed: **true 필수**. 성인용 원본의 제약입니다.
- targetMinutes: 420~720 정수, 기본480. 미래 계획의 실제 수면 용량 목표.
- preparationCommuteMinutes: 0~360 정수, 기본0. 일정 전 준비·이동시간.
- sleepLatencyMinutes: 0~120 정수, 기본20. 입면 가정.
- preferredBedtime / preferredWakeTime: 로컬 HH:mm, 기본23:00 / 07:00.

sleepRecords[]:

| 필드 | 의미 |
| --- | --- |
| date | 필수, 한국 기상일. 동일 날짜 중복은 오류 |
| status | recorded(기본), missing, all_nighter |
| targetMinutes | 해당 날짜 목표420~720분. 생략하면 서버 설정480을 사용하며 preferences.targetMinutes를 자동 소급하지 않음 |
| actualSleepMinutes | 실제 야간 수면0~1440분. 시각 없이 실제 분량만 제공 가능 |
| sleepStart / sleepEnd | 선택 쌍. 제공 시 둘 다 필요, 마지막 기상일이 date와 일치하고 미래 수면이 아니어야 함 |
| awakeMinutes | 시각 구간 안의 입면·각성 분량, 기본0. 시각이 있으면 actual=구간−awake와 일치해야 함 |
| napMinutes | 별도 낮잠 분량, 기본0. 야간+낮잠은 하루1440분 이하 |

LOG-05의 세션별 SleepLog를 그대로 넣는 배열이 아닙니다. Spring이 완료 기록을 **기상일당 한 건으로 정리**해야 합니다. 여러 세션을 근거 없이 연속 수면으로 합성하지 마세요. 신뢰할 실제 분량이 있으면 actualSleepMinutes만 보내도 됩니다. 수동 침대 체류량을 실제 수면으로 잘못 전달하지 않아야 합니다. 시각이 없으면 원본 모델은 선호 기상으로 이력을 추정하고 경고를 반환할 수 있습니다.

미기록은 배열에서 생략하거나 status=missing으로 표시합니다. missing에는 측정값을 넣지 않습니다. **기록 없음은 밤샘이 아닙니다.** all_nighter는 사용자가 실제 밤샘을 확인한 경우만 사용하며 실제 야간수면0, 시각 없음으로 전달합니다. targetMinutes와 실제 수면은 분 단위 정수입니다.

events[]:

- eventId: 필수 숫자 또는 문자열. 중복 불가, 한 요청에서는 자료형 통일.
- title, description: 일정 텍스트. 키워드 기준선으로 분류하므로 제목 전달 권장.
- startAt / endAt: 필수, 오프셋 포함 분 정밀도, 시작<종료.
- leadMinutes: 선택0~360분. 있으면 공통 preparationCommuteMinutes보다 우선.
- labels / userOverride: 선택 객체. type(exam/presentation_interview/class/study/personal/other), focusDemand(low/medium/high/unknown), movability(flexible/fixed/unknown). userOverride가 우선.

Spring이 시간표·반복 일정을 실제 발생 구간으로 펼치고 다른 일정과 합쳐 전달합니다. 이동 가능성을 모르면 unknown으로 유지합니다. flexible을 자동 승인으로 취급하지 마세요. AI-05과 이 모델의 내장 라벨러는 별개 규칙이며 자동 대체 연결하지 않습니다. 사용자 확인값만 위 계약에 맞춰 전달할 수 있습니다.

## 응답과 처리

전체 실행 예제: [요청](examples/ai04-request.json) · [실제 응답](examples/ai04-response.json).

result 주요 필드:

- schemaVersion, ruleVersion, asOfDate
- sleepSummary: shortfallMinutes, aboveTargetMinutes, napMinutes, recordedDays, windowDays, coverageFraction, missingDates, dataStatus(no_records/partial/complete), daily, physiologicalDebtMinutes(null)
- events: 원본 일정과 labels·규칙 근거. 원본 모델의 ID 필드는 id, 시각은 start/end로 반환됩니다.
- recoveryPlan.policy: model / adaptive_fallback / baseline_only와 적용 이유
- recoveryPlan.plans: 기상일별 bedtime/wakeTime, 잠자리 시간, 계획 수면 용량, 목표 미달, 일정 조정 전제 등
- recoveryPlan.movabilityConfirmationRequests: 이동 가능 여부 확인 요청. hypotheticalOnly=true인 조건부 결과이며 실제 일정 변경 아님
- warnings: 결측·개인화되지 않은 그룹 수행 모형·미검증 캘린더 등 원본 경고 코드

기록이 부족하면 baseline_only이며 추가 수면 목표는 null입니다. 부족량0 또는 추가량null을 '피로 없음/추가 수면 불필요'로 표시하지 마세요. 기본 계획은 계속 반환할 수 있습니다. no_feasible_opportunity 또는 requires_rescheduling 등 계획 상태도 원본대로 HTTP200 안에 반환합니다. 수면 부족 집계 자체가 유효하므로 해당 상태를 전부 HTTP422로 바꾸지 않습니다. flexible 일정은 실제 이동하지 않으며 Spring이 승인·재계산을 관리합니다.

잘못된 입력은 400 INVALID_INPUT, 자원 파일 사용 불가는 공통503입니다. 수면 기록·동의·일정 조회 실패를 []로 숨기지 않아야 합니다. 응답의 추가 수면 계획은 개인별 임상 회복 효과가 검증된 처방이 아닙니다.

이 구현은 Asia/Seoul 전용이며 Windows에서도 같은 IANA Seoul TZif를 사용하도록 시간대 리소스를 포함했습니다. 변경 가능한 정책은 서버의 ai_server/ai_04/config.json에서 관리하며, 요청에서 임의 수식·분류기 URL·외부 모델을 지정할 수 없습니다.

## 공통 서버 규칙

인증·JSON 중복 키/비유한 수/깊이 제한·메서드405·본문 수신408·실행/배포 제약은 [통합 서버 명세](INTEGRATED_SERVER_SPEC.md)를 함께 적용합니다. 호출별 필수값·계산 의미는 이 문서를 따릅니다.

계산은 공통 프로세스 예산·호출량·동시 처리·서킷 제어를 적용합니다. 기존 도메인 상태 외에504 MODEL_TIMEOUT,429 RATE_LIMITED,503 SERVER_BUSY/CIRCUIT_OPEN을 처리해야 합니다. 정상 응답 JSON은 그대로입니다.

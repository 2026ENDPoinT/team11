# AI-02 평상시 수면 추천

POST /ai/v1/plan/tonight. modelVersion=ai02-normal-v1. Spring이 필요한 전체 스냅샷을 전달하며 모델이 백엔드를 조회하지 않습니다.

```text
preprocessing.prepare_request
 → Sleep_plan.planner.analyze_plan
     → Sleep_plan.history (완료 기록 정제·실제 수면 추정·타임라인)
     → Fatigue.inputs/model/integrate (현재 피로 1회 계산)
 → service.calculate (원시 계획 + breakdown·한국어 설명 → result/modelVersion)
```

[계산 흐름도](FLOW.md) · [통합 백엔드 연동](../BACKEND_INTEGRATION.md)

## 설계와 기반 자료

평상시 계획은 수업과 일정, 준비·이동 시간으로 기상 마감을 정하고 15분 격자에서 취침·기상 후보를 비교합니다. 후보의 실제 수면 추정량은 `기상 − 취침 − 입면시간`입니다. 비용에는 수면 압력과 평소 수면 중간 시각으로부터의 거리, 수면 부족과 일정 관련 항이 들어갑니다. [계획기](Sleep_plan/planner.py)

- **수면 압력.** Borbély의 수면 조절 두 과정 모형에서 수면·각성에 따라 변하는 Process S가 개념적 배경입니다. 코드에서는 깨어 있는 동안 `S' = 1 − (1 − S) exp(−t/τw)`, 자는 동안 `S' = S exp(−t/τs)`를 사용합니다. 기본 시간 상수는 각각 18.2시간과 4.2시간입니다. 계획의 비용과 가중치는 구현에서 정합니다. [Borbély (1982)](https://pubmed.ncbi.nlm.nih.gov/7185792/) · [계산 코드](Sleep_plan/planner.py)
- **피로 입력과 학습 산출물.** 최근 7일 관측과 그 이전 28일의 습관을 나눠 사용합니다. 내장 M1 회귀·보정 계수와 M2/M3 확률표는 JSON 산출물로 제공되며, 가용 모델의 점수를 기본 비중 75·10·15로 결합합니다. 원본 학습 데이터의 이름과 행은 현재 배포 자료에서 확인할 수 없습니다. 요청으로 받은 사용자 기록은 추론용 입력입니다. [Fatigue 설명](Fatigue/README.md) · [모델 산출물](Fatigue/models.json) · [가중치](Fatigue/weights.json)

## 입력·출력 경계

필수 입력은 now, targetDate, profile, sleepHistory, timetable, events입니다. 배열이 실제로 비어 있으면 []를 사용합니다. now는 오프셋 포함, targetDate는 한국 시간 기준 오늘/내일의 기상일입니다. profile 범위와 모든 선택 필드는 [백엔드 계약](../../docs/AI-02_BACKEND_CONTRACT.md)에 있습니다.

**HTTP API는 평상시 전용**입니다. 전처리가 examNextDay를 false로 만들고 EXAM 일정을 일반 고정 일정으로 바꿔 시험 가산과 시험 중요도 가중을 적용하지 않습니다. 해당 일정의 시간 충돌 제약은 유지합니다. 하위 Sleep_plan의 원시 함수·CLI에는 이전 시험 처리 기능이 남아 있으므로 원시 호출과 HTTP를 혼동하지 마세요.

완료 수면은 최대35일, 최근7일과 그 이전28일의 용도가 다릅니다. 수면 기록 오류는 기존 정제·폴백 정책에 따라 제외할 수 있으며 모든 오류 기록이 HTTP400을 만드는 것은 아닙니다. 최근 관측 기상일5일 미만이나 민감도 불안정 등은 규칙 계획으로 전환합니다.

서비스는 Fatigue 모델 메타데이터·판정선·기본 가중치의 사용 가능 여부를 검사합니다. 사용 불가하면503 MODEL_UNAVAILABLE입니다. 정상 입력으로 계획이 없으면422 SOLVER_INFEASIBLE입니다. 결과의 recommendedBedAt/recommendedWakeAt/sleepMin과 breakdown/reasons/warnings를 응답하며 내부 비용·피로·ablation은 HTTP 응답에서 제외합니다. 사용자 설정/수면 패턴에 따라 짧은 수면 계획이 나올 수 있고 minSleepMin은 AI-03의 개인2주기 하한과 다릅니다.

## 실행·검증

통합 서버 실행과 인증은 [통합 백엔드 연동](../BACKEND_INTEGRATION.md)을 따릅니다. [요청](../../docs/examples/ai02-request.json) · [실제 응답](../../docs/examples/ai02-response.json).

하위 모듈의 실행과 역할은 [Sleep_plan](Sleep_plan/README.md), [Fatigue](Fatigue/README.md)에 있습니다. 원시 CLI와 통합 HTTP의 정책 차이 외에는 계산식·학습 산출물을 수정하지 않았으며 24개 이동 전 전체 결과·파일 해시를 회귀검증합니다.

## 공통 실행 제어 (2026-11-11)

일반 통합 서버는 전처리부터 계산·응답 조립까지 별도 spawn 프로세스에서 실행하고, 기본2.5초 예산 초과 시 프로세스를 종료합니다. 동시 계산·호출량·서킷·리소스 무결성 검사는 부모 서버에서 수행합니다. 원시 Python 함수를 직접 호출할 때는 이 보호가 자동 적용되지 않습니다. [통합 서버 명세](../../docs/INTEGRATED_SERVER_SPEC.md)를 참고하세요.

입력 개수: sleepHistory/recentSleep/timetable/events/eveningEvents 각각500개, 수면 세션별 stages500개. 초과 시400으로 거부하며 자동으로 기록을 잘라 계산하지 않습니다.

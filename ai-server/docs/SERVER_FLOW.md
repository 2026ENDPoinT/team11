# 백엔드 ↔ AI 서버 전체 흐름

## 전체 요청·응답

```mermaid
flowchart TD
    App[앱] <--> Spring[Spring 백엔드]
    DB[(설정·수면·일정·시험·개인 규칙 DB)] <--> Spring
    Spring --> B[소유권·동의 확인 / 필요한 전체 데이터 조회]
    B --> Snapshot[페이지 병합·반복 일정 전개 / 호출별 JSON 스냅샷]
    Snapshot -->|HTTPS·내부 키·요청 ID| HTTP[AI HTTP 접수 / 연결 상한]
    HTTP --> Auth[인증·크기·JSON·리소스 검사]
    Auth --> Gate[호출량·동시 계산·서킷 검사]
    Gate --> Worker[시간 제한이 있는 spawn 프로세스]
    Worker --> Service[호출별 service → 전처리 → 모델 → 응답 조립]
    Service -->|계산 결과 또는 도메인 오류| HTTP
    Worker -->|예산 초과: 프로세스 종료| Timeout[504 MODEL_TIMEOUT]
    Timeout --> HTTP
    HTTP -->|응답 JSON·상태·동일 요청 ID| Spring
    Spring --> Validate[시각·범위·일정 검증 / 저장·알림·앱용 응답]
    Validate --> App
    HTTP --> Log[원문 없는 접근 로그·집계 메트릭]
```

AI는 DB나 백엔드로 데이터 보완 요청을 하지 않습니다. 모델에 전달할 데이터는 요청에 완성되어 있어야 합니다. 응답 형식은 서비스가 만들고, 실제 DB planId/slotId 발급은 Spring의 책임입니다.

## AI 내부: 호출별 계산

```mermaid
flowchart TD
    Route[경로별 호출 선택]
    Route -->|AI-01 GET health| Health[health 서비스 / 상태·버전만 응답]
    Route -->|AI-02 POST plan/tonight| P2[평상시 전처리 / 시험 가산 비활성화]
    P2 --> H2[Sleep_plan.history / 완료 기록·실제 수면·타임라인 정리]
    H2 --> F2[Fatigue.inputs / 최근7일·이전28일 특성]
    F2 --> M2[Fatigue.model / M1 회귀·M2/M3 확률표]
    M2 --> I2[Fatigue.integrate / 보정 점수·기본 가중 통합]
    I2 --> O2[Sleep_plan.planner / 후보·Process S·제약·비용·규칙 폴백]
    Route -->|AI-03 POST plan/exam-sleep| P3[시험 준비 상태·시험 유형·수면 기록·고정 일정]
    P3 --> C3[주수면·낮잠·공부 배치 후보]
    C3 --> K3[기억과 수행 대리 효용 / 256개 가정]
    K3 --> O3[최악의 상대 후회값 비교]
    Route -->|AI-04 POST sleep-debt/calculate| P4[부채 전처리 / 일별 기록·이동 가능성·원본 필드 변환]
    P4 --> D4[sleep_coach.shortfall / 관측 목표 미달 집계]
    P4 --> L4[sleep_coach.labeler / 원본 키워드·사용자 확인값]
    D4 --> O4[planner·recovery_policy·performance_model / 회복 기회 비교]
    L4 --> O4
    Route -->|AI-05 POST insights/risks| P5[일괄 일정 전처리 / 텍스트·사용자 규칙·설정]
    P5 --> O5[rules·academic·assessment·preferences / 분류·근거·숙면 중요도·긴급도]
    Route -->|AI-06 POST plan/focus-sleep| P6[기상 마감·침대 체류 범위·주수면 기록]
    P6 --> C6[15분 후보 / 개인 실수면 추정]
    Artifact6[오프라인 PVT 회귀 모델] --> M6[PVT 점수와 지원 범위 평가]
    C6 --> M6
    M6 --> S6[PVT 순위 또는 명목 주기 대체안]
    S6 --> Out[호출별 service / 공개 JSON 조립]
    O2 --> Out
    O3 --> Out
    O4 --> Out
    O5 --> Out
    Out --> Parent[부모 HTTP 서버 → Spring]
    Health --> Parent
```

AI-02~06의 전처리·모델·응답 조립은 같은 요청의 자식 프로세스에서 실행됩니다. AI-03 벼락치기 추천과 AI-06 집중 모드는 각각의 모델로 계산합니다.

## 운영 상태 흐름

```mermaid
flowchart LR
    Start[서버 시작] --> Hash[manifest SHA-256 확인]
    Hash --> Smoke[시작 점검 입력으로 AI-02~06 실행]
    Smoke --> Ready[인증된 GET ready]
    Request[각 계산 요청] --> Check[리소스 해시 재확인]
    Check --> Limited[입력·호출량·동시 계산 제한]
    Limited --> Execute[프로세스 계산]
    Execute --> Metrics[접근 로그·인증된 GET metrics]
```

health는 liveness이고, ready는 현재 배포 리소스와 시작 추론 결과입니다. 임의의 모든 사용자 입력이 성공한다는 의미는 아닙니다. 400/422·429·503·504의 처리와 재시도 기준은 [서버 명세](INTEGRATED_SERVER_SPEC.md)를 따릅니다.



# SLEEP 통합 AI 서버 명세

검토 기준: 2026-11-12, 현재 코드와 실행 예제. 노션의 과거 호출 번호·스키마보다 이 저장소의 계약을 우선합니다.

## 1. 책임과 전체 흐름

```text
앱
 → Spring: 로그인·소유권·건강정보 동의 확인, DB 조회, 페이지 병합, 반복 일정 전개
 → HTTP 요청 (완전한 사용자별 스냅샷, X-Internal-Key, X-Request-Id)
 → ai_server/server.py: 인증·본문 제한·JSON 검사·라우팅
 → 실행 제어: 호출량·동시 계산·서킷 검사, 리소스 무결성 확인
 → 경로별 기한의 계산 프로세스(AI-02·04·05 2.5초 / AI-03·06 10초) → routes.POST_ROUTES: 호출별 service.calculate
 → 각 preprocessing.prepare_request: DTO 검증·이름/단위/시간 의미 변환
 → 해당 모델: 원시 계산 결과
 → 각 service: 공개 JSON 응답 조립·도메인 오류 매핑
 → Spring: 결과 검증·앱용 설명·저장/알림·필요한 재계산
```

AI-01은 모델·전처리 없이 health 서비스가 응답합니다. 기본 실행은 spawn 자식 프로세스이며 시간 초과·종료 시 정리합니다. 테스트 전용 inline 설정은 하드 타임아웃을 보장하지 않습니다.

AI 서버는 백엔드를 재호출하지 않으며 사용자 JWT·DB 연결·사용자별 영구 저장이 없습니다. 읽는 파일은 학습 산출물·기본 가중치·설정·시간대 등 서버 리소스입니다. 모델의 읽기 전용 캐시는 존재하지만 사용자 기록/결과 캐시는 없습니다.

## 2. 호출 목록

| 번호 | 메서드·경로 | 역할 | modelVersion |
| --- | --- | --- | --- |
| AI-01 | GET /ai/v1/health | 서버 응답 상태·호출별 버전 | 최상위 값은 호환용 AI-02 버전 |
| AI-02 | POST /ai/v1/plan/tonight | 평상시 단일일 수면 계획 | ai02-normal-v1 |
| AI-03 | POST /ai/v1/plan/exam-sleep | 벼락치기 수면·공부 배치 | exam-sleep-workload-2.0.0 |
| AI-04 | POST /ai/v1/sleep-debt/calculate | 목표 미달 수면 집계·회복 기회 | sleep-coach-v4-history-calendar |
| AI-05 | POST /ai/v1/insights/risks | 일정 일괄 분류 | schedule-labeler-1.2.0 |
| AI-06 | POST /ai/v1/plan/focus-sleep | 집중 모드 수면 추천 | focus-sleep-pvt-1.0.0 |

AI-06은 공통 worker 슬롯과 기본 10초 기한을 사용합니다. 계약은 [AI-06 집중 모드](AI-06_FOCUS_BACKEND_CONTRACT.md)를 따릅니다.

운영 보조 경로: 인증된 GET /ai/v1/ready와 GET /ai/v1/metrics. 새 모델 호출 번호를 부여하는 API가 아닙니다.

시험 수면의 현재 경로와 스키마는 [AI-03 계약](AI-03_BACKEND_CONTRACT.md)을 적용합니다.

경로는 정확히 일치해야 합니다. 마지막 / 또는 쿼리를 자동 정규화하지 않습니다. 브라우저가 직접 AI를 호출하는 공용 서비스나 CORS API가 아닙니다. demo/는 별도 로컬 중계 서버입니다.

## 3. 공통 HTTP 계약

### 인증·추적

- 계산 API는 X-Internal-Key 필수. 서버 AI_INTERNAL_KEY와 상수 시간 비교합니다. 사용자 Bearer 토큰으로 대체하지 않습니다.
- 키는 공백 없는 출력 가능한 ASCII 문자열입니다. ready/metrics도 같은 키로 인증합니다. 환경에 주입하고 코드·문서·요청 본문·앱에 저장하지 않습니다.
- GET /health만 키 없이 호출합니다. 서버를 시작하는 환경에는 키가 필요합니다.
- X-Request-Id 선택, 영숫자·`-_.` 1~128자. 없으면 UUID hex 생성, 모든 핸들러 응답 헤더에 반환합니다. 값이 부적절하면400과 새 생성 ID를 반환합니다.
- 모델 버전과 정책 버전은 구분합니다. AI-03/04 결과에 정책·규칙 버전이 추가될 수 있습니다.

### 본문

- UTF-8 application/json. charset 파라미터가 있어도 UTF-8로 해석합니다.
- 하나의 Content-Length 필요. chunked 등 Transfer-Encoding은 지원하지 않습니다.
- 1~1,048,576바이트. 빈 본문/잘못된 길이는400, 상한 초과413입니다.
- JSON의 NaN/Infinity 및 1e999 같은 float overflow를 거부합니다. 중복 객체 키와 컨테이너 중첩 깊이64 초과도400입니다.
- 각 필드의 필수·null·기본값은 호출별로 다릅니다. 생략 가능한 숫자 필드라도 null이 항상 기본값을 의미하지는 않습니다.
- 모든 날짜 시각의 기준 now는 Spring이 고정해 전달합니다. 재시도에도 동일한 스냅샷을 사용합니다.
- 미지원 메서드는405 JSON과 Allow 헤더를 반환합니다. HEAD는 본문을 쓰지 않습니다. 손상된 HTTP 요청선/헤더 등 핸들러 진입 이전 오류는 표준 라이브러리 처리 범위입니다.

### 응답

계산 성공: `{ "result": { ... }, "modelVersion": "..." }`.

헬스체크: `{ "status": "ok", "modelVersion": "ai02-normal-v1", "models": { ... } }`.

실패: `{ "error": { "code": "...", "message": "한국어", "details": ... } }`. details는 선택이며 필드 오류 배열 또는 도메인 진단 객체일 수 있습니다. 모든 입력 오류가 details.field를 제공하는 것은 아닙니다.

Content-Type은 application/json; charset=utf-8, Content-Length와 Cache-Control: no-store가 붙습니다. 기본 HTTP 서버는 HTTP/1.0 응답 방식이며 연결 유지·HTTP/2를 전제로 하지 않습니다.

| HTTP | code | 의미·Spring 처리 |
| --- | --- | --- |
| 400 | INVALID_INPUT | 입력·시간·JSON 오류. 수정 없이 재시도하지 않음 |
| 401 | UNAUTHORIZED | 내부 키 불일치. 사용자 JWT 재발급 문제와 구분 |
| 404 | NOT_FOUND | 미등록 경로. 인증 없는 계산 경로 요청은 먼저401이 될 수 있음 |
| 405 | METHOD_NOT_ALLOWED | GET/POST 계약 불일치 |
| 408 | REQUEST_TIMEOUT | 요청 본문 수신 타임아웃. 모델 장애가 아님 |
| 413 / 415 | INVALID_INPUT | 크기 초과 / Content-Type 불일치 |
| 422 | SOLVER_INFEASIBLE | AI-02 계획 또는 AI-03 주수면 배치 불가. 호출별 진단 확인 |
| 503 | MODEL_UNAVAILABLE | 파일·모델·가중치·시간대 자원 사용 불가 |
| 500 | INTERNAL_ERROR | 예상하지 못한 계산/서버 오류 |

계산 과부하·회로 차단은503 SERVER_BUSY/CIRCUIT_OPEN, 호출량 초과는429 RATE_LIMITED, 계산 제한 초과는504 MODEL_TIMEOUT입니다. 429/503에는 Retry-After가 붙습니다. 연결이 가득 찬 경우에도 가능한 한503을 반환하며, 읽기를 위한 추가 대기는 짧게 제한합니다. 악성/느린 클라이언트에는 응답을 끝까지 전달하지 못할 수 있습니다.

검증 실패와 모델 자원 오류를 구분합니다. AI-02는 모델 메타데이터·판정선·가중치 사용 가능 여부를, AI-04는 시간대 자원을 검사합니다. 등록된 계산 호출 전에는 resource_manifest.json의 모델 코드·산출물·설정·시간대·smoke 입력 SHA-256을 대조합니다. 변경/누락 시503으로 차단합니다. 이 파일은 서명이 아닌 배포 무결성 기준이며, 신뢰한 변경만 오프라인에서 `python -B -S -X utf8 -m ai_server.resources --write-manifest`로 갱신합니다.

## 4. 호출별 데이터 차이

| 항목 | AI-02 | AI-03 | AI-04 | AI-05 |
| --- | --- | --- | --- | --- |
| 필수 문맥 | now,targetDate | now,timezone,mode,exam | now,asOfDate | now |
| 수면 입력 | sleepHistory, 최대35일 사용 | sleepHistory, 최대28일·단계로 주기 추정 | sleepRecords, 기상일당1건 | 없음 |
| 일정 입력 | timetable + events | fixedEvents 실제 구간 | events 실제 구간·이동 가능성 | events 텍스트·선택 시각 |
| 개인 설정 | 필요 수면·입면·통학·준비·버퍼·하한 | 입면·시험 버퍼,기상 선호용 usualBedtime | 성인 확인·목표·준비/통학·입면·선호 시각 | 과목 목록·사용자 분류 규칙 |
| 학습 상태 | 없음 | 총량1~100·완료율1~100,숙련도 선택/미사용 | 없음 | 없음 |
| 시간대 | KST 고정, 차이는UTC | KST/UTC 기본, 다른IANA는OS DB | IANA Asia/Seoul 전용, 번들TZif | 오프셋 시각 차이 |
| 주요 제한 | 사용자 하한240~480, 기본300 | 시험31일 이내·다음 수면1회·개인2주기 하한 | 기록90건·일정100건, window1~60/plan1~7일 | 일정100건·규칙500건·과목200개 |

추가 입력 한도: AI-02의 sleepHistory/recentSleep/timetable/events/eveningEvents는 각각500개, AI-03의 sleepHistory는200개·fixedEvents는500개, 두 호출 모두 세션별 stages는500개이며 AI-03 전체stages는5000개입니다. 초과 시400이며 잘라 계산하지 않습니다. 공통1MiB 한도도 함께 적용됩니다. 실제 목표 부하는 운영 머신에서 측정해야 합니다.

### 의미상의 주의점

- 수면량과 침대 체류를 구분합니다. AI-02/03은 수동·탭 기록의 입면시간 차감을 수행합니다. AI-04는 이미 의미가 정리된 일별 실제 수면/각성 분량을 받습니다. 같은 DTO를 그대로 네 호출에 공통 전달하면 안 됩니다.
- AI-04의 입력 시각은 원본 제약대로 **초·마이크로초0**이어야 합니다. 백엔드 기록에 초가 있으면 분 정밀도 변환 정책을 합의해야 하며 AI가 조용히 반올림하지 않습니다.
- AI-02는 오류·겹침 기록을 제외하고 규칙 폴백할 수 있지만 AI-03/04는 해당 입력을400으로 거절할 수 있습니다.
- AI-03의 D1/D3/D7은 수식의D=1/3/7이며 시험까지 실제시간과 구별합니다. 다음 주수면 한 번만 반환합니다. 여러 시험의 독립 추천을 충돌 확인 없이 합치지 않습니다.
- AI-04 부족량은 초과 수면 상쇄형 부채 잔액이 아닙니다. AI-05 숙면 중요도는 위험 확률·severity가 아닙니다.
- 누락·조회 실패를 빈 배열로 숨기지 않습니다. 실제 데이터 없음만 []를 씁니다.

## 5. 결과 상태와 저장

- AI-02: plan이 없으면422. 내부 RULE/OPTIMIZED는 앱 PlanMode NORMAL/FREE와 다릅니다. HTTP 응답에는 내부 진단을 제외합니다. HTTP 전처리는 시험 가산을 비활성화하지만 원시 Sleep_plan CLI에는 기존 기능이 남아 있습니다.
- AI-03: OK/NO_FUTURE_NIGHT_REQUIRED는200. 진행 중이면422 SLEEP_IN_PROGRESS,선택 수면량 배치 불가는422 SLEEP_WINDOW_UNAVAILABLE입니다. 단계 부족은90분 가정이며 오류가 아닙니다. riskLevel=UNKNOWN이고confidence는주기추정품질입니다.
- AI-04: valid 집계는200이고 계획별 target_met/target_shortfall/requires_rescheduling/no_feasible_opportunity를 본문에서 구분합니다. 결측이면 추가 수면 목표null, baseline_only입니다. 일정이 실제 변경되었다는 의미가 아닙니다.
- AI-05: 미분류·규칙 충돌·과목 선택 필요도200입니다. needsUserInput/needsCourseSelection/preferenceConflict를 사용합니다.

AI는 planId·slotId 발급이나 DB ACTIVE/SUPERSEDED 전이를 하지 않습니다. Spring이 결과의 시각 순서·수면량·일정 충돌을 재검증하고 저장/미리보기 정책을 적용합니다. 계산 호출은 저장 부작용이 없으므로 동일 입력 재시도가 가능하지만 자동 재시도는 서버에 구현하지 않았습니다.

## 6. 실행과 운영 경계

Python3.11 이상. SLEEP에서 AI_INTERNAL_KEY를 환경에 주입한 뒤:

```powershell
python -B -S -X utf8 -m ai_server --host 127.0.0.1 --port 8000
```

동일 머신 외 Spring에서 접근하려면 적절한 바인딩 주소와 내부 네트워크 경로를 구성해야 합니다. 운영에서 0.0.0.0 바인딩만으로 공용 공개하지 않습니다. TLS·방화벽·인증 키 배포는 리버스 프록시/배포 환경에서 설정합니다.

HTTP 접수는 ThreadingHTTPServer를 사용하되, 연결 상한과 별도 프로세스 계산 제어를 추가했습니다. 기본 설정은 아래와 같으며 CLI 환경 변수로 조정합니다.

| 환경 변수 | 기본값 | 범위·의미 |
| --- | --- | --- |
| SLEEP_WORKERS | 4 | 동시 계산1~32, 넘치면 대기열 없이503 |
| SLEEP_CONNECTIONS | 32 | 접수 연결1~256 |
| SLEEP_TIMEOUT_SECONDS | 2.5 | .05~300초, AI-02~05의 spawn 시작부터 계산 결과 대기까지 예산 |
| SLEEP_EXAM_TIMEOUT_SECONDS | 10 | .05~300초, AI-06 계산 예산 |
| SLEEP_RATE_PER_MINUTE | 120 | 공유 키 호출량1~1000000/분 |
| SLEEP_BURST | 20 | 토큰 버킷 초기·최대 버스트1~100000 |
| SLEEP_FAILURE_THRESHOLD | 3 | 모델별 연속 서버 오류 차단 기준1~100 |
| SLEEP_COOLDOWN_SECONDS | 60 | 차단 후 재탐색까지 .05~300초 |

기본값 기준 timeout 프로세스는 terminate→필요 시 kill→join으로 정리하고 슬롯을 반환합니다. 400/422는 모델 서버 장애로 세지 않습니다. 회로는 경로별이며 cooldown 뒤 하나의 시험 요청만 허용합니다. health/ready/metrics는 계산 호출량 제한에서 제외하지만 연결 상한은 적용됩니다. 재시도는 자동 실행하지 않습니다.

Spring 읽기 제한은 서버 계산 예산+프로세스 정리+네트워크 여유보다 길게 정해야 합니다(기본 서버2.5초에 예를 들어4초부터 환경에서 검증). Spring 연결 종료를 즉시 감지해 계산을 취소하는 프로토콜은 없지만, 설정된 서버 예산에서 자식 프로세스를 종료합니다. 본문/소켓5초 timeout과 추론 예산은 서로 다릅니다. OS 스케줄링·프로세스 시작/정리·응답 전송 때문에 HTTP 전체 시간의 정확한 SLA는 별도 측정이 필요합니다.

CLI는 시작 시 리소스 무결성과 예제 입력으로 여섯 계산 모델을 실제 실행합니다. 실패하면 종료 코드2로 시작을 중단합니다. GET /ready는 해당 시작 추론 결과와 현재 리소스 해시를 검사하며 ready=false면503입니다. 시작 검증 결과를 계속 노출하며 매 readiness 요청마다 추론하지는 않습니다. 이후 모든 임의 사용자 입력에 대한 추론 성공을 보증하는 검사는 아닙니다.

GET /metrics는 JSON으로 요청 수·서버 오류·현재 연결·연결 거절·지연시간 합계/최대·계산 접수/완료/실패/timeout/과부하/회로 차단을 반환합니다. CLI는 요청별 requestId/등록 경로/status/elapsedMs를 JSON 로그로 남깁니다. 원문·사용자 ID·내부 키는 로깅하지 않습니다. 기본 로깅을 끈 라이브러리 호출자는 별도로 logging을 설정해야 합니다.

TLS는 SLEEP_TLS_CERT/SLEEP_TLS_KEY(또는 CLI 옵션)로 설정하며 TLS1.2 이상입니다. 실제 도메인·인증서·사설CA 배포는 환경에서 제공해야 합니다. 리버스 프록시 TLS 종료 구성도 가능합니다. [배포 구성](../deploy/README.md)에 Docker 재시작/컨테이너 CPU·메모리·PID 제한, systemd 재시작/cgroup, Windows 재시작 스크립트를 제공합니다. 실제 서비스에 설치/배포한 것은 아니며, Windows 단독 실행의 OS 메모리 제한까지 자동 설정하지 않습니다.

모델 파일과 manifest는 읽기 전용으로 배포하고 변경 후 재시작합니다. 부모 HTTP 모듈은 시작에 라우트를 import하므로 AI-04 config 등 필수 Python/JSON이 손상되면 HTTP 시작 전 실패할 수 있습니다. 이는 정상 liveness로 위장하지 않고 시작 실패로 드러납니다.

실제 Spring 네트워크·배포/위임 인증·목표 동시 접속 부하·실제 사용자 효과 검증은 아직 수행하지 않았습니다. 코드와 배포 구성을 갖춘 상태와 실제 운영 환경에서 배포·검증된 상태를 구분합니다.

## 7. 계약·예제·검증 위치

[AI-01](AI-01_BACKEND_CONTRACT.md) · [AI-02](AI-02_BACKEND_CONTRACT.md) · [AI-03](AI-03_BACKEND_CONTRACT.md) · [AI-04](AI-04_BACKEND_CONTRACT.md) · [AI-05](AI-05_BACKEND_CONTRACT.md)

실행 가능한 요청과 실제 응답은 docs/examples/ai02~ai05-*.json입니다. 반환 정책은 각 호출 계약에서 확인합니다.

```powershell
python -B -m ai_server.resources
python -B -X utf8 tools/check_backend_contract.py http://127.0.0.1:8000/ai/v1
```

검증 내용: 기존24개 계획 전체 비교, Fatigue 산출물 해시, 팀원 모델 함수/원본 소스 해시, 수면 코치9개 및 라벨러223개 출력 보존, AI-03 수식120000격자·10000임의입력·주기/배치 시나리오, API 예제의 실제 HTTP 일치, 입력 경계253건,16개 혼합 동시 요청의 결과/추적 ID 격리, 메서드·JSON·타임아웃·자원 오류 분류. 이 수치는 서로 다른 검증 집합이며 사용자 대상 정확도와 같지 않습니다.

AI-03의 요청·응답과 백엔드 저장/단계 조회는 [백엔드 안내](AI-03_BACKEND_CONTRACT.md), 수식·주기·배치는 [AI-03 명세](../ai_server/ai_03/README.md)를 따릅니다. 기존 검토 결과는 [검토 보고서](../dummy/docs/SERVER_REVIEW.md), 원본 인계와 평가 자료는 [보관 문서](../dummy/docs/upstream)에 있습니다.




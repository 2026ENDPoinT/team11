# 통합 AI 서버 연결

Spring 백엔드와 Python AI 서버를 별도로 실행합니다. Spring의 `AI_BASE_URL`에는
`http://127.0.0.1:8000` 같은 기본 주소를 지정합니다. 컨테이너 배포에서는 백엔드에서
접근 가능한 AI 서비스 주소를 사용합니다. 두 서비스에 같은 `AI_INTERNAL_KEY`를 설정합니다.
AI-01은 Spring에서 내부 키 없이 호출하고, AI-02~06은 Spring이 `X-Internal-Key`를 추가합니다.

`AI_TIMEOUT_SECONDS`는 연결 및 일반 응답 제한이며 기본 4초입니다.
`AI_LONG_TIMEOUT_SECONDS`는 AI-03·06 응답 제한이며 기본 15초입니다.

## 백엔드 API

모든 경로에 백엔드 로그인으로 받은 액세스 토큰을 `Authorization: Bearer <token>`으로
전달합니다. POST에는 `Content-Type: application/json`과 AI 계약의 JSON 객체를 보냅니다.
AI-02·03·04·06은 건강 데이터 제공 동의가 필요합니다.

| 기능 | 메서드와 Spring 경로 | AI 저장소의 요청 예제 |
|---|---|---|
| AI-01 상태 | GET `/api/v1/ai/health` | 본문 없음 |
| AI-02 오늘 밤 플랜 | POST `/api/v1/ai/plan/tonight` | `docs/examples/ai02-request.json` |
| AI-03 시험 수면 계획 | POST `/api/v1/ai/plan/exam-sleep` | `docs/examples/ai03-spring-snapshot-request.json` |
| AI-04 수면 회복 | POST `/api/v1/ai/sleep-debt/calculate` | `docs/examples/ai04-request.json` |
| AI-05 일정 분류 | POST `/api/v1/ai/insights/risks` | `docs/examples/ai05-request.json` |
| AI-06 집중 모드 | POST `/api/v1/ai/plan/focus-sleep` | `docs/examples/ai06-focus-request.json` |

계산 응답의 `data.result`와 `data.modelVersion`은 AI 응답을 그대로 전달합니다.
AI-01은 `data.status`가 `ok`이면 연결된 상태입니다. AI가 실행 가능한 시험 계획을
찾지 못해 HTTP 200 안에 계획 없음 상태를 반환하면 해당 상태도 그대로 전달합니다.
Spring은 입력 오류를 400, AI의 422를 422, 요청 한도 초과를 429,
연결 실패나 AI 서비스 오류를 503으로 응답합니다.

AI-03~06 호출자는 AI 서버 계약에 맞는 스냅샷을 구성합니다. 이 경로들은 결과를 계산해
돌려주며 DB에 플랜을 저장하지 않습니다. 기존 시험 기간, 수면 통계 및 일정 위험 API는
이 경로와 별도로 동작합니다.

## 배포 확인

AI 저장소 루트에서 백엔드 주소와 테스트 계정 토큰을 설정한 뒤 예제 요청을 보낼 수 있습니다.

```powershell
$headers = @{ Authorization = "Bearer $accessToken" }
Invoke-RestMethod "$backendUrl/api/v1/ai/health" -Headers $headers
Invoke-RestMethod "$backendUrl/api/v1/ai/plan/exam-sleep" -Headers $headers `
  -Method Post -ContentType 'application/json' -InFile 'docs/examples/ai03-spring-snapshot-request.json'
```

기존 앱의 `GET /api/v1/plans/tonight`도 AI-02를 호출합니다. 활성 플랜이 없는
테스트 계정으로 요청해 `data.generatedBy`가 `AI`이고 `data.modelVersion`에 모델
버전이 있는지 확인합니다. 연결 실패 시에는 기존 규칙 기반 플랜이 반환됩니다.

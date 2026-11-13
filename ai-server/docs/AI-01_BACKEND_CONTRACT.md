# AI-01 헬스체크

`GET /ai/v1/health` — 요청 본문·인증 헤더 없이 호출합니다. 서버가 응답하면 HTTP200과 아래 JSON을 반환합니다. 일반 모델 호출의 result 봉투는 사용하지 않습니다.

```json
{
  "status": "ok",
  "modelVersion": "ai02-normal-v1",
  "models": {
    "AI-02": "ai02-normal-v1",
    "AI-03": "exam-sleep-workload-2.0.0",
    "AI-06": "focus-sleep-pvt-1.0.0"
  }
}
```

`models`는 호출별 버전입니다. 최상위 modelVersion은 기존 헬스체크와의 호환을 위해 AI-02 버전을 유지합니다. 모델 버전 갱신 시 실제 응답 값도 바뀝니다.

X-Request-Id를 전달하면 응답 헤더에 그대로 돌려주고, 생략하면 생성합니다. 공통 요청 ID 검증과 Cache-Control: no-store가 적용됩니다. 헬스체크는 서버 응답 여부를 확인하는 가벼운 liveness 용도이며, 모델 추론·DB 조회·외부 통신이나 전체 모델의 정상 계산 검증을 실행하지 않습니다.

서버 실행 자체에는 기존처럼 AI_INTERNAL_KEY 설정이 필요하지만, 이 GET 호출에는 키를 보내지 않아도 됩니다.

## 공통 서버 규칙

인증·JSON 중복 키/비유한 수/깊이 제한·메서드405·본문 수신408·실행/배포 제약은 [통합 서버 명세](INTEGRATED_SERVER_SPEC.md)를 함께 적용합니다. 호출별 필수값·계산 의미는 이 문서를 따릅니다.

운영 점검은 내부 인증된 GET /ai/v1/ready(시작 추론·현재 리소스 해시)와 GET /ai/v1/metrics(집계)를 별도로 사용합니다. health 자체는 가볍게 유지됩니다.

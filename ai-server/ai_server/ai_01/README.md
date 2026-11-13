# AI-01 헬스체크

GET /ai/v1/health를 인증·본문 없이 호출합니다. service.health()가 status=ok, 기존 호환용 modelVersion(AI-02 버전), 호출별 models 버전을 반환합니다. 이 호출에는 일반 계산 API의 result 봉투를 사용하지 않습니다.

현재 서버가 요청에 응답하는지 확인하는 liveness입니다. 모델 추론·DB·외부 API·전체 산출물 무결성을 검사하는 readiness는 아닙니다. 서버 시작 시에는 다른 계산 API를 위해 AI_INTERNAL_KEY 설정이 여전히 필요합니다. 잘못된 메서드는405, 잘못된 요청 ID는400입니다.

[백엔드 계약](../../docs/AI-01_BACKEND_CONTRACT.md) · [통합 서버 명세](../../docs/INTEGRATED_SERVER_SPEC.md)

인증된 GET /ai/v1/ready에서 시작 시 다섯 계산 모델(AI-02~06)의 실제 추론·현재 리소스 해시를, GET /ai/v1/metrics에서 운영 집계를 별도로 확인합니다. health의 models에는AI-06 버전도 포함되며 공통 응답 형식과 역할은 그대로입니다.

# AI-05 일정 분류와 숙면 중요도

Spring 호출은 `POST /ai/v1/insights/risks`이다. 일정별 텍스트, 과목 사전, 사용자 규칙, 중요도·집중력 요구를 받아 `analyses[]`를 입력 순서로 반환한다. [계산 흐름도](FLOW.md) · [백엔드 계약](../../docs/AI-05_BACKEND_CONTRACT.md) · [통합 백엔드 연동](../BACKEND_INTEGRATION.md)

## 설계와 기반 자료

팀의 schedule-labeler 1.2.0 규칙 구현을 사용한다. 키워드·정규식과 문맥 예외, 과목·별칭 사전은 코드와 요청 데이터에 들어 있다. 이 구현에 적용된 논문 공식이나 학습용 데이터셋은 전달 문서와 코드에서 확인되지 않는다. 고정 약칭 표본과 저장소의 원본 결과 자료는 규칙 검사에 쓰인다. [팀원 인계서](../../dummy/docs/upstream/schedule-labeler-HANDOFF.md) · [약칭 검사 자료](../../dummy/docs/upstream/schedule-labeler-ABBREVIATION_AUDIT.md)

1. **텍스트와 과목.** 문자열을 검사하고 규칙에 따라 시험·과제·수업 등 활동을 분류한다. 정식 과목과 별칭을 대조해 과목 후보를 만들며, 중복 후보는 선택이 필요한 결과로 돌려준다. [규칙](rules.py) · [과목과 활동](academic.py)
2. **숙면 중요도.** `낮음 < 보통 < 높음` 순서에서 기본 분류와 명시된 중요도·집중력 요구 중 높은 값을 선택한다. 시작 상태와 마감 긴급도는 요청 `now`와 비교해 별도로 계산한다. [평가](assessment.py)
3. **사용자 규칙.** 정확 문구, 확장 규칙, 자동 분류 순으로 적용한다. 확장 규칙은 키워드 조합 또는 과목과 활동 조합이며, 서로 다른 결과를 주는 확장 규칙이 동시에 맞으면 선택이 필요한 상태를 반환한다. 저장과 사용자 범위 조회는 Spring이 담당한다. [개인 규칙](preferences.py)

공개 앱 응답의 위험 등급과 제안 문구는 Spring의 변환 정책에서 정한다. [공개 Swagger 연결](../../docs/AI-04_AI-05_SLEEPSYNC_HANDOFF.md)

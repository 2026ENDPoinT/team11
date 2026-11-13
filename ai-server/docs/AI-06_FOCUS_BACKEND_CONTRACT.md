# AI-06 집중 모드 수면 추천

Spring에서 `POST /ai/v1/plan/focus-sleep`을 호출합니다. 요청 계약은 `FOCUS_SLEEP_V1`, 응답 모델 버전은 `focus-sleep-pvt-1.0.0`입니다.

[모델 설계·논문·공식](../ai_server/ai_06/README.md) · [OpenAPI](AI-06_FOCUS_OPENAPI.json) · [요청 예제](examples/ai06-focus-request.json) · [실제 응답 예제](examples/ai06-focus-response.json)

## Spring 요청

`Content-Type: application/json`과 `X-Internal-Key`가 필요합니다. `X-Request-Id`는 선택이며 응답 헤더로 돌려받습니다.

| 필드 | 값과 단위 |
| --- | --- |
| `contractVersion`, `timezone` | 각각 `FOCUS_SLEEP_V1`, `Asia/Seoul` |
| `asOfAt` | Spring이 요청 생성 시 고정한 기준 시각. 초와 오프셋을 포함한 RFC 3339. 재시도 시 동일 값 사용 |
| `wakeDeadlineAt` | 준비·이동 등 앱의 제약을 이미 차감한 최종 침대 이탈 마감. 기준 시각 이후 36시간 이내 |
| `minInBedMinutes`, `maxInBedMinutes` | 1–960 정수 분, 최소 ≤ 최대. 잠복기와 각성을 포함한 침대 체류 기회 |
| `history` | 최근 90일 내 종료한 주수면 3–90건. 서울 시각으로 기상 날짜당 한 건 |

수면 기록에는 `wakeDate`(YYYY-MM-DD, 기상한 날짜), `bedtime`, `wakeTime`(HH:mm), `asleepMinutes`(실제 수면 추정 분)가 필요합니다. 자정을 넘겼으면 `wakeDate`는 다음 날입니다. `asleepMinutes`는 침대 체류시간 이하여야 합니다. 선택 필드는 `latencyMinutes` 또는 `sleepOnset` 중 하나와 `awakeningCount`입니다. 잠복기 0분을 관측한 경우에는 0을 보내고, 미상인 필드는 생략합니다. AI는 요청에 포함된 스냅샷으로 계산하며 Spring이나 외부 기상 마감 API를 조회하지 않습니다.

Spring에서 겹치는 웨어러블·수동 기록을 정리하고, 1일 1회 주수면과 실수면 추정값을 선택합니다. 사용자 식별자나 일정 제목을 보낼 필요는 없습니다.

## 응답과 화면 표시

HTTP 200은 공통 `{result, modelVersion}` 형식입니다. `result`에는 계약 버전과 원본 모델의 출력 필드를 포함합니다.

- `recommendation.bedtime`과 `wake_time`은 서울 오프셋을 포함한 시간표입니다. `sleep_opportunity_hours`는 침대 체류시간, `estimated_actual_sleep_hours`는 그 안에서 잠들어 있을 것으로 추정하는 시간입니다. 두 필드의 응답 단위는 시간입니다.
- `prediction_used_for_ranking=true`이면 지원 범위의 후보를 낮은 PVT 주의력 저하 예측값 순으로 비교했습니다. PVT 점수는 학습 자료의 지표 척도이며 실제 집중력 향상 확률이나 시험 점수로 표시하지 않습니다.
- `cycle_fallback.used=true`이면 지원 범위의 PVT 후보가 없어 예상 실수면과 명목 90분 주기의 정렬을 사용했습니다. 해당 경로에서 추천의 PVT 점수는 `null`입니다. 90분은 계획용 가정입니다.
- `recommendation_validated`와 `causal_improvement_validated`는 `false`입니다. `maximum_time_recommendation`은 가장 긴 침대 체류 기준안이며, `alternatives`와 각 시간 길이의 `duration_comparison`을 별도로 제공합니다.

| HTTP | 코드 |
| --- | --- |
| 400 | `INVALID_INPUT` · DTO 형식, 날짜 중복, 기록 범위, 필드 값 오류 |
| 401 | `UNAUTHORIZED` |
| 422 | `UNSUPPORTED_HORIZON` · 기준 시각 이후 36시간 초과 또는 지난 마감 |
| 422 | `NO_FEASIBLE_PLAN` · 15분 탐색 격자에서 요청 범위와 마감을 동시에 충족할 수 없음 |
| 503 | `MODEL_UNAVAILABLE`, `SERVER_BUSY`, `CIRCUIT_OPEN` |
| 504 | `MODEL_TIMEOUT` |

AI-06은 통합 서버의 공용 worker 슬롯과 `SLEEP_EXAM_TIMEOUT_SECONDS`(기본 10초)를 사용합니다. Spring read timeout은 기본 설정에서 12초 이상으로 두세요. `GET /ai/v1/health`의 `models.AI-06`과 내부 인증이 필요한 `GET /ai/v1/ready`의 `startupInference.models.ai06`에서 기동을 확인합니다.

## 모델 설명

팀원 ZIP의 `sleep-only-cognition-v2/tree3.joblib` 모델을 그대로 사용합니다. 수면 기록으로 개인의 실수면을 추정하고, 15분 단위로 만든 후보에 PVT 회귀 모델을 적용합니다. 참가자별 검증 MAE의 평균은 ZIP 산출물 기준 약 3.257이며, 학습 데이터의 사후 수면 관측과 PVT 지표를 평가한 수치입니다. 수면 시간표를 바꾸는 개입이 집중력을 높였는지는 이 수치로 판단할 수 없습니다. 원본 플래너의 명목 주기 대체 규칙과 결과 플래그도 유지합니다.

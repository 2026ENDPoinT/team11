# Fatigue 계산 모듈

Python 3.11 이상 표준 라이브러리 실행본입니다. models.json의 M1 회귀·보정 계수, M2/M3 확률표와 weights.json의 75%·10%·15% 비중은 변경하지 않았습니다.

| 파일 | 역할 |
| --- | --- |
| model.py | 공통 입력 검증·개별 추론 |
| integrate.py | 판정선 기준 0~100 환산·통합 |
| inputs.py | 기존 backend.py의 네트워크를 제거하고 보존한 이력 → 입력 변환 |
| personalization.py | 전달된 수면·컨디션 이력으로 가중치 계산. AI-02에서는 미사용 |

공통 입력은 asleep/in_bed/bed_at/mean_3d/mean_7d/days_7d, 관측 3일 이상이면 std_7d/bedtime_var_7d, 평소 입력 habit_minutes/habit_bedtime/quality(0~4)입니다. allow_missing_habits=True면 부족한 평소 모델을 제외해 재가중하고 M1 입력이 없으면 점수는 null입니다. 이 양식과 변환 수식은 기존과 같습니다.

Sleep_plan은 입면시간을 반영한 수면량을 inputs.build_model_input에 전달합니다. 최근 7일에 앞선 28일에서 평소 습관을 추정합니다. 통합 지표는 연구·시연용이며 실제 사용자 체감 피로 정확도를 의미하지 않습니다.

inputs.py는 기상일별 가장 긴 주 수면 하나를 선택합니다. 평소 수면량·취침 시간대에는 최소7개 관측 밤, 평소 수면의 질에는 최소3개 평점이 필요합니다. API의 quality=1~5는 여기서0~4로 변환합니다. 평소 수면301~359분은 M3 원본 설문 범주가 없어 M3를 제외합니다. 확률은 모델별 판정선을50점으로 대응시키는 구간 선형 환산 후 가중 평균하며, 점수 자체가 고피로 확률은 아닙니다.

개인화는 최소20개 유효 피드백·과거 학습/최근 검증 분리·개선 확인을 수행하는 별도 순수 함수입니다. 코드가 존재하지만 현재 HTTP 호출에서는 컨디션 기록을 받거나 개인화를 실행하지 않습니다. models.json은 최초 사용 시 캐시하며 교체하면 서버 재시작이 필요합니다. weights.json의 기본 가중치는 호출 시 읽습니다. 로컬 CLI는 별도 진입점일 뿐 독립적인 외부 Fatigue HTTP API가 등록된 것은 아닙니다.

SLEEP에서 실행:

```powershell
python -B -X utf8 -m ai_server.ai_02.Fatigue.integrate dummy/ai_server/ai_02/Fatigue/example.json
```

예제는 27.45점입니다. 데모·내보내기 도구·개인화 연구 모듈은 [보관 폴더](../../../dummy/ai_server/ai_02/Fatigue)에 있습니다.

export_models.py는 별도 원본 학습 환경용 도구입니다. 현재 저장소에 없는 SLEEP/Fatigue_model과 joblib 학습 환경을 준비한 경우에만 사용합니다. 서버 추론에는 필요하지 않으며 이번 구조 변경에서 실행하지 않았습니다.

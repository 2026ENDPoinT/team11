# AI-05 위험 일정 분석 — 일정 라벨러 연결 계약

[SleepSync 공개 Swagger와 Spring 연동](AI-04_AI-05_SLEEPSYNC_HANDOFF.md)

**POST /ai/v1/insights/risks**. 위험 일정 분석의 호출 번호를 AI-05로 통일했습니다. 호출 URL·요청·응답 JSON·분류 로직·모델 버전은 변경하지 않았습니다. 과거 백엔드 문서의 AI-05 크로노타입 추정 대신 이 계약을 적용합니다. `/ai/v1/chronotype/estimate`는 이 서버에서 제공하지 않습니다.

실제 전달받은 schedule-labeler 1.2.0의 입출력을 사용합니다. 공통 인증 X-Internal-Key, 추적 X-Request-Id, 1MiB 본문 제한, `{result,modelVersion}` / `{error}` 규격을 사용합니다. 모델 버전은 schedule-labeler-1.2.0입니다.

## 결과의 의미와 기존 문서 대비 변경

팀원의 모델은 텍스트의 일정 종류·숙면 중요도·과목·활동을 규칙으로 분류합니다. 개인별 사고 위험이나 피로 확률을 예측하지 않습니다. **sleepPriority(높음/보통/낮음)는 과거 위험 일정 규격의 severity(HIGH/MEDIUM/LOW)와 동일한 의미가 아닙니다.** 마감이 임박했다는 이유만으로 숙면 중요도를 높이지 않습니다.

따라서 예전 문서의 upcoming + sleepDebtMin → risks[]/severity/recommendedBedAt 형태를 그대로 반환하지 않습니다. Spring은 아래 events 요청과 analyses 응답으로 수정해야 합니다. 술자리 종료 후 권장 취침시간 산출이나 수면 기록을 이용한 위험도 계산을 임의로 추가하지 않았습니다. 백엔드가 위험 알림 정책을 별도로 만들려면 이 분류 결과와 수면·일정을 조합하는 별도 규칙이 필요합니다.

## Spring → AI 요청

| 필드 | 필수·기본 | 의미·백엔드 출처 |
| --- | --- | --- |
| now | 필수 | 평가 시각, 오프셋 포함 ISO 8601. 모든 일정의 reference_at으로 사용 |
| events | 필수 배열 | 분석할 일정, 최대100건. 없으면 [] |
| courses | 선택 | 해당 사용자의 정식 과목명·별칭 목록. 시간표 또는 별도 과목 설정에서 구성 |
| userRules | 선택, [] | 해당 사용자만의 저장 분류 규칙. Spring이 DB에서 조회 |

events[]:

- eventId: 필수, 중복 없는 숫자 또는 문자열.
- text: 필수 문자열1~2000자. 일정 제목 또는 백엔드가 결합한 제목·설명 **원문**. AI 전처리가 날짜·문구를 삭제하거나 재작성하지 않습니다.
- importance / focus: 선택, 높음·보통·낮음 또는 null. 사용자 중요도·집중력 요구.
- startAt / deadlineAt: 선택 오프셋 포함 시각. 시작 상태와 마감 긴급도 계산.
- courseName: 선택, 과목 후보 중 사용자가 확정한 **정식 이름**.

courses 예: `[{"name":"자료구조","aliases":["자구","DS"]}]`. 생략/null이면 원본 기본13과목, []이면 기본 사전도 사용하지 않습니다. 최대200과목, 과목당 별칭20개이며 정식 과목명 중복은 오류입니다. 서로 다른 과목의 별칭 중복은 허용하고 선택 필요 상태를 반환합니다.

userRules는 최대500건입니다:

```json
[
  {"text":"동아리 정모","label":"중요 모임","sleepPriority":"높음"},
  {"text":"동아리 정모","label":"모임","sleepPriority":"보통","scope":"keywords","keywords":["동아리","정모"]},
  {"text":"자구 과제","label":"과제·제출","sleepPriority":"높음","scope":"course_activity","courseName":"자료구조","activity":"assignment"}
]
```

scope 생략은 exact입니다. keywords는1~5개 리터럴 표현, activity는 exam/assignment/study/meeting/class/presentation/exam_admin입니다. 정확 문구 > 확장 규칙 > 기본 분류 순서와 충돌 처리 모두 원본 그대로입니다.

사용자 규칙·과목·중요도·집중력 저장은 모델이 하지 않습니다. 필요하면 Spring에서 사용자 소유 설정 테이블과 CRUD를 추가하고 userId+원본 rule_key 기준으로 저장해야 합니다. 키워드·과목활동의 기존 scope/키 생성 방식은 [팀원 인계 문서](../dummy/docs/upstream/schedule-labeler-HANDOFF.md)를 참조하세요. 앱이 이번만 수정한 결과를 영구 규칙으로 오인해 저장하지 마세요.

전체 예제: [요청](examples/ai05-request.json) · [실제 응답](examples/ai05-response.json).

## AI → Spring 응답

```json
{
  "result": {
    "analyses": [{
      "eventId":101,
      "label":"과제·제출",
      "sleepPriority":"높음",
      "course":"자료구조",
      "activity":"assignment",
      "needsUserInput":false,
      "reason":"분류 근거 문자열",
      "assessment":{"urgency":"24시간 이내","prioritySource":"explicit_signal"}
    }],
    "warnings":["RULE_BASED_SLEEP_PRIORITY_NOT_RISK_PROBABILITY","KNOWN_ABBREVIATION_FALSE_POSITIVES"]
  },
  "modelVersion":"schedule-labeler-1.2.0"
}
```

위는 필드 요약이며 실제 응답은 원본의 모든 분류 근거를 camelCase로 보존합니다:

- source, ruleVersion, matchedRule/matchedText, matches/excluded
- courseCandidates/courseMatches, needsCourseSelection
- assessment: basePriority/finalPriority, importance/focus, prioritySource, factors, missingInputs, urgency/startStatus, referenceAt
- 사용자 규칙 적용 시 matchedScope/matchedUserRule, automaticResult
- 규칙 충돌 시 preferenceConflict/candidateUserRules, needsUserInput=true

미분류는 기타·sleepPriority=null이며 임의 중요도를 생성하지 않습니다. 동일 입력은 now가 같으면 동일 결과입니다. 모델에는 현재 시각 조회 폴백이 남아 있지만 통합 서버가 reference_at=now를 항상 넣어 실행 경로에서 사용하지 않습니다.

잘못된 필드·규칙·과목 목록·시각은 400 INVALID_INPUT입니다. 미분류나 사용자 선택 필요는 유효한 분석 결과이므로 HTTP200입니다. 저장된 규칙은 다른 사용자의 분류에 전파되지 않습니다. 권한 확인·HEALTH_DATA 등 필요한 동의와 개인화 자료의 사용자 범위는 Spring이 책임집니다.

약칭의 오인식은 원본에서 알려진 한계로, 이번 통합에서도 수정하지 않았습니다. [고정 스트레스 표본 측정](../dummy/docs/upstream/schedule-labeler-ABBREVIATION_AUDIT.md)은 실제 사용자 정확도가 아닙니다. 외부 LLM, 백엔드 조회, 저장은 사용하지 않습니다.

## 공통 서버 규칙

인증·JSON 중복 키/비유한 수/깊이 제한·메서드405·본문 수신408·실행/배포 제약은 [통합 서버 명세](INTEGRATED_SERVER_SPEC.md)를 함께 적용합니다. 호출별 필수값·계산 의미는 이 문서를 따릅니다.

계산은 공통 프로세스 예산·호출량·동시 처리·서킷 제어를 적용합니다. 기존 도메인 상태 외에504 MODEL_TIMEOUT,429 RATE_LIMITED,503 SERVER_BUSY/CIRCUIT_OPEN을 처리해야 합니다. 정상 응답 JSON은 그대로입니다.

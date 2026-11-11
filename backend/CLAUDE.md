# CLAUDE.md — SleepSync 백엔드

수업 연동 수면 코치 앱 "SleepSync"(Team 11, ENDPoinT)의 Spring Boot 백엔드입니다.
이 파일은 Claude Code가 작업 시작 전에 읽는 프로젝트 규칙입니다. 모르는 것은 추측하지 말고 "확인 필요" 항목에 적은 뒤 사용자에게 물어보세요.

## 1. 프로젝트 개요

- 시간표·일정(수업, 시험)을 바탕으로 사용자별 취침/기상 시각을 추천하고 수면 기록을 관리하는 모바일 앱.
- 구성: React Native 앱 ↔ **이 백엔드(Spring Boot)** ↔ 별도 Python AI 서버(무상태). DB는 PostgreSQL.
- 백엔드 개발자는 1명입니다. 해커톤 일정이므로 **동작하는 최소 구현을 우선**하고, 과한 추상화는 피합니다.
- 소스 오브 트루스:
  - API 명세: Notion "API DOCS SUBPAGE" (하위 페이지: Auth, Users, Devices, Timetable, Events, Exam, Plans, Logs, Stats, Streak/Home, Tips/Sync/Etc, 내부 AI 서버 API)
  - DB: `sleepsync_erd.sql` (저장소 루트, Part 1 = 테이블, Part 2 = 인덱스). 적용된 Flyway 마이그레이션 상태를 그대로 반영한 스냅샷이며, 마이그레이션을 추가/수정할 때 같이 갱신합니다. ERD Cloud(https://www.erdcloud.com/d/fe66R32xsRDguAGrQ)는 이 파일보다 뒤처져 있어 현재는 2차 참고용이고, 이 파일 기준으로 다시 그릴 예정입니다.
  - 명세와 코드가 다르면 **코드를 임의로 바꾸지 말고** 어느 쪽이 맞는지 사용자에게 먼저 물어보세요.

## 2. 기술 스택 (확인 필요 항목 포함)

- Java / Spring Boot, Spring Security + JWT, Spring Data JPA, PostgreSQL 16, Flyway
- 확인 필요: Java 버전(권장 21), 빌드 도구(Gradle 권장), 기본 패키지명, 테스트 DB 방식(Testcontainers 권장)
- 외부 연동: 푸시 알림(FCM 등, 확인 필요), 지도 API(통학 시간용, 네이버/카카오 미확정), 학교 eclass/LMS(학교 회신 대기)

## 3. 작업 원칙

1. 구현 전에 해당 기능의 Notion 명세와 ERD 테이블을 먼저 확인합니다.
2. 한 번에 하나의 기능(예: Auth → Users → Timetable ...)을 끝까지 만들고 테스트까지 통과시킨 뒤 다음으로 갑니다.
3. DB 스키마 변경은 반드시 Flyway 마이그레이션 파일로 합니다. 기존 마이그레이션은 수정하지 않습니다. ERD/명세 변경이 필요하면 코드 작업 후 사용자에게 알려 문서도 같이 고치게 합니다.
4. 비밀값(JWT 시크릿, 내부 AI 키, DB 비밀번호, 외부 API 키)은 환경변수로만 받고 커밋하지 않습니다. `.env`는 `.gitignore`.
5. 커밋은 작은 단위로, 메시지는 한국어 또는 영어 중 팀 관례를 따릅니다(확인 필요). 사용자가 요청하기 전에는 push/PR을 만들지 않습니다.

## 4. API 규약

- 베이스 경로 `/api/v1`, 인증은 `Authorization: Bearer <JWT>` (Access/Refresh 구조는 Auth 명세를 따름).
- 모든 응답은 envelope: 성공 `{ "data": ..., "error": null }`, 실패 `{ "data": null, "error": { "code": "...", "message": "..." } }`.
- 시각은 **ISO 8601 + 오프셋 (`+09:00`)**, 저장은 `timestamptz`. 서버 기본 타임존은 Asia/Seoul로 두되 DB에는 UTC 기준으로 저장해도 변환 규칙을 한곳에서만 다룹니다.
- 에러 코드는 명세의 코드표를 그대로 사용하고 새 코드를 만들면 명세에 추가할 목록으로 기록합니다.
- 컨트롤러는 얇게: 검증 → 서비스 호출 → 응답 변환. 비즈니스 규칙은 서비스에 둡니다. 엔티티를 응답으로 직접 반환하지 않고 DTO를 씁니다.
- 로그인 실패 누적/잠금(`failed_login_count`, `locked_until`)은 Auth 명세 규칙을 따릅니다.

## 5. 도메인 규칙 (명세에서 확정된 것)

- **필요 수면 시간**: 사용자 설정값. 기본 465분(7시간 45분). 사용자별 최소 수면 `min_sleep_min`은 **240~480분** 범위로 검증(DB check 제약 `ck_users_*` 있음).
- **스트릭**: 목표 달성 시 ±30분/±60분 구간 규칙은 Streak/Home 명세(HOME-01)를 따릅니다. 계산 위치는 서버이며 홈 응답에 `streak`와 `character`를 포함합니다.
- **캐릭터**: `characters` 마스터 테이블 + `users.character_id`(ON DELETE SET NULL). 목록 `GET /characters`(USER-13), 선택 `PUT /users/me/character`(USER-14). 시드 데이터는 Flyway로 넣으며 개수/이미지 전달 방식/해금 조건/기본값은 **확인 필요**.
- **예약 알림**: `scheduled_notifications` (플랜 슬롯 FK, 중복 방지 `uq_sched_pending`). 발송 대상 조회는 `ix_sched_due` 인덱스를 사용합니다.
- 시험 기간 모드, 휴식 계획(slots/미리보기→저장 흐름)은 Exam/Plans 명세를 따릅니다. 현재 와이어프레임과 명세 사이에 일부 불일치가 있어(온보딩 직접 입력 vs MCTQ 설문 USER-03, 동의 항목 등) 구현 전 사용자에게 확인합니다.

## 6. 내부 AI 서버 연동 (Spring → AI)

- 명세: 페이지 "내부 AI 서버 API" (2026-11-11, AI팀 "AI 모델 1차 완료" 전달 기준으로 전면 개정됨 — 과거 버전과 호출 번호·경로가 다르니 캐시된 내용 믿지 말 것).
- 실제 제공되는 호출은 **AI-01~05 다섯 개뿐**: 헬스체크 / `plan/tonight` / `plan/exam-sleep`(시험 모드, 경로 변경) / `sleep-debt/calculate`(구 AI-04=준비시간 추정 아님) / `insights/risks`(구 AI-05=크로노타입 아님). 그 외(각성도 예측, 팁 추천, 자연어 해석, 웨어러블 분석, 크로노타입 추정, 준비시간 추정)는 **AI 미지원** — Spring 자체 규칙/고정값으로만 구현.
- 헤더 `X-Internal-Key`, 타임아웃 **2.5초 고정**(시험 모드도 동일, 더 이상 10초 분리 없음). Spring 읽기 타임아웃은 이보다 길게(예: 4초~) 설정.
- AI 서버는 **무상태**이고 익명 `userKey`만 받습니다(현재는 어떤 모델도 사용하지 않음). 이름·이메일 등 개인 식별 정보는 절대 보내지 않습니다.
- AI-02/03 요청은 요약 데이터가 아니라 **원시 스냅샷 전체**(최근 35일 수면기록·전체 시간표·전체 일정 등)를 넘겨야 합니다. 조회 결과 없음과 조회 실패를 빈 배열로 섞지 않습니다.
- 실패/타임아웃 시 **규칙 기반 폴백**으로 계획을 생성하고 `generatedBy=RULE`로 표시합니다(AI 성공 시의 값은 명세 참고). 폴백 때문에 API 자체가 실패하면 안 됩니다.
- 응답의 모르는 필드는 무시합니다(`FAIL_ON_UNKNOWN_PROPERTIES=false`). AI 쪽 계약은 아직 변경될 수 있으므로 호출부를 한 클래스(예: `AiClient`)에 모으고 DTO 매핑을 분리합니다.
- **확인 필요(확정 전 구현 금지)**: ① 수면부채(STAT-01) 정의 충돌 — AI-04의 `shortfallMinutes`(상쇄 없는 단순 부족분 합계)와 기존 STAT-01의 초과수면 상쇄형 누적 부채가 다른 값인데 어느 쪽을 쓸지 미정. ② USER-03 온보딩 크로노타입 추정이 AI 보정을 전제로 설계됐다면 AI 미지원으로 재검토 필요. ③ 시험기간 플랜(EXAM-07, 다과목/전체기간)과 새 AI-03(시험 1건당 단일 주수면 추천, 신규 EXAM-08 제안)의 관계 정리.

## 7. 코드 구조 (확정 — Auth 작업 때 사용자 확인)

```
src/main/java/<base>/
  auth/ users/ devices/ timetable/ events/ exam/ plans/ logs/ stats/ home/ tips/ sync/
    controller/  service/  repository/  domain/  dto/
  common/   (응답 envelope, 예외 처리, 에러 코드, 시간 유틸)
  config/   (Security, JWT, Jackson, 스케줄러)
  ai/       (AiClient, 폴백 플래너)
src/main/resources/db/migration/   V1__init_auth.sql ...
```

- `User`/`Character`/`Consent`(+`ConsentType`, `Chronotype`) 엔티티와 리포지토리는 **users** 패키지가 소유합니다. Auth는 가입/로그인/토큰 발급 시점에 이를 참조만 하고, `RefreshToken`(세션 토큰)만 auth 소유입니다.

- 기능 패키지(도메인별) 구조를 사용합니다. 계층별 최상위 패키지로 나누지 않습니다.

## 8. 테스트

- 서비스 로직(스트릭 계산, 플랜 생성/폴백, 잠금 정책)은 단위 테스트 필수.
- 컨트롤러는 인증·검증·에러 envelope 위주로 슬라이스 테스트, 핵심 흐름(가입→로그인→시간표 등록→플랜 조회)은 통합 테스트 1개 이상.
- DB 의존 테스트는 실제 PostgreSQL(Testcontainers)을 사용합니다. H2로 대체하지 않습니다(DDL이 PG 전용 기능을 사용).
- 변경 후에는 빌드와 테스트를 실행해 통과를 확인하고, 실패를 숨기기 위해 테스트를 삭제/완화하지 않습니다.

## 9. 보안·개인정보

- 비밀번호는 BCrypt(또는 Argon2) 해시. 로그에 비밀번호, 토큰, 이메일 전체를 남기지 않습니다.
- 수면·건강 데이터는 민감 정보입니다. 동의(consent) 항목을 확인한 사용자 데이터만 처리하고, 타 사용자 데이터 접근은 항상 소유자 검증을 합니다(`userId`는 토큰에서만 얻고 요청 본문을 신뢰하지 않음).
- eclass/LMS 가져오기는 학교 회신 전까지 구현하지 않습니다. 사용자 계정 정보를 서버에 저장하는 방식은 금지(방식 확정 후 결정).

## 10. 열려 있는 결정 (구현 전에 사용자에게 확인)

- ~~지도 API~~ → 확정됨: **카카오** 사용(대중교통/도보 경로 지원). 네이버는 사용하지 않음. 통학 시간은 백엔드가 계산해 AI에 전달.
- 프로필에 주소/좌표·통학수단 필드 추가 (카카오 확정에 따라 진행 — Users/Timetable 도메인 작업 시 설계).
- 스트릭·캐릭터·팁 기능의 최종 범위, 팁 출처/검수.
- AI 서버 호스팅 위치(타임아웃은 2.5초로 확정, §6 참고).
- ~~명세를 탭 구조로 재편할지~~ → 확정됨: Figma v2 "unslop" 와이어프레임이 0 인증·온보딩 / 1 홈 / 2 기록 / 3 일정 / 4 리포트 / 5 설정 6탭 구조.
- ~~AUTH-01 단일 호출 유지 여부~~ → 확정됨: 회원가입 화면에서 동의(consents) 체크가 별도 온보딩 화면으로 분리됐지만, API는 지금처럼 프론트가 여러 화면 입력을 모았다가 **한 번에 `POST /auth/signup`으로 제출**하는 단일 호출을 유지. 중간에 이탈하면 계정 자체가 생성되지 않는 것이 의도된 동작.
- STAT-01 수면부채 정의 충돌: AI-04(`sleep-debt/calculate`)의 `shortfallMinutes`(상쇄 없는 단순 부족분 합계)와 기존 STAT-01 정의(초과수면 상쇄형 누적 부채)가 다름 — 어느 쪽을 쓸지, 아니면 필드를 분리할지 결정 필요.
- USER-03 온보딩 크로노타입 추정: 뒷받침하던 AI `/chronotype/estimate`가 이번 AI 서버에 없음 — Spring 규칙 임계값만으로 충분한지 재확인 필요.
- EXAM-07(다과목/전체기간 플랜)과 새 AI-03 기반의 EXAM-08 제안(시험 1건당 단일 주수면 추천)의 관계 정리.

## 11. 응답/보고 방식

- 작업이 끝나면 변경한 파일, 실행한 테스트와 결과, 남은 확인 사항을 짧게 요약합니다.
- 명세와 다른 구현을 했다면 이유와 함께 반드시 명시합니다.

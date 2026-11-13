# CLAUDE.md — SleepSync 프론트엔드 (React Native, iOS 우선)

수업 연동 수면 코치 앱의 RN 0.88 CLI 앱. Figma v2 와이어프레임(fileKey `K4WWLXA6gNOAI0qOzY4u0O`) 기준, 백엔드는
`https://team11-h10w.onrender.com/api/v1`(`src/api/client.ts` 상수 한 줄). 전체 API 레퍼런스는 백엔드 Swagger
(`/swagger-ui/index.html`)와 `../backend/CLAUDE.md` §4~5.

## 실행
```bash
npm install && cd ios && bundle exec pod install && cd ..   # 최초 1회
npm start                                                    # Metro
npm run ios -- --simulator "iPhone 14"                       # 빌드·실행
npm test && npx tsc --noEmit && npm run lint
```
첫 요청 전에 `curl $BASE_URL/app/config`로 서버를 깨워두면 빠르다(Render 콜드 스타트 ~100초).

## 구조
```
src/api/{client,endpoints,types}.ts   fetch 래퍼(envelope·Bearer·refresh)·도메인 함수·DTO
src/session.tsx                       토큰(AsyncStorage) + 로그인 사용자 Context — 전역 상태는 이것뿐
src/useApi.ts                         조회 훅(포커스마다 재조회). 캐시 없음
src/kst.ts                            +09:00 ISO 조립/파싱, 분↔"HH:mm", 날짜 산술
src/theme.ts  src/components/         Figma 컴포넌트 섹션(257:302) 1:1
src/screens/{auth,home,schedule,report,settings}/
src/navigation.tsx                    static API. 탭 4개 + 루트 스택
```

## 깨지기 쉬운 규칙 (게으르게 넘기지 말 것)
1. **refreshToken 회전 + single-flight** — `client.ts refresh()`. 동시 401이 refresh를 두 번 쏘면 서버가 전 세션을 끊는다. `__tests__/client.test.ts`가 지킴.
2. **시각은 `+09:00` 문자열 직접 조립** — `toISOString()` 금지(Z로 나감). `kst.ts`만 쓴다.
3. **취침/기상·리포트 집계는 서버 값 그대로** — 프론트에서 재계산하지 않는다.
4. **온보딩은 마지막 '완료'에서 단 한 번** `POST /users/me/onboarding`. 중간 저장 없음(의도).
5. 콜드 스타트: 스플래시 `/app/config` 타임아웃 120초, 나머지 20초(플랜 30초).
6. enum은 서버 값(`ASSIGNMENT·EXAM·PERSONAL·ETC`, `MON…SUN`, `quality 1|2|3`) — 한글 매핑은 화면에서만.
7. error.code 분기는 필요한 곳만(RATE_LIMITED·TIMETABLE_OVERLAP·SLEEP_LOG_DUPLICATE_DATE·SLEEP_HISTORY_INSUFFICIENT·
   VALIDATION_FAILED(EXAM 첫 호출)·NO_FEASIBLE_PLAN). 나머지는 `error.message`(한국어) 그대로 노출.

## 의도된 단순화
- 3-2-2 일정 인라인 수정 → `EventEdit` 화면 재사용(행 누르면 이동).
- 홈 마지막 모드·시험 공부량은 메모리에만(앱 재시작 시 일상 모드).
- 프로필 사진 변경·OS 푸시 권한 요청 없음(서버에 저장소/발송 기능 없음).
- 캘린더·차트·슬라이더·휠은 직접 그림(의존성 추가 안 함). 휠만 `@react-native-picker/picker`(iOS 네이티브).

## 저장소
실제 모노레포는 `~/Documents/project/team11/frontend`(원격 `2026ENDPoinT/team11`). 이 디렉터리는 작업 복사본.
기능 단위가 끝나면 `team11/frontend`를 통째로 교체 → `frontend/<기능>` 브랜치 → `main` PR(squash). 머지는 사용자 확인 후.
커밋 메시지는 한국어, Claude 기여 표시 넣지 않음(CONTRIBUTING.md).

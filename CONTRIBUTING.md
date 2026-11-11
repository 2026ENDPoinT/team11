# GitHub 전략

해커톤 규모(백엔드 1명 + 프론트 2명 + AI 2명, 총 5명)에 맞춘 최소한의 규칙. 과하게 쪼개지 않는다.

## 구조

모노레포, `main` 하나로 합친다.

```
main
  backend/   Spring Boot
  frontend/  React Native(Expo) — 아직 루트에 있음, 같은 방식으로 옮겨서 합류 예정
```

- `backend/`, `frontend/` 바깥 공통 파일(README, 이 문서, `.github/`)만 루트에 둔다.
- AI 서버는 별도 저장소가 없고 Notion으로 zip 전달받는 방식이라 이 repo에는 포함하지 않는다.

## 브랜치

- `main`만 장기 브랜치. **작업용 장기 브랜치(`backend`, `frontend` 같은)는 더 이상 만들지 않는다** — 지금 `backend`/`frontend`가 남아 있는 건 초기 세팅 과정에서 생긴 과도기 상태고, `frontend`도 `frontend/` 폴더로 옮겨 `main`에 합치면 둘 다 정리한다.
- 새 작업은 `main`에서 브랜치를 따서 짧게 끝내고 PR로 합친다. 브랜치 이름은 영역/내용이 드러나게: `backend/auth`, `backend/users`, `frontend/onboarding-ui` 등. 한 브랜치 = 한 도메인/기능, 오래 끌지 않는다.

## 커밋·PR

- 커밋 메시지는 지금까지처럼 한국어, 한 줄 요약 + 필요하면 본문. 영역이 섞이면 `[backend]`/`[frontend]` 접두어로 구분(모노레포라 `git log`가 한눈에 안 보일 수 있어서).
- PR은 `main` 대상으로 연다. PR 제목 = 머지될 때 커밋 메시지가 되므로 요약을 명확히.
- 머지 방식은 **Squash and merge**로 통일(머지 커밋/리베이스 금지) — `main` 히스토리를 도메인 단위로 깔끔하게 유지.
- CI(`Backend CI`)가 초록불이어야 머지. 프론트도 작업 범위가 생기면 `frontend/**` 경로로 스코프된 CI를 같은 방식(`.github/workflows/`)으로 추가해서 맞춘다.
- 리뷰어를 강제하진 않지만, 같은 영역에 사람이 둘 이상이면(프론트 2명, AI 2명) 서로 한 번씩 보고 머지하는 걸 권장. 백엔드는 혼자라 CI만 통과하면 셀프 머지해도 되지만, DB 마이그레이션·보안 설정처럼 되돌리기 어려운 변경은 다른 영역 사람에게도 한 번 공유한다.

## 보호 설정 (해볼지 말지는 선택)

지금은 아무도 `main`에 강제로 묶여 있지 않다. 원하면 GitHub 저장소 설정에서 아래를 켤 수 있다:

- `main`에 직접 push 금지, PR만 허용
- PR 머지 전 `Backend CI` 통과 필수(status check)

필요하면 말해주면 설정해준다 — 저장소 설정 변경이라 바로 켜지는 않았다.

## 비밀값

- `.env`, API 키, DB 비밀번호는 절대 커밋하지 않는다(`backend/.gitignore`에 `.env` 이미 제외).
- 배포 환경이 정해지면 그 환경변수/시크릿은 GitHub Actions Secrets 또는 호스팅 쪽 환경변수로 넣는다(아직 호스팅 위치 미정 — CLAUDE.md §10 참고).

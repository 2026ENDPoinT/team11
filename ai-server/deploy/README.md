# 실행·재시작·자원 제한 구성

이 폴더는 배포에 사용할 구성입니다. 실제 서버 설치·도메인·인증서 발급·Spring 수정까지 수행한 것은 아닙니다. Docker는 현재 개발 머신에 없어 컨테이너 기동을 검증하지 않았습니다. Python 실행과 임시 테스트 인증서를 신뢰 CA로 등록한 HTTPS 왕복은 검증합니다.

## 공통 준비

AI_INTERNAL_KEY를 공백 없는 ASCII 비밀값으로 환경에 주입합니다. `python -B -S -X utf8 -m ai_server.resources`가 ok=true인지 확인합니다. 신뢰한 모델 배포 변경에 한해 `--write-manifest`로 기준을 갱신합니다. 손상된 파일을 승인하는 자동 복구 기능이 아닙니다.

기본 설정은 [서버 명세](../docs/INTEGRATED_SERVER_SPEC.md)에 있습니다. Spring read timeout은 일반 서버 계산 예산2.5초, AI-03 전용10초에 각각 프로세스 정리·네트워크 여유를 더해 정합니다(AI-03 권장12초). 제한 변경 시 양쪽을 함께 조정합니다.3초를 무조건 만족한다는 SLA는 아닙니다.

## 로컬 / Windows

```powershell
python -m pip install -r ai_server/ai_06/requirements.txt
python -B -X utf8 -m ai_server --host 127.0.0.1 --port 8000
```

장애 시 재시작은 `deploy/start_server.ps1`을 직접 실행합니다. 정상 종료 또는 설정 오류(코드2)는 반복 재시작하지 않고, 비정상 종료는 최대30초 backoff로 재시작합니다. 실제 Windows 서비스에 등록하는 구성은 아닙니다. OS 메모리/CPU 제한이 필요하면 컨테이너 또는 OS 서비스 관리자를 사용하세요.

## HTTPS

```powershell
$env:SLEEP_TLS_CERT = 'C:/secure/ai-server-cert.pem'
$env:SLEEP_TLS_KEY = 'C:/secure/ai-server-key.pem'
python -B -X utf8 -m ai_server --host 0.0.0.0 --port 8443
```

인증서 파일은 실제 접속 DNS/SAN에 맞고 백엔드가 신뢰하는 CA에서 발급받아야 합니다. TLS1.2 이상을 사용합니다. 사설 CA는 Spring과 점검 도구의 신뢰 저장소에 등록하고 검증을 끄지 않습니다. API 키·TLS 개인 키를 Git/이미지/브라우저에 넣지 않습니다. TLS만으로 공용 노출의 호출량·방화벽 정책이 완성되지는 않습니다.

## Docker Compose

SLEEP에서 AI_INTERNAL_KEY를 설정한 뒤:

```text
docker compose -f deploy/compose.yaml up --build -d
```

Python 3.12 이미지에 AI-06 의존성을 설치하고 ai_server를 복사합니다. 비root·읽기 전용 파일시스템, 임시32MiB, 메모리512MiB, CPU2개, PID64개, 계산worker2개입니다. 장애 재시작을 설정하고 인증된 readiness를 healthcheck로 사용합니다. Docker의 unhealthy 상태 자체가 자동 재시작을 뜻하지는 않습니다. 프로세스 비정상 종료는 restart 정책으로 처리합니다.

포트는 기본127.0.0.1에만 공개합니다. 다른 머신의 Spring은 내부 네트워크·리버스 프록시 TLS 종료 또는 적절한 인증서 마운트 구성을 별도로 적용해야 합니다. 실제 트래픽으로 CPU/메모리 한도를 조정하세요. 숫자는 성능 보장값이 아닌 시작 설정입니다.

## Linux systemd

sleep-ai.service의 `/opt/sleep`, 전용 사용자 sleep-ai, `/etc/sleep-ai.env`를 준비합니다. Python 3.12로 `/opt/sleep/.venv`를 만들고 해당 가상환경의 pip로 `ai_server/ai_06/requirements.txt`를 설치합니다. env 파일은 서비스 계정만 읽을 수 있게 두고 키를 주입합니다. Restart=on-failure, cgroup CPU/메모리/PID 제한과 control-group 종료로 자식을 함께 정리합니다. 실제 설치·권한 부여·enable은 운영 환경에서 수행해야 합니다.

## 백엔드 머신에서 연동 확인

```text
python -B -S -X utf8 tools/check_backend_contract.py https://실제AI주소/ai/v1 --ca-file 실제사설CA.pem
```

공개 CA라면 --ca-file은 생략합니다. 같은 AI_INTERNAL_KEY를 환경에서 읽으며 로그에 키·본문을 출력하지 않습니다. health, ready, 활성 모델의 요청·응답과 요청 ID를 검사하고 실패 시 종료 코드1입니다. HTTP는 루프백 개발 주소만 허용합니다. 실제 Spring의 DTO·HTTP 클라이언트와 권한/동의 처리까지 자동 검증하는 도구는 아닙니다.

기본 검사는 AI-02~06의 백엔드 호출을 확인합니다.
AI-03의 기본 계산 예산은 `SLEEP_MEMORY_TIMEOUT_SECONDS=10`이고 전용 슬롯은 1개입니다.
AI-06의 기본 계산 예산은 `SLEEP_EXAM_TIMEOUT_SECONDS=10`입니다. readiness에서 `startupInference.models.ai03`과 `startupInference.models.ai06`을 확인하세요.

## AI-04 수면 회복과 AI-05 일정 분류

Spring에서는 [AI-04 수면 회복 계약](../docs/AI-04_BACKEND_CONTRACT.md)의
`/ai/v1/sleep-debt/calculate`와 [AI-05 일괄 분류 계약](../docs/AI-05_BACKEND_CONTRACT.md)의
`/ai/v1/insights/risks`를 사용합니다. 아래 예제는 기존 모델 직접 호출용입니다.

두 호출 모두 `POST`이며 기존 통합 서버와 동일하게 `X-Internal-Key`와
`Content-Type: application/json`을 사용합니다. 성공 시 HTTP 200으로
`{"result": {...}, "modelVersion": "..."}`를 반환하며 입력 오류는 공통 HTTP 400 오류 형식을 사용합니다.
배포 후 readiness의 `startupInference.models.ai04`, `ai05`로 기동 검사를 확인할 수 있습니다.

### AI-04 · `/ai/v1/plan/sleep-recovery`

```json
{
  "schema_version": "0.1",
  "now": "2026-11-13T18:00+09:00",
  "as_of_date": "2026-11-13",
  "preferences": {
    "adult_confirmed": true,
    "target_minutes": 480,
    "preparation_commute_minutes": 90,
    "sleep_latency_minutes": 15,
    "preferred_bedtime": "23:00",
    "preferred_wake_time": "07:00"
  },
  "sleep_records": [{"date": "2026-11-13", "actual_sleep_minutes": 420}],
  "events": [{
    "id": "class-1",
    "title": "수업",
    "start": "2026-11-14T09:00+09:00",
    "end": "2026-11-14T11:00+09:00"
  }]
}
```

`now`, `as_of_date`, `preferences`는 필수입니다. `adult_confirmed`는
`true`여야 합니다. 날짜는 `YYYY-MM-DD`, 시각은 오프셋을 포함한 분 단위 ISO 8601,
수면·이동 길이는 정수 분입니다. 기록과 일정은 생략하면 빈 배열을 사용합니다.
수면 기록은 실제 수면 분 또는 `sleep_start`와 `sleep_end`를 받을 수 있습니다.
응답 `result`에는 `sleep_summary`, 라벨이 붙은 `events`, `recovery_plan`,
`warnings`가 들어갑니다. 일정 라벨은 요청으로 전달된 라벨과 모델의 내장 규칙을 사용합니다.

### AI-05 · `/ai/v1/schedule/label`

```json
{"text":"자료구조 기말고사","user_rules":[],"options":{}}
```

`text`는 일정 한 건의 제목 또는 설명 문자열입니다. `user_rules`와
`options`는 선택입니다. 저장된 사용자 규칙은 요청에 실어 보내며,
`options`에는 과목 목록과 선택한 과목, 중요도, 집중도, 시작·마감 시각을 보낼 수 있습니다.
응답 `result`에는 `label`, `sleep_priority`, `reason`, `source`,
`needs_user_input`, 과목 후보, 평가 세부 내용이 들어갑니다.

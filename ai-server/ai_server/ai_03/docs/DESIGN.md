# AI-03 모델 설계

AI-03은 시험 전 최대 72시간에 배치할 주수면·낮잠·공부를 비교한다. 시험 준비 상태와 시험 성격, 일정, 최근 수면 기록을 받아 선택안과 대안을 돌려준다. 실행 모델은 `exam-sleep-workload-2.0.0`, 정책은 `workload-balanced-2`다.

| 계산 단계 | 현재 계산 | 사용 데이터와 근거 |
|---|---|---|
| 입력과 관측 정리 | 수업·시험 준비 등 고정 일정을 합치고, 수면 기록의 중복과 누락을 처리한다. 설정에 평소 취침·기상이 없으면 사용자의 수면 세션에서 추정한다. | 요청에 들어온 개인 일정·웨어러블·수동 기록. 외부 학습 데이터 없이 계산한다. |
| 수면 후보 생성 | 주수면, 무수면, 허용된 낮잠과 조합을 만든다. 평소 취침·기상과 일정 경계를 사용한다. 고정 일정 사이 60분 미만의 빈 구간은 공부 대신 휴식으로 분류한다. | 사용자 일정과 수면 패턴, 정책 설정을 사용한다. 수면과 추가 공부를 함께 비교하는 설계에는 아래 참고 논문 1을 사용했다. |
| 피로와 수면 부족 | 최근 최대 7일 기록으로 초기 피로를 추정한다. 계획을 따라 15분 기본 간격과 일정 경계마다 피로 상태를 갱신하고, 직전 24시간 수면량 부족도 반영한다. | 개인 수면 기록과 아래 참고 논문 2의 수면·각성 상태 전이 구조를 사용한다. 24시간 부족량과 기억을 연결하는 방식은 모델 가정이다. |
| 공부와 기억 | 가용 시간에 집중 비율과 부호화 효율을 곱해 유효 공부 시간을 구한다. 새 학습, 반복 복습, 망각, 학습 전후 수면 손실을 반영한다. | 아래 데이터셋 두 개에서 적합한 계수를 사용한다. 참고 논문 3은 학습 전 수면 손실을 고려하는 근거다. |
| 시험 시점 비교 | 시험 중간 시점의 회상과 주의력 대리값을 합친다. 기억 위주 시험은 회상에 더 비중을 둔다. 시나리오마다 최선안 대비 상대 손실을 구해 최악 손실이 작은 계획을 우선한다. | 준비 상태별 남은 작업량, 집중 비율, 시험 유형별 가중치는 공학적 가정이다. 실제 시험 성적이나 개인의 완료 확률을 학습한 계수는 없다. |
| 평소 시각과 미세 조정 | 평소 시각에 가까운 실행 가능 안을 최종 비교에 남긴다. 최악 상대 후회 차이가 0.05 이내인 경우 평소 시각에 가까운 주수면을 우선할 수 있다. 선택된 계획의 마지막 주수면은 ±15분 안에서 5분 간격으로 살핀 뒤 일부를 1분 간격으로 평가한다. | 0.05와 탐색 간격은 제품 정책이다. 평소 시각을 우선하는 근거는 아래 참고 논문 4–6이다. |

## 실행 모델에 쓰인 적합 데이터셋

| 데이터 파일 | 실제 사용 범위 | 적용한 계산 |
|---|---|---|
| `Siefert2024_learning_data.csv` | 참가자 34명의 행동 학습 시행 4,679행 | 최초 노출의 정답률과 반복 노출에 따른 복습 이득을 적합했다. 실행 모델은 최초 정답률과 반복 노출 계수를 사용한다. [행동 데이터 저장소](https://github.com/schapirolab/Siefert2024) · [Siefert, Uppuluri, Mu, Tandoc, Antony, Schapiro (2024), *Memory reactivation during sleep does not act holistically on object memory*](https://doi.org/10.1523/JNEUROSCI.0022-24.2024) |
| `Combined_Sleep_Loss_Data.csv` | 원본 310개 효과크기 중 39개 연구의 56개 효과크기 | 학습 전·후와 수면 제한·완전 박탈을 조합한 네 조건의 기억 손실 효과크기를 적합했다. [Crowley 데이터 저장소](https://osf.io/j8sqx/) · [Crowley, Alderman, Javadi, Tamminen (2024), *A systematic and meta-analytic review of the impact of sleep restriction on memory formation*](https://doi.org/10.1016/j.neubiorev.2024.105929) |

`Restriction_Data.csv`는 수면 시간과 제한 일수에 따른 용량 곡선을 별도로 검토하는 데 사용했다. 이 실험에서 적합한 곡선은 현재 추천 계산에 포함되지 않는다. [연구 검토 기록](../../../dummy/docs/AI07_WORKLOAD_REVISION.md)

## 설계에 참고한 논문

1. **Cousins, Wong, Raghunath, Look, Chee (2019).** 낮잠과 같은 시간의 추가 공부를 비교하는 후보를 두는 근거로 사용했다. [*The long-term memory benefits of a daytime nap compared with cramming*](https://doi.org/10.1093/sleep/zsy207)
2. **McCauley, Kalachev, Smith, Belenky, Dinges, Van Dongen (2009).** 수면과 각성에 따라 갱신하는 빠른·느린 피로 상태의 구조를 사용했다. [*A new mathematical model for the homeostatic effects of sleep loss on neurobehavioral performance*](https://pubmed.ncbi.nlm.nih.gov/18938181/)
3. **Yoo, Hu, Gujar, Jolesz, Walker (2007).** 학습 이전 수면 손실을 새 기억 형성 단계에서 고려하는 근거로 사용했다. [*A deficit in the ability to form new human memories without sleep*](https://doi.org/10.1038/nn1851)
4. **Dijk, Czeisler (1995).** 생체리듬과 수면 압력에 따라 잠들기 쉬운 시각이 달라진다는 근거로 사용했다. [*Contribution of the circadian pacemaker and the sleep homeostat to sleep propensity, sleep structure, electroencephalographic slow waves, and sleep spindle activity in humans*](https://pubmed.ncbi.nlm.nih.gov/7751928/)
5. **Phillips, Clerx, O’Brien, Sano, Barger, Picard, Lockley, Klerman, Czeisler (2017).** 대학생의 수면 규칙성과 생체리듬·학업 수행의 연관성을 평소 수면 패턴 우선 정책의 배경으로 참고했다. [*Irregular sleep/wake patterns are associated with poorer academic performance and delayed circadian and sleep/wake timing*](https://doi.org/10.1038/s41598-017-03171-4)
6. **Okano, Kaczmarzyk, Dave, Gabrieli, Grossman (2019).** 대학생의 수면 규칙성과 학업 성과의 관련성을 같은 정책의 배경으로 참고했다. [*Sleep quality, duration, and consistency are associated with better academic performance in college students*](https://doi.org/10.1038/s41539-019-0055-z)

**계산 예산.** 기본 수면 후보의 명목 한도는 36시간 이하 40개, 초과 20개다. 각각 24개와 16개의 공통 가정으로 선별해 최대 4개와 3개를 256개 가정으로 평가한다. 미세 탐색은 추가 후보 최대 1개를 전체 가정으로 평가한다. 단계 기록이 충분한 경우 별도로 워치 기반 기상 ±15분 후보를 만들 수 있다. 이 보조에는 동일한 지원 기기 계열에서 3시간 이상이고 단계 커버리지가 70% 이상인 적격 세션이 7개 이상 필요하다.

**해석.** 배정한 수면 구간은 수면 기회이며 실제 잠든 시간은 가정에 따른 범위로 표시한다. 추천은 제한된 후보 집합에서 얻은 결과다. 학습에 사용한 집단 효과와 개인의 시험 성적 사이에는 검증되지 않은 연결 가정이 있다.

[근거 산출물](../artifacts/evidence_priors.json) · [학습·검토 자료 상세](../../../dummy/docs/AI07_WORKLOAD_REVISION.md) · [현재 정책](../config/policy.json) · [계산 플로우](FLOW.md)

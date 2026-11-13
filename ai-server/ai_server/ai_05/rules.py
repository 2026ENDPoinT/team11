"""Deterministic Korean schedule rules. No model, network, or persistent data."""
import re
import unicodedata
from dataclasses import dataclass

MAX_TEXT = 2000
LABELS = ("시험", "시험 준비", "시험 행정", "동아리", "MT·OT", "학생회", "면접", "발표", "회의", "수업", "운동", "운전", "집안일", "약속", "기타")
PRIORITIES = ("높음", "보통", "낮음")
VERSION = "1.2.0"


@dataclass(frozen=True)
class Rule:
    id: str
    label: str
    priority: str
    pattern: str
    reason: str
    specificity: int = 0
    context: bool = False


# Short English abbreviations must not match inside words (HTML, Saturday, etc.).
ENGLISH_EXAMS = r"(?<![a-z])(?:toeic|toefl|ielts|teps|opic|jlpt|jpt|hsk|topik|gre|gmat|sat|act|g-?telp)(?![a-z])"
EXAM = (
    r"(?:중간\s*(?:고사|시험|평가)|기말\s*(?:고사|시험|평가)|쪽지\s*시험|퀴즈|quiz(?![a-z])|"
    r"모의\s*(?:고사|평가|시험)|학력\s*평가|전국\s*연합\s*학력\s*평가|모평|학평|"
    r"대학\s*수학\s*능력\s*시험|수능|수학\s*능력\s*시험|"
    r"자격(?:증)?\s*(?:시험|검정)|필기\s*시험|실기\s*시험|구술\s*시험|구두\s*시험|"
    r"전공\s*시험|종합\s*시험|졸업\s*시험|재시험|재시(?:험)?(?![가-힣])|추가\s*시험|"
    r"토익(?:\s*스피킹)?|토스(?=\s*(?:응시|시험|접수|공부|준비|성적|결과|연습|스터디))|토플|아이엘츠|텝스|오픽|지텔프|"
    r"한국사\s*능력\s*검정(?:\s*시험)?|한능검|컴퓨터\s*활용\s*능력(?:\s*[12]급)?|컴활(?:\s*[12]급)?|"
    r"정보\s*처리\s*(?:산업)?기사|정처기|정처산기|운전\s*면허\s*(?:필기|실기|시험)|"
    r"임용\s*(?:고사|시험)|공무원\s*시험|편입\s*시험|적성\s*검사|인적성(?:\s*검사)?|"
    r"(?<![가-힣])(?:중간|기말)(?=$|\s*(?:응시|준비|공부|대비|접수|성적|결과|기간|끝|시작|벼락))|"
    r"시험(?!\s*(?:관|적|삼아|용))|고사(?![가-힣])|" + ENGLISH_EXAMS + r")"
) + r"(?:\s*(?:n[1-5]|[1-6]\s*급|[12]\s*차|필기|실기))?"
PREPARATION = r"(?:준비|대비|공부|복습|예습|벼락치기|스터디|문제\s*풀이|기출(?:\s*(?:문제|풀이|풀기))?|문제집|오답\s*(?:정리|노트)|해설\s*(?:강의|수업)|특강|인강|강의|수업|연습)"
EXAM_ADMIN = r"(?:원서\s*(?:접수|제출)|접수(?:\s*(?:마감|시작|신청))?|응시\s*(?:신청|등록|취소)|신청|등록|수험표\s*(?:출력|확인)|고사장\s*확인|(?:성적(?:표)?|점수|결과|합격자?)\s*(?:발표|확인|조회|공개)|(?:범위|일정|시간표)\s*(?:공지|확인|안내))"
CLUB = r"(?:중앙\s*동아리|연합\s*동아리|교내\s*동아리|학술\s*동아리|봉사\s*동아리|동아리\s*연합회|동아리|동연(?=$|\s|회의)|동방(?=$|\s|청소|모임|회의|정리)|소모임|동호회|학회\s*(?:정모|정기\s*모임|신입부원))"
STUDENT_COUNCIL = r"(?:총\s*학생회|과\s*학생회|학과\s*학생회|단과대\s*학생회|학생회|총학(?=$|\s|회의|행사|선거|집행부|운영|축제|부스|주점)|과대(?=$|\s|회의|모임)|과\s*대표|학과\s*대표|학생\s*자치회|학생\s*대의원회|학생\s*대의원\s*총회|중앙\s*운영\s*위원회|중운위|전학대회|확대\s*운영\s*위원회|확운위)"
RETREAT = r"(?:(?<![a-z0-9])m\s*\.?\s*t\.?(?![a-z0-9])|엠티|멤버십\s*트레이닝|(?<![a-z0-9])ot(?![a-z0-9])|오티|오리엔테이션|새내기\s*(?:새로\s*)?배움터|새터|신입생\s*환영회)"


# Rules are ordered to break ties at the same position; higher priority wins overall.
RULES = (
    Rule("exam_admin", "시험 행정", "낮음", EXAM + r"\s*(?:[은는이가을를의]\s*)?" + EXAM_ADMIN,
         "시험 접수·안내·결과 확인 일정으로, 시험 응시 자체와는 구분했습니다.", 60),
    Rule("exam_preparation", "시험 준비", "보통", EXAM + r"\s*(?:[을를의]\s*)?(?:전날\s*|대비\s*)?" + PREPARATION,
         "시험을 준비하거나 공부하는 일정으로, 실제 응시와 구분해 기본적인 집중력이 필요합니다.", 50),
    Rule("exam_mock_practice", "시험 준비", "보통", EXAM + r"\s*모의\s*(?:고사|시험)\s*(?:풀이|풀기|공부)",
         "모의 문제를 풀며 시험을 준비하는 학습 일정입니다.", 50),
    Rule("exam_study_group", "시험 준비", "보통", r"(?:기말|중간|시험)\s*기간\s*(?:공부|스터디)|(?:기출\s*문제\s*풀이|시험\s*대비\s*스터디)",
         "시험 대비 학습 일정으로 기본적인 집중력이 필요합니다.", 50),
    Rule("exam", "시험", "높음", EXAM, "평가를 받는 시험 일정으로 집중력과 컨디션이 중요합니다."),
    Rule("council_responsibility", "학생회", "높음", STUDENT_COUNCIL + r"\s*(?:(?:축제|행사|부스|주점)\s*)?(?:진행\s*총괄|총괄|사회\s*진행|사회자|행사\s*진행)",
         "학생회 행사 진행을 책임지는 일정으로 집중력과 컨디션이 중요합니다.", 35),
    Rule("student_council", "학생회", "보통", STUDENT_COUNCIL,
         "학생 자치 조직의 활동 일정으로 기본적인 집중력과 컨디션이 필요합니다.", 20, True),
    Rule("club_performance", "동아리", "높음", CLUB + r"\s*(?:(?:정기|정기\s*공연|축제|무대)\s*)?(?:공연\s*(?:출연|참가)|무대\s*(?:출연|공연)|대회\s*(?:출전|참가))",
         "동아리 공연이나 대회에 직접 참여하는 일정으로 집중력과 컨디션이 중요합니다.", 35),
    Rule("club", "동아리", "보통", CLUB,
         "동아리 모임·활동 일정으로 기본적인 컨디션이 필요합니다.", 20, True),
    Rule("retreat_planning", "MT·OT", "보통", RETREAT + r"\s*(?:[을를의]\s*)?(?:준비|기획|사전\s*답사|답사|준비물\s*구매|장보기)",
         "MT·OT를 준비하는 일정으로 실제 참가와 구분해 분류했습니다.", 40),
    Rule("retreat", "MT·OT", "보통", RETREAT,
         "MT·OT 등 교류·적응 행사 일정입니다. 활동 강도에 따라 중요도를 직접 조정할 수 있습니다.", 30, True),
    Rule("interview", "면접", "높음", r"면접|인터뷰", "평가 또는 질의응답이 있는 면접·인터뷰 일정으로 집중력이 중요합니다."),
    Rule("presentation", "발표", "높음", r"발표|프레젠테이션|피칭|\bpt\s*발표", "청중 앞에서 내용을 전달하는 발표 일정으로 집중력과 컨디션이 중요합니다."),
    Rule("competition", "운동", "높음", r"(?:대회|경기)\s*(?:참가|출전)|마라톤(?:\s*(?:풀코스|참가|출전))?", "경기나 대회에 참여하는 일정으로 체력과 컨디션이 중요합니다."),
    Rule("long_drive", "운전", "높음", r"장거리\s*운전|고속도로\s*운전|야간\s*운전", "장거리 또는 주의가 필요한 운전 일정으로 안전을 위한 집중력이 중요합니다."),
    Rule("meeting", "회의", "보통", r"회의(?!실)|(?<!팬)미팅(?!룸)", "일반적인 회의 일정으로 기본적인 집중력이 필요합니다."),
    Rule("class", "수업", "보통", r"수업|강의|세미나", "학습 내용을 듣고 참여하는 일정으로 기본적인 집중력이 필요합니다."),
    Rule("exercise", "운동", "보통", r"운동|헬스|러닝|조깅|요가|수영|등산|자전거\s*타기", "일상적인 운동 일정으로 기본적인 체력과 컨디션이 필요합니다."),
    Rule("drive", "운전", "보통", r"운전", "운전 일정으로 주변 상황에 주의를 기울일 수 있는 컨디션이 필요합니다."),
    Rule("chore", "집안일", "낮음", r"분리\s*수거|쓰레기\s*(?:버리기|배출)|택배\s*(?:수령|받기)|청소|설거지|빨래", "가벼운 일상 작업으로 높은 수준의 집중력이 필요한 일정은 아닙니다."),
    Rule("appointment", "약속", "보통", r"약속|친구(?:와|랑)\s*(?:저녁|점심|식사|만남)|가족(?:과|이랑)\s*(?:저녁|점심|식사)|동창회", "일상적인 만남 일정으로 기본적인 컨디션이 필요합니다."),
)
COMPILED = tuple((rule, re.compile(rule.pattern, re.IGNORECASE)) for rule in RULES)
RULE_BY_ID = {rule.id: rule for rule in RULES}
RANK = {"높음": 3, "보통": 2, "낮음": 1}


def validate_text(text):
    if not isinstance(text, str) or not text.strip():
        raise ValueError("일정 텍스트를 입력하세요.")
    if len(text) > MAX_TEXT:
        raise ValueError(f"일정은 {MAX_TEXT:,}자 이하로 입력하세요.")
    return text.strip()


EXCLUSIONS = (
    ("before", "search", "all", r"(?:취소|연기|철회)(?:된|한)?\s*$|(?:취소|연기)된.{0,24}$", "취소·연기된 활동"),
    ("after", "match", "all", r"\s*(?:[은는이가을를에의도]\s*)?(?:일정[은이가]?\s*)?(?:취소|연기|철회|불참|없(?:음|다|어|는)|안\s*(?:함|해|감|가|봄|보기|보는|참석)|못\s*(?:감|가|참석)|하지\s*않|보지\s*않|아님|아니라)", "취소·연기·부정된 활동"),
    ("full_after", "match", "context", r"\s*(?:[은는이가을를의]\s*)?(?:(?:정기|정모|모임|회의|행사|활동|mt|엠티|ot|오티|준비|계획|일정|공연|면접|총회|간담회|수업|공부|스터디|시험|대회|참가|출전|발표|특별)\s*){0,6}(?:취소|연기|불참|없음|안\s*감)", "취소·연기된 조직·행사 활동"),
    ("after", "match", "activity", r"\s*(?:[을를의]\s*)?(?:(?:자료|원고|슬라이드)\s*(?:정리|작성|준비|검토)|준비|연습|공부|예습|대비|관람|참관|구경|시청)", "준비 또는 관람 활동"),
    ("before", "search", "all", r"(?:친구|동생|형|누나|언니|오빠|자녀|동료|남편|아내)의\s*$", "타인의 활동"),
    ("full_after", "match", "all", r"\s*(?:[을를]\s*)?(?:보는|본|볼|준비하는|참가하는)\s*(?:친구|동생|동료|가족)", "타인의 활동"),
)


def exclusion_reason(before, after, full_after, rule):
    fields = {"before": before, "after": after, "full_after": full_after}
    for field, method, scope, pattern, reason in EXCLUSIONS:
        if scope == "context" and not rule.context or scope == "activity" and rule.context:
            continue
        matcher = re.match if method == "match" else re.search
        if matcher(pattern, fields[field]):
            return reason
    return None


def classify(text):
    original = validate_text(text)
    normalized = unicodedata.normalize("NFKC", original).lower()
    # Preserve dotted abbreviations before sentence segmentation.
    normalized = re.sub(r"(?<![a-z0-9])([mo])\s*\.\s*([t])\.?(?![a-z0-9])", r"\1\2", normalized)
    normalized = re.sub(r"[^\S\n]+", " ", normalized)
    segments = re.split(r"[\n,，;；.!?。/]+|\s+(?:그리고|대신|하지만|후|후에|이후|끝나고)\s+", normalized)
    candidates = []
    excluded = []
    for segment in segments:
        segment_candidates = []
        hits = []
        for rule, pattern in COMPILED:
            hits.extend((match.start(), match.end(), rule, match.group()) for match in pattern.finditer(segment))
        hits.sort(key=lambda hit: (hit[0], -(hit[1] - hit[0])))
        # Keep the most specific match when rules overlap (e.g. long drive vs drive).
        unique = []
        for hit in hits:
            if any(hit[0] < other[1] and other[0] < hit[1] for other in unique):
                continue
            unique.append(hit)
        for i, (start, end, rule, phrase) in enumerate(unique):
            next_start = unique[i + 1][0] if i + 1 < len(unique) else len(segment)
            before, after = segment[:start], segment[end:next_start]
            excluded_by = exclusion_reason(before, after, segment[end:], rule)
            if excluded_by:
                excluded.append({"label": rule.label, "matched_text": phrase, "reason": excluded_by})
            else:
                segment_candidates.append({"label": rule.label, "sleep_priority": rule.priority,
                                   "reason": rule.reason, "matched_rule": rule.id, "matched_text": phrase})
        # Organization names describe the context of an exam/interview/retreat;
        # they must not override its actual type, even if its priority is lower.
        if any(item["label"] in {"시험", "시험 준비", "시험 행정", "면접", "발표", "MT·OT"} for item in segment_candidates):
            segment_candidates = [item for item in segment_candidates if item["matched_rule"] not in {"club", "student_council"}]
        candidates.extend(segment_candidates)
    if candidates:
        chosen = max(candidates, key=lambda item: (RANK[item["sleep_priority"]], RULE_BY_ID[item["matched_rule"]].specificity))
        reason = chosen["reason"]
        if len(candidates) > 1:
            reason = f"여러 활동 중 '{chosen['matched_text']}'을 기준으로 분류했습니다. " + reason
        return {**chosen, "reason": reason, "source": "rule", "needs_user_input": False,
                "matches": candidates, "excluded": excluded, "rule_version": VERSION}
    reason = "정해진 분류 규칙과 일치하지 않습니다. 일정 라벨과 숙면 중요도를 직접 설정해 주세요."
    if excluded:
        reason = "취소·준비·관람 또는 타인의 활동을 분류 근거에서 제외했습니다. 직접 설정해 주세요."
    return {"label": "기타", "sleep_priority": None, "reason": reason, "matched_rule": None,
            "matched_text": None, "source": "unclassified", "needs_user_input": True,
            "matches": [], "excluded": excluded, "rule_version": VERSION}

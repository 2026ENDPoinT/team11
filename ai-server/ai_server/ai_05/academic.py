"""Course vocabulary and activity context, independent of LMS access."""
import re
from .rules import LABELS, classify, validate_text
from unicodedata import normalize

DEFAULT_COURSES = [
    {"name":"자료구조", "aliases":["자구","자료구조론","DS"]},
    {"name":"운영체제", "aliases":["운체","OS"]},
    {"name":"알고리즘", "aliases":["알고","ALGO"]},
    {"name":"데이터베이스", "aliases":["디비","DB"]},
    {"name":"컴퓨터네트워크", "aliases":["컴네","네트워크","CN"]},
    {"name":"인공지능", "aliases":["인공","AI"]},
    {"name":"기계학습", "aliases":["머신러닝","ML"]},
    {"name":"객체지향프로그래밍", "aliases":["객프","OOP"]},
    {"name":"컴퓨터구조", "aliases":["컴구","CA"]},
    {"name":"미적분학", "aliases":["미적","미적분","Calculus"]},
    {"name":"일반물리학", "aliases":["일반물리","일물"]},
    {"name":"일반화학", "aliases":["일화"]},
    {"name":"인지심리학", "aliases":["인지심리","인심"]},
]
ACADEMIC_LABELS = list(LABELS[:-1]) + ["과제·제출","학습·복습","기타"]
ACTIVITIES = [
    {"id":"exam", "label":"시험", "priority":"높음", "pattern":r"중간(?:\s*(?:고사|시험))?|기말(?:\s*(?:고사|시험))?|퀴즈|쪽지\s*시험|시험|평가", "reason":"과목 정보와 시험·평가 표현을 함께 확인했습니다."},
    {"id":"assignment", "label":"과제·제출", "priority":"보통", "pattern":r"과제|제출|리포트|레포트|보고서|실습\s*보고서|프로젝트\s*마감|팀플\s*마감|과제\s*마감", "reason":"과제 작성·제출 활동으로 기본적인 집중력이 필요합니다."},
    {"id":"study", "label":"학습·복습", "priority":"보통", "pattern":r"복습|예습|공부|문제\s*풀이|기출\s*풀이|스터디|개념\s*정리", "reason":"학습·복습 활동으로 기본적인 집중력이 필요합니다."},
    {"id":"meeting", "label":"회의", "priority":"보통", "pattern":r"회의|미팅|팀플\s*모임", "reason":"과목 관련 회의·팀 활동으로 기본적인 집중력이 필요합니다."},
    {"id":"class", "label":"수업", "priority":"보통", "pattern":r"수업|강의|세미나|실습", "reason":"과목 수업·실습 활동으로 기본적인 집중력이 필요합니다."},
]
SPECIALIZED = {"시험","시험 준비","시험 행정","면접","발표","운전"}
CANCEL = r"취소|연기|불참|안\s*(?:감|가|함)|못\s*(?:감|가)"

def norm(text):
    return re.sub(r"\s+"," ",normalize("NFKC",text).lower()).strip()

def phrase_pattern(phrase):
    return r"(?<![a-z0-9가-힣])" + re.escape(norm(phrase)).replace(r"\ ",r"\s+") + r"(?=$|[^a-z0-9가-힣]|(?:은|는|이|가|을|를|의)(?=\s|$))"

def contains_phrase(text, phrase):
    return re.search(phrase_pattern(phrase),norm(text)) is not None

def validate_courses(courses):
    if courses is None:
        return DEFAULT_COURSES
    if not isinstance(courses,list) or len(courses)>200:
        raise ValueError("과목 목록은 최대 200개의 배열이어야 합니다.")
    result=[]; names=set()
    for item in courses:
        if not isinstance(item,dict) or not isinstance(item.get("name"),str) or not 1<=len(item["name"].strip())<=60:
            raise ValueError("과목 이름은 1~60자로 입력하세요.")
        name=item["name"].strip(); aliases=item.get("aliases",[])
        if norm(name) in names: raise ValueError("같은 과목 이름이 중복되었습니다.")
        if not isinstance(aliases,list) or len(aliases)>20 or any(not isinstance(a,str) or not 1<=len(a.strip())<=40 for a in aliases):
            raise ValueError("과목 약칭은 1~40자 문자열의 배열로, 과목당 최대 20개까지 입력하세요.")
        names.add(norm(name)); result.append({"name":name,"aliases":list(dict.fromkeys(a.strip() for a in aliases))})
    return result

def classify_academic(text,courses=None,course_name=None):
    validate_text(text); catalog=validate_courses(courses); normalized=norm(text)
    base=classify(text); hits=[]
    for course in catalog:
        for phrase in [course["name"]]+course["aliases"]:
            if contains_phrase(normalized,phrase):
                hits.append({"course":course["name"],"expression":phrase,"is_alias":norm(phrase)!=norm(course["name"])})
    names=list(dict.fromkeys(hit["course"] for hit in hits))
    if course_name:
        found=next((c for c in catalog if c["name"]==course_name),None)
        if not found: raise ValueError("선택한 과목이 과목 목록에 없습니다.")
        selected=course_name
    else: selected=names[0] if len(names)==1 else None
    # Distant cancellation is scoped by explicit activity clauses.
    segments=re.split(r"[,;\n.!?]+|\s+(?:그리고|대신|하지만|후|후에|이후)\s+",normalized)
    active_segments=[s for s in segments if not re.search(CANCEL,s)]
    activities=[]
    for activity in ACTIVITIES:
        for segment in active_segments:
            if re.search(activity["pattern"],segment):
                if activity["id"]=="exam" and (not names and not course_name or re.search(r"공부|복습|대비|준비|결과|접수|성적|발표",segment)):
                    continue
                # Generic '제출/공부/실습' needs subject context; explicit homework/study terms can stand alone.
                if not names and not course_name and not re.search(r"과제|레포트|리포트|보고서|복습|예습|기말\s*과제|중간\s*과제",segment):
                    continue
                activities.append(activity); break
    if activities:
        # Work/study context is more specific than an exam token used as a modifier.
        ids={a["id"] for a in activities}
        chosen=next((a for a in activities if a["id"]=="meeting"),None) if "meeting" in ids else None
        if chosen is None: chosen=next((a for a in activities if a["id"]=="assignment"),activities[0])
        if base["label"] not in SPECIALIZED:
            base={**base,"label":chosen["label"],"sleep_priority":chosen["priority"],"reason":chosen["reason"],
                  "source":"rule","needs_user_input":False,"matched_rule":"academic_"+chosen["id"],"matched_text":None}
        activity=({"시험":"exam","시험 준비":"study","시험 행정":"exam_admin","면접":"interview","발표":"presentation","운전":"drive"}.get(base["label"]) if base["label"] in SPECIALIZED else chosen["id"])
    else:
        activity={"시험":"exam","시험 준비":"study","시험 행정":"exam_admin","면접":"interview","운전":"drive","회의":"meeting","수업":"class","발표":"presentation"}.get(base["label"])
    return {**base,"course":selected,"course_candidates":names,"course_matches":hits,
            "needs_course_selection":not course_name and len(names)>1,"activity":activity}

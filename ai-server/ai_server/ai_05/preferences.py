"""Pure personalization helpers. The caller owns per-user storage."""
import re
import unicodedata

from .rules import PRIORITIES, VERSION, classify, validate_text
from .academic import classify_academic, contains_phrase, validate_courses
from .assessment import assess


def normalize_text(text):
    return re.sub(r"\s+", " ", unicodedata.normalize("NFKC", validate_text(text)).lower()).strip()


def make_user_rule(text, label, sleep_priority, scope="exact", keywords=None, course_name=None, activity=None):
    validate_text(text)
    if not isinstance(label, str) or not 1 <= len(label.strip()) <= 30:
        raise ValueError("라벨은 1~30자로 입력하세요.")
    if sleep_priority not in PRIORITIES:
        raise ValueError("숙면 중요도는 높음·보통·낮음 중에서 선택하세요.")
    rule={"text": text.strip(), "label": label.strip(), "sleep_priority": sleep_priority}
    if scope=="exact": return rule
    if scope=="keywords":
        if not isinstance(keywords,list) or not 1<=len(keywords)<=5 or any(not isinstance(k,str) or not 1<=len(k.strip())<=40 for k in keywords):
            raise ValueError("표현 조합은 1~40자 키워드 1~5개로 입력하세요.")
        return {**rule,"scope":scope,"keywords":sorted(set(normalize_text(k) for k in keywords))}
    if scope=="course_activity":
        if not isinstance(course_name,str) or not 1<=len(course_name.strip())<=60 or activity not in ("exam","assignment","study","meeting","class","presentation","exam_admin"):
            raise ValueError("과목과 활동을 선택하세요.")
        return {**rule,"scope":scope,"course_name":course_name.strip(),"activity":activity}
    raise ValueError("지원하지 않는 적용 범위입니다.")

def rule_key(rule):
    scope=rule.get("scope","exact")
    if scope=="exact": return normalize_text(rule["text"])
    if scope=="keywords":
        import json
        return "keywords:"+json.dumps(sorted(rule["keywords"]),ensure_ascii=False,separators=(",",":"))
    return "course_activity:"+normalize_text(rule["course_name"])+":"+rule["activity"]


def validate_user_rules(user_rules):
    if not isinstance(user_rules, list) or len(user_rules) > 500:
        raise ValueError("사용자 규칙은 최대 500개의 배열이어야 합니다.")
    result = []
    keys = set()
    for rule in user_rules:
        if not isinstance(rule, dict):
            raise ValueError("사용자 규칙의 형식이 올바르지 않습니다.")
        valid = make_user_rule(rule.get("text"), rule.get("label"), rule.get("sleep_priority"),rule.get("scope","exact"),rule.get("keywords"),rule.get("course_name"),rule.get("activity"))
        key = rule_key(valid)
        if key in keys:
            raise ValueError("같은 일정에 대한 사용자 규칙이 중복되었습니다.")
        keys.add(key)
        result.append(valid)
    return result


def classify_with_preferences(text, user_rules=None, options=None):
    key = normalize_text(text)
    rules = validate_user_rules([] if user_rules is None else user_rules)
    options={} if options is None else options
    if not isinstance(options,dict): raise ValueError("추가 정보는 객체여야 합니다.")
    automatic = assess(classify_academic(text,options.get("courses"),options.get("course_name")),options)
    exact=[r for r in rules if r.get("scope","exact")=="exact" and normalize_text(r["text"])==key]
    broad=[]
    if not automatic["excluded"]:
        for r in rules:
            if r.get("scope")=="keywords" and all(contains_phrase(text,k) for k in r["keywords"]): broad.append(r)
            if r.get("scope")=="course_activity" and automatic["course"]==r["course_name"] and automatic["activity"]==r["activity"]: broad.append(r)
    matches=exact or broad
    if not exact and len({(r["label"],r["sleep_priority"]) for r in broad})>1:
        return {**automatic,"preference_conflict":True,"candidate_user_rules":broad,"needs_user_input":True,
                "reason":"서로 다른 사용자 규칙이 동시에 일치합니다. 적용할 설정을 직접 선택해 주세요."}
    saved=matches[0] if matches else None
    if saved is None:
        return automatic
    reason="이전에 같은 일정에 저장한 라벨과 숙면 중요도를 적용했습니다." if saved.get("scope","exact")=="exact" else "사용자가 저장한 적용 범위와 일치해 라벨과 숙면 중요도를 적용했습니다."
    return {"label": saved["label"], "sleep_priority": saved["sleep_priority"],
            "reason": reason,
            "source": "saved_user_rule", "needs_user_input": False,
            "matched_rule": None, "matched_text": None, "matched_user_rule": rule_key(saved),
            "matched_scope":saved.get("scope","exact"),"course":automatic["course"],"activity":automatic["activity"],
            "course_candidates":automatic["course_candidates"],"course_matches":automatic["course_matches"],
            "needs_course_selection":automatic["needs_course_selection"],
            "assessment":{**automatic["assessment"],"final_priority":saved["sleep_priority"],"priority_source":"saved_user_rule"},
            "rule_version": VERSION, "automatic_result": automatic}

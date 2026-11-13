"""Transparent product heuristics, with urgency kept separate from sleep priority."""
from datetime import datetime, timezone
from .rules import PRIORITIES

def assess(result,options=None):
    options={} if options is None else options
    if not isinstance(options,dict): raise ValueError("추가 정보는 객체여야 합니다.")
    importance=options.get("importance") or None; focus=options.get("focus") or None
    for value in (importance,focus):
        if value is not None and value not in PRIORITIES: raise ValueError("중요도·집중력은 높음·보통·낮음 중 선택하세요.")
    times={}
    for field in ("starts_at","deadline_at","reference_at"):
        value=options.get(field)
        if value:
            try:
                parsed=datetime.fromisoformat(value.replace("Z","+00:00"))
                if parsed.tzinfo is None: raise ValueError()
                times[field]=parsed
            except (ValueError,TypeError,AttributeError): raise ValueError("일시는 시간대를 포함한 ISO 형식으로 입력하세요.")
    reference=times.get("reference_at") or (datetime.now(timezone.utc) if times else None)
    urgency="미입력"
    if "deadline_at" in times:
        hours=(times["deadline_at"]-reference).total_seconds()/3600
        urgency="기한 지남" if hours<0 else "24시간 이내" if hours<=24 else "3일 이내" if hours<=72 else "3일 초과"
    start_status="미입력"
    if "starts_at" in times:
        hours=(times["starts_at"]-reference).total_seconds()/3600
        start_status="시작 시각 지남" if hours<0 else "24시간 이내 시작" if hours<=24 else "24시간 이후 시작"
    rank={None:0,"낮음":1,"보통":2,"높음":3}; base=result["sleep_priority"]
    final=max((base,importance,focus),key=lambda v:rank[v])
    factors=[f"기본 분류 중요도: {base or '미설정'}",f"사용자 중요도: {importance or '미입력'}",f"집중력 요구: {focus or '미입력'}"]
    if urgency!="미입력": factors.append("마감: "+urgency+" (숙면 중요도에는 가산하지 않음)")
    if start_status!="미입력": factors.append("시작: "+start_status+" (숙면 중요도에는 가산하지 않음)")
    source="explicit_signal" if rank[final]>rank[base] else "base_rule"
    reason=result["reason"]
    if source=="explicit_signal": reason+=" 사용자가 입력한 중요도·집중력 요구 중 높은 값을 반영했습니다."
    return {**result,"sleep_priority":final,"reason":reason,"assessment":{
        "base_priority":base,"importance":importance,"focus":focus,"final_priority":final,"priority_source":source,
        "urgency":urgency,"start_status":start_status,"factors":factors,
        "starts_at":options.get("starts_at") or None,"deadline_at":options.get("deadline_at") or None,
        "reference_at":options.get("reference_at") or (reference.isoformat() if reference else None),
        "missing_inputs":[f for f in ("importance","focus","starts_at","deadline_at") if not options.get(f)]}}

"""Bounded strategy-preserving sleep layout search and complete timelines."""
from datetime import timedelta
import math
from .intervals import KST, contains, free, grid, merge, wire
from .schemas import at

MIN_STUDY_GAP_BETWEEN_FIXED_MIN = 60


def minutes(a,b): return (b-a).total_seconds()/60


def timeline(p,layout):
    blocked=[(a,b,k) for a,b,k in layout]+[(a,b,'FIXED') for a,b in p.fixed]
    short_gaps = [(end, start) for (_, end), (start, _) in zip(p.fixed, p.fixed[1:])
                  if 0 < minutes(end, start) < MIN_STUDY_GAP_BETWEEN_FIXED_MIN]
    # Recovery buffer only fills genuinely free time after sleep. Preparation
    # and travel already provide wake lead, so they are never double counted.
    after=[]
    for a,b,k in layout:
        right=min(p.end,b+timedelta(minutes=30))
        for x,y in free(b,right,[(c,d) for c,d,_ in blocked]): after.append((x,y,'WAKE_BUFFER'))
    blocks=sorted(blocked+after);out=[];cursor=p.start
    for a,b,k in blocks:
        if cursor<a:
            kind = 'REST' if contains(short_gaps, cursor, a) else 'STUDY_AVAILABLE'
            out.append((cursor,a,kind))
        out.append((a,b,k));cursor=b
    if cursor<p.end: out.append((cursor,p.end,'STUDY_AVAILABLE'))
    return out


def valid(p,layout,policy):
    if not p.windows: return False
    rows=sorted(layout)
    main=[x for x in rows if x[2]=='MAIN_SLEEP']
    if len(main)>math.ceil((p.end-p.start).total_seconds()/(24*3600)): return False
    for i,(a,b,kind) in enumerate(rows):
        if a<p.start or b>p.end-timedelta(minutes=policy['wakeLeadMin']) or b<=a: return False
        if not contains(p.windows,a,b) or (i and rows[i-1][1]>a): return False
        if kind=='NAP' and not any(x<=a and b<=y for x,y in [(at(w['startAt']),at(w['endAt'])) for w in p.raw['constraints']['napWindows']]): return False
    if len({b.astimezone(KST).date() for a,b,k in main})!=len(main): return False
    naps=[x for x in rows if x[2]=='NAP']
    if len({a.astimezone(KST).date() for a,b,k in naps})!=len(naps): return False
    if naps and p.raw['constraints']['napPolicy']!='ALLOWED': return False
    # Wake duration and insufficient sleep are scored in the state/memory
    # simulation, never used to hide the no-sleep or short-sleep comparison.
    return True


def habitual_distance(p,layout):
    clock=p.raw['profile'].get('usualWakeTime') or '07:00';hh,mm=map(int,clock.split(':'));target=hh*60+mm
    distances=[]
    for a,b,k in layout:
        if k=='MAIN_SLEEP':
            local=b.astimezone(KST);d=abs(local.hour*60+local.minute-target);distances.append(min(d,1440-d))
    return sum(distances)+60*sum(k=='NAP' for a,b,k in layout)


def sleep_timing_distance(p, layout):
    """Mean clock distance for both ends of each main sleep."""
    profile = p.raw['profile']
    if not (profile.get('usualBedtime') and profile.get('usualWakeTime')):
        return math.inf

    def distance(moment, clock):
        hh, mm = map(int, clock.split(':'))
        local = moment.astimezone(KST)
        gap = abs(local.hour*60+local.minute-(hh*60+mm))
        return min(gap,1440-gap)

    deviations = [distance(a,profile['usualBedtime']) + distance(b,profile['usualWakeTime'])
                  for a,b,kind in layout if kind=='MAIN_SLEEP']
    return sum(deviations)/(2*len(deviations)) if deviations else math.inf


def build(p,policy):
    durations=sorted(set(policy['primaryDurationsMin']+[min(570,max(60,p.raw['profile']['sleepNeedMin']))]))
    wake=p.raw['profile'].get('usualWakeTime') or '07:00';hh,mm=map(int,wake.split(':'))
    bedtime=p.raw['profile'].get('usualBedtime') if policy.get('habitualTimingRegretTolerance') else None
    if bedtime:
        bh,bm=map(int,bedtime.split(':'))
        usual_duration=(hh*60+mm-bh*60-bm)%1440
        if durations[0]<=usual_duration<=durations[-1]:
            durations=sorted(set(durations+[usual_duration]))
    choices={};finish=p.end-timedelta(minutes=policy['wakeLeadMin'])
    for a,b in p.windows:
        b=min(b,finish)
        for duration in durations:
            if minutes(a,b)<duration: continue
            ends={b,a+timedelta(minutes=duration)}
            for offset in range(5):
                day=(p.start.astimezone(KST)+timedelta(days=offset-1)).replace(hour=hh,minute=mm,second=0,microsecond=0)
                for shift in (-120,-60,0,60,120): ends.add(day+timedelta(minutes=shift))
                if bedtime:
                    bed=day.replace(hour=bh,minute=bm)
                    ends.add(bed+timedelta(minutes=duration))
            for end in ends:
                start=end-timedelta(minutes=duration)
                if a<=start<end<=b:
                    key=end.astimezone(KST).date();choices.setdefault(key,[]).append((start,end,'MAIN_SLEEP'))
    # Beam representatives by duration and timing, not only nominal memory.
    prefixes=[[]]
    for day,options in sorted(choices.items()):
        options=sorted(set(options),key=lambda x:(habitual_distance(p,[x]),abs(minutes(x[0],x[1])-p.raw['profile']['sleepNeedMin']),x[0]))
        representative=[]
        for duration in durations:
            match=next((x for x in options if abs(minutes(x[0],x[1])-duration)<1e-6),None)
            if match: representative.append(match)
        for x in options:
            if x not in representative: representative.append(x)
            if len(representative)>=24: break
        max_main=math.ceil((p.end-p.start).total_seconds()/(24*3600))
        grown=[old+[row] for old in prefixes for row in representative if len(old)<max_main and (not old or old[-1][1]<=row[0])]
        # Preserve empty prefixes to avoid forcing an early future night.
        expanded=prefixes+grown
        ranked=sorted(expanded,key=lambda x:(-len(x),habitual_distance(p,x),sum(abs(minutes(a,b)-p.raw['profile']['sleepNeedMin']) for a,b,k in x)))
        # Keep shorter prefixes as well: a completed early sequence must not
        # evict a sequence that can still choose the final future night.
        prefixes=[]
        for size in range(max_main+1):
            prefixes.extend([x for x in ranked if len(x)==size][:policy['beamWidth']])
        if [] not in prefixes: prefixes.append([])
        # Extra length families keep shorter and longer strategies reachable.
        for duration in durations:
            match=next((x for x in grown if all(abs(minutes(a,b)-duration)<1e-6 for a,b,k in x)),None)
            if match and match not in prefixes: prefixes.append(match)
    pool=[x for x in prefixes if valid(p,x,policy)]
    # Single-night layouts can otherwise disappear during a multi-date beam.
    for options in choices.values():
        pool.extend([x] for x in options if valid(p,[x],policy))
    unique={tuple(x):x for x in pool};pool=list(unique.values())
    def rank(x):
        return (not bool(x),-len(x),habitual_distance(p,x),sum(abs(minutes(a,b)-p.raw['profile']['sleepNeedMin']) for a,b,k in x),tuple(x))
    pool.sort(key=rank)
    # Baseline is a rested-length comparator, not a default winner. Avoid
    # accidentally choosing empty or a 60-minute layout as the baseline.
    baseline=min((x for x in pool if x),key=lambda x:(-len(x),sum(abs(minutes(a,b)-p.raw['profile']['sleepNeedMin']) for a,b,k in x),habitual_distance(p,x),tuple(x)),default=None)
    retained=[]
    if baseline is not None: retained.append(baseline)
    if valid(p,[],policy): retained.append([])
    if baseline and bedtime:
        habitual=min((x for x in pool if len(x)==len(baseline)),
                     key=lambda x:(sleep_timing_distance(p,x),rank(x)))
        if habitual not in retained: retained.append(habitual)
    # Do not reserve half the budget for naps that are disabled. Preserve
    # both sleep-before-study and study-before-sleep for the same duration.
    has_naps=p.raw['constraints']['napPolicy']=='ALLOWED'
    main_limit=max(6,policy['layoutLimit']-12) if has_naps else policy['layoutLimit']
    reservation_durations=sorted(set([durations[0],durations[-1],*durations[::max(1,len(durations)//max(1,main_limit-2))]]))[:main_limit-1]
    if durations[-1] not in reservation_durations: reservation_durations.append(durations[-1])
    for duration in reservation_durations:
        match=next((x for x in pool if any(abs(minutes(a,b)-duration)<1e-6 for a,b,k in x)),None)
        if match and match not in retained and len(retained)<main_limit: retained.append(match)
    for duration in reservation_durations:
        family=[x for x in pool if x and any(abs(minutes(a,b)-duration)<1e-6 for a,b,k in x)]
        if not family:continue
        for match in (min(family,key=lambda x:(x[0][0],tuple(x))),max(family,key=lambda x:(x[0][0],tuple(x)))):
            if match not in retained and len(retained)<main_limit:retained.append(match)
    for x in pool:
        if x not in retained: retained.append(x)
        if len(retained)>=main_limit: break
    if p.raw['constraints']['napPolicy']=='ALLOWED':
        naps=[]
        for window in p.raw['constraints']['napWindows']:
            left,right=at(window['startAt']),at(window['endAt'])
            for duration in policy['napDurationsMin']:
                for start in (left,right-timedelta(minutes=duration)):
                    end=start+timedelta(minutes=duration)
                    if left<=start<end<=right and contains(p.windows,start,end): naps.append((start,end,'NAP'))
        # Nap-only plans compete with primary sleep and no sleep. They use
        # exactly the same explicit location/time permission as combined naps.
        for duration in policy['napDurationsMin']:
            layout=next(([nap] for nap in naps if abs(minutes(nap[0],nap[1])-duration)<1e-6 and valid(p,[nap],policy)),None)
            if layout is not None and layout not in retained: retained.append(layout)
        main_representatives=list(retained)
        for duration in policy['napDurationsMin']:
            found=None
            for nap in naps:
                if abs(minutes(nap[0],nap[1])-duration)>1e-6: continue
                found=next((sorted(main+[nap]) for main in main_representatives if valid(p,sorted(main+[nap]),policy)),None)
                if found: break
            if found and found not in retained and len(retained)<policy['layoutLimit']: retained.append(found)
        # Preserve a repeated daily-nap strategy for multi-day requests rather
        # than silently evaluating only a single nap across all 72 hours.
        for duration in (20,30,60):
            if baseline is None: break
            layout=list(baseline);used_days=set()
            for nap in sorted(naps):
                day=nap[0].astimezone(KST).date()
                if day in used_days or abs(minutes(nap[0],nap[1])-duration)>1e-6: continue
                proposal=sorted(layout+[nap])
                if valid(p,proposal,policy): layout=proposal;used_days.add(day)
            if len(used_days)>1 and layout not in retained and len(retained)<policy['layoutLimit']: retained.append(layout)
        for nap in naps:
            for main in main_representatives:
                layout=sorted(main+[nap])
                if valid(p,layout,policy) and layout not in retained: retained.append(layout)
                if len(retained)>=policy['layoutLimit']: break
            if len(retained)>=policy['layoutLimit']: break
    return [dict(key='candidate-'+str(i),layout=x,timeline=timeline(p,x),strategy=('NO_SLEEP' if not x else 'NAP_ONLY' if all(k=='NAP' for a,b,k in x) else 'MAIN_NAP' if any(k=='NAP' for a,b,k in x) else 'MAIN_ONLY'),distance=habitual_distance(p,x),baseline=x==baseline) for i,x in enumerate(retained)], dict(generatedLayouts=len(pool),assessedLayoutLimit=policy['layoutLimit'],searchScope='FINITE_STRATEGY_PRESERVING_POOL',searchComplete=False,sleepMinimumEnforced=False,wakeLimitEnforced=False)

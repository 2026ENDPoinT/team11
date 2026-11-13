"""Observed versus unknown history, habitual timing, and state initialization."""
from datetime import timedelta
import math
from . import fatigue
from .intervals import KST, merge
from .schemas import at


def circular_time(values):
    if not values: return None
    x=sum(math.cos(v*2*math.pi/1440) for v in values);y=sum(math.sin(v*2*math.pi/1440) for v in values)
    if math.hypot(x,y)<1e-8: return None
    minute=round(math.atan2(y,x)*1440/(2*math.pi))%1440
    return f'{minute//60:02d}:{minute%60:02d}'


def canonical_history(p):
    """Ambiguous overlapping sessions cannot add recovery twice."""
    result=[];ambiguous=0
    for i,(a,b,row) in enumerate(p.history):
        if (i and p.history[i-1][1]>a) or (i+1<len(p.history) and p.history[i+1][0]<b):
            ambiguous+=1;continue
        result.append((a,b,row))
    if ambiguous: p.warnings.append('OVERLAPPING_SLEEP_SESSIONS_EXCLUDED')
    return result


def observed_spans(a,b,row):
    stages=row['stages'];covered=sum((at(x['endAt'])-at(x['startAt'])).total_seconds()/60 for x in stages if x['stage']!='UNKNOWN')
    sleeping=merge([(at(x['startAt']),at(x['endAt'])) for x in stages if x['stage'] in ('LIGHT','DEEP','REM','ASLEEP_UNSPECIFIED')])
    if covered/max(1,(b-a).total_seconds()/60)>=.7:
        total=sum((y-x).total_seconds()/60 for x,y in sleeping)
        if row.get('asleepDurationMin') is None or abs(total-row['asleepDurationMin'])<=15: return sleeping
    if row.get('asleepDurationMin') is not None:
        # A total is observed, its within-session location is an approximation.
        return [(b-timedelta(minutes=row['asleepDurationMin']),b)] if row['asleepDurationMin'] else []
    if sleeping: return sleeping
    # Opportunity/unknown is not an observed all-asleep interval.
    return []


def initialize(p):
    rows=canonical_history(p);p.cache['history']=rows
    profile=p.raw['profile']
    main=[(a,b,r) for a,b,r in rows if r['sessionKind']!='NAP' and (b-a).total_seconds()>=3*3600]
    if profile.get('usualBedtime') is None and main:
        profile['usualBedtime']=circular_time([a.astimezone(KST).hour*60+a.astimezone(KST).minute for a,b,r in main])
        profile['usualWakeTime']=circular_time([b.astimezone(KST).hour*60+b.astimezone(KST).minute for a,b,r in main])
        p.warnings.append('HABITUAL_TIMING_DERIVED_FROM_HISTORY')
    p.cache['lastSleepEnd']=max((b for a,b,r in rows if observed_spans(a,b,r)),default=None)
    return rows


def initial_state(p,theta):
    key=('initial',theta['initial'])
    if key in p.cache: return p.cache[key]
    fp,fu=fatigue.rested();fp+=fatigue.REFERENCE*theta['initial']
    cursor=max(at(p.raw['historyCoverage']['startAt']),p.start-timedelta(days=7))
    known=p.raw['historyCoverage']['state']=='COMPLETE'
    coverage_end=at(p.raw['historyCoverage']['endAt'])
    spans=merge([span for a,b,row in p.cache['history'] for span in observed_spans(a,b,row)])
    points=sorted({cursor,p.start,max(cursor,coverage_end),*[max(cursor,min(p.start,x)) for pair in spans for x in pair]})
    for a,b in zip(points,points[1:]):
        asleep=any(x<=a and b<=y for x,y in spans)
        hours=(b-a).total_seconds()/3600
        if asleep or (known and b<=coverage_end): fp,fu=fatigue.step(fp,fu,hours,asleep)
        else:
            # Partial gaps span both reference and wake continuations; never
            # assert that an unrecorded night was a measured all-nighter.
            wp,wu=fatigue.step(fp,fu,min(hours,24),False);rp,ru=fatigue.rested();alpha=theta['initial']
            fp,fu=rp*(1-alpha)+wp*alpha,ru*(1-alpha)+wu*alpha
    p.cache[key]=(fp,fu)
    return fp,fu

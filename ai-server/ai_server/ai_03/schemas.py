"""Strict minimal-input contract; defaults never mutate caller-owned objects."""
from copy import deepcopy
from dataclasses import dataclass, field
from datetime import datetime, timedelta
import math
import re
from ..errors import APIError
from .intervals import UTC, canonical_hash, free, merge

CONTRACT = 'EXAM_SLEEP_MINIMAL_V1'
READINESS = ('STARTING', 'STARTING_HEAVY', 'SEEN_NOT_RECALLABLE', 'PATCHING_GAPS', 'FINAL_REVIEW')
NATURES = ('RECALL', 'MIXED', 'PROBLEM_SOLVING')
STAGES = ('AWAKE', 'LIGHT', 'DEEP', 'REM', 'ASLEEP_UNSPECIFIED', 'UNKNOWN')


def fail(path, message, code='INVALID_INPUT', status=400):
    raise APIError(code, message, status, field=path)


def obj(value, path, allowed, required=()):
    if not isinstance(value, dict): fail(path, '객체가 필요합니다.')
    for key in value:
        if key not in allowed: fail(path+'.'+key, '알 수 없는 필드입니다.')
    for key in required:
        if key not in value: fail(path+'.'+key, '필수 필드입니다.')
    return value


def number(value, path, low, high, integer=False):
    if type(value) not in ((int,) if integer else (int, float)) or not low <= value <= high or not math.isfinite(value):
        fail(path, f'{low}–{high} 범위의 유한한 숫자가 필요합니다.')
    return value


def enum(value, path, choices):
    if not isinstance(value, str) or value not in choices: fail(path, '지원하는 값: '+', '.join(choices))
    return value


def text(value, path):
    if not isinstance(value, str) or not 1 <= len(value) <= 128: fail(path, '1–128자 문자열이 필요합니다.')
    return value


def array(value, path, limit):
    if not isinstance(value, list) or len(value) > limit: fail(path, f'최대 {limit}개 배열이 필요합니다.')
    return value


def at(value, path='time'):
    try:
        if not isinstance(value, str) or not re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?(?:Z|[+-]\d\d:\d\d)', value): raise ValueError
        result = datetime.fromisoformat(value.replace('Z', '+00:00'))
        if not 2000 <= result.year <= 2100 or result.utcoffset() is None: raise ValueError
        return result.astimezone(UTC)
    except (ValueError, OverflowError): fail(path, 'offset 포함 RFC3339 시각이 필요합니다.')


def interval(row, path):
    a, b = at(row.get('startAt'), path+'.startAt'), at(row.get('endAt'), path+'.endAt')
    if b <= a: fail(path, 'endAt은 startAt 이후여야 합니다.')
    return a, b


@dataclass
class Prepared:
    raw: dict
    start: datetime
    end: datetime
    fixed: list
    windows: list
    history: list
    warnings: list
    input_hash: str
    cache: dict = field(default_factory=dict)


def prepare(payload):
    fields = {'contractVersion','asOfAt','timezone','populationScope','sourceRevision','target','preparation','profile',
              'historyCoverage','sleepHistory','fixedIntervals','constraints','optionalObservations','randomSeed'}
    obj(payload, '$', fields, fields-{'profile','optionalObservations','randomSeed'}); r=deepcopy(payload)
    enum(r['contractVersion'],'contractVersion',[CONTRACT]);enum(r['timezone'],'timezone',['Asia/Seoul'])
    enum(r['populationScope'],'populationScope',['ADULT_VERIFIED','ADULT_UNVERIFIED']);start=at(r['asOfAt'],'asOfAt')
    number(r['sourceRevision'],'sourceRevision',0,2**53-1,True)
    target=obj(r['target'],'target',{'key','startsAt','examNature','durationMin'},['key','startsAt','examNature'])
    text(target['key'],'target.key');enum(target['examNature'],'target.examNature',NATURES);end=at(target['startsAt'],'target.startsAt')
    if not 0<(end-start).total_seconds()<=72*3600: fail('target.startsAt','시험은 현재 이후 72시간 이내여야 합니다.','UNSUPPORTED_HORIZON',422)
    number(target.setdefault('durationMin',60),'target.durationMin',1,480)
    prep=obj(r['preparation'],'preparation',{'state','assessedAt','wordingVersion','remainingStudyHours'},['state','assessedAt','wordingVersion'])
    enum(prep['state'],'preparation.state',READINESS);enum(prep['wordingVersion'],'preparation.wordingVersion',['readiness-copy-1','readiness-copy-2'])
    estimate=prep.get('remainingStudyHours')
    if estimate is not None:
        obj(estimate,'preparation.remainingStudyHours',{'lower','upper','source'},['lower','upper','source'])
        number(estimate['lower'],'preparation.remainingStudyHours.lower',.1,200)
        number(estimate['upper'],'preparation.remainingStudyHours.upper',estimate['lower'],200)
        enum(estimate['source'],'preparation.remainingStudyHours.source',['BACKEND_ESTIMATE','PRIOR_SESSION_ESTIMATE'])
    assessed=at(prep['assessedAt'],'preparation.assessedAt')
    if assessed>start: fail('preparation.assessedAt','미래 선택 시각입니다.')
    profile=r.setdefault('profile',{})
    obj(profile,'profile',{'usualBedtime','usualWakeTime','sleepNeedMin','sleepLatencyMin','minMainSleepOpportunityMin','commuteMin','prepMin','bufferMin','provenance'})
    for key,default,low,high in [('sleepNeedMin',480,0,720),('sleepLatencyMin',15,0,120),('minMainSleepOpportunityMin',0,0,480),('commuteMin',0,0,240),('prepMin',0,0,240),('bufferMin',0,0,120)]:
        if profile.get(key) is None: profile[key]=default
        number(profile[key],'profile.'+key,low,high)
    for key in ('usualBedtime','usualWakeTime'):
        value=profile.get(key)
        if value is not None and (not isinstance(value,str) or not re.fullmatch(r'(?:[01]\d|2[0-3]):[0-5]\d',value)): fail('profile.'+key,'HH:mm 형식이 필요합니다.')
    if not profile.get('usualBedtime') or not profile.get('usualWakeTime'): profile['usualBedtime']=profile['usualWakeTime']=None
    provenance=profile.setdefault('provenance',{});obj(provenance,'profile.provenance',{'habitualNights','basis','sleepNeedBasis'})
    if 'habitualNights' in provenance: number(provenance['habitualNights'],'profile.provenance.habitualNights',0,100,True)
    for key in ('basis','sleepNeedBasis'):
        if key in provenance: text(provenance[key],'profile.provenance.'+key)
    coverage=obj(r['historyCoverage'],'historyCoverage',{'startAt','endAt','state','lastSyncedAt'},['startAt','endAt','state']);lo,hi=interval(coverage,'historyCoverage')
    if hi>start or lo<start-timedelta(days=28): fail('historyCoverage','현재 이전 28일 이내 관측 범위가 필요합니다.')
    enum(coverage['state'],'historyCoverage.state',['COMPLETE','PARTIAL','NONE'])
    if coverage.get('lastSyncedAt') is not None and at(coverage['lastSyncedAt'],'historyCoverage.lastSyncedAt')>start: fail('historyCoverage.lastSyncedAt','미래 동기화 시각입니다.')
    history=[];keys=set();stage_count=0
    for i,row in enumerate(array(r['sleepHistory'],'sleepHistory',100)):
        path=f'sleepHistory[{i}]'
        obj(row,path,{'key','startAt','endAt','basis','asleepDurationMin','sessionKind','deviceFamily','stageCoverage','stages'},['key','startAt','endAt','basis'])
        text(row['key'],path+'.key')
        if row['key'] in keys: fail(path+'.key','중복 key입니다.')
        keys.add(row['key']);a,b=interval(row,path)
        if a<lo or b>hi: fail(path,'관측 범위를 벗어났습니다.')
        enum(row['basis'],path+'.basis',['ASLEEP_ESTIMATE','OPPORTUNITY','SESSION_SPAN','UNKNOWN'])
        if row.get('asleepDurationMin') is not None:
            number(row['asleepDurationMin'],path+'.asleepDurationMin',0,(b-a).total_seconds()/60)
            if row['basis']!='ASLEEP_ESTIMATE': fail(path+'.basis','실수면 총량은 ASLEEP_ESTIMATE여야 합니다.')
        enum(row.setdefault('sessionKind','UNKNOWN'),path+'.sessionKind',['MAIN','NAP','UNKNOWN'])
        enum(row.setdefault('deviceFamily','UNKNOWN'),path+'.deviceFamily',['APPLE_WATCH','GALAXY_WATCH','UNKNOWN','MANUAL'])
        if row.get('stageCoverage') is not None: number(row['stageCoverage'],path+'.stageCoverage',0,1)
        previous=a;stages=array(row.setdefault('stages',[]),path+'.stages',10000);stage_count+=len(stages)
        if stage_count>10000: fail('sleepHistory','단계 전체는 최대 10000개입니다.')
        for j,stage in enumerate(stages):
            sp=path+f'.stages[{j}]';obj(stage,sp,{'startAt','endAt','stage'},['startAt','endAt','stage']);x,y=interval(stage,sp);enum(stage['stage'],sp+'.stage',STAGES)
            if x<a or y>b or x<previous: fail(sp,'단계는 세션 안에서 겹침 없이 시간순이어야 합니다.')
            previous=y
        history.append((a,b,row))
    if coverage['state']=='NONE' and history: fail('sleepHistory','NONE은 빈 관측 목록이어야 합니다.')
    fixed=[];keys=set()
    for i,row in enumerate(array(r['fixedIntervals'],'fixedIntervals',500)):
        path=f'fixedIntervals[{i}]';obj(row,path,{'key','startAt','endAt','kind'},['key','startAt','endAt','kind']);text(row['key'],path+'.key')
        if row['key'] in keys: fail(path+'.key','중복 key입니다.')
        keys.add(row['key']);a,b=interval(row,path);enum(row['kind'],path+'.kind',['OBLIGATION','PREPARATION_TRAVEL','OTHER_EXAM','MEAL','REST'])
        if max(a,start)<min(b,end): fixed.append((max(a,start),min(b,end)))
    con=obj(r['constraints'],'constraints',{'napPolicy','napWindows','policyVersion','experimentalSplit','calendarState'},['napPolicy','policyVersion','calendarState'])
    enum(con['napPolicy'],'constraints.napPolicy',['DISABLED','OPTIONAL_ONLY','ALLOWED']);enum(con['policyVersion'],'constraints.policyVersion',['minimal-balanced-1','workload-balanced-2']);enum(con['calendarState'],'constraints.calendarState',['SYNCED','PARTIAL','UNKNOWN'])
    if type(con.setdefault('experimentalSplit',False)) is not bool: fail('constraints.experimentalSplit','boolean이 필요합니다.')
    if con['experimentalSplit']: fail('constraints.experimentalSplit','복잡한 다상수면은 지원하지 않습니다.','UNSUPPORTED_POLICY',422)
    for i,row in enumerate(array(con.setdefault('napWindows',[]),'constraints.napWindows',30)):
        path=f'constraints.napWindows[{i}]';obj(row,path,{'startAt','endAt'},['startAt','endAt']);a,b=interval(row,path)
        if a<start or b>end: fail(path,'요청 범위 안이어야 합니다.')
    obs=r.setdefault('optionalObservations',{'caffeineState':'UNKNOWN','caffeineEvents':[]});obj(obs,'optionalObservations',{'caffeineState','caffeineEvents'},['caffeineState','caffeineEvents'])
    enum(obs['caffeineState'],'optionalObservations.caffeineState',['UNKNOWN','PARTIAL','COMPLETE'])
    for i,row in enumerate(array(obs['caffeineEvents'],'optionalObservations.caffeineEvents',100)):
        path=f'optionalObservations.caffeineEvents[{i}]';obj(row,path,{'at','mg'},['at','mg']);t=at(row['at'],path+'.at');number(row['mg'],path+'.mg',0,1000)
        if not start-timedelta(days=2)<=t<=start: fail(path+'.at','과거 48시간 이내여야 합니다.')
    number(r.setdefault('randomSeed',20261113),'randomSeed',0,2**32-1,True)
    warnings=['TARGET_EFFECT_NOT_VALIDATED','ENGINEERING_RANGES_NOT_PROBABILITIES','AGE_BAND_UNVERIFIED']
    if profile['minMainSleepOpportunityMin']:
        warnings.append('LEGACY_SLEEP_MINIMUM_IGNORED')
    if con['policyVersion']=='minimal-balanced-1': warnings.append('LEGACY_POLICY_REPLACED')
    if estimate is None: warnings.append('WORKLOAD_NOT_MEASURED')
    if coverage['state']!='COMPLETE' or hi<start: warnings.append('PARTIAL_SLEEP_HISTORY')
    if r['populationScope']=='ADULT_UNVERIFIED': warnings.append('ADULT_STATUS_UNVERIFIED_BASELINE_ONLY')
    if start-assessed>timedelta(hours=6):
        warnings.append('PREPARATION_STATE_STALE')
        if estimate is not None: warnings.append('STALE_WORKLOAD_ESTIMATE_IGNORED')
    if con['calendarState']!='SYNCED': warnings.append('CALENDAR_INCOMPLETE')
    if any(x['kind']=='OTHER_EXAM' for x in r['fixedIntervals']): warnings.append('OTHER_EXAMS_NOT_OPTIMIZED')
    fixed=merge(fixed)
    return Prepared(r,start,end,fixed,free(start,end,fixed),sorted(history,key=lambda x:x[:2]),warnings,canonical_hash(r))

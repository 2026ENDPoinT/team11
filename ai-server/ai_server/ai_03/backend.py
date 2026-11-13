"""Pure Spring snapshot adapter. Does not fetch, persist, or call external APIs."""
from copy import deepcopy
from datetime import datetime, timedelta
import hashlib
from .intervals import KST, UTC, wire, merge, free
from .observations import circular_time
from .schemas import obj, array, at, enum, number, text, fail, prepare, CONTRACT, READINESS, NATURES

BACKEND_CONTRACT='EXAM_SLEEP_BACKEND_V1'
WEEKDAYS=('MON','TUE','WED','THU','FRI','SAT','SUN')
MAPPING={'CORE':'LIGHT','ASLEEPCORE':'LIGHT','LIGHT':'LIGHT','DEEP':'DEEP','ASLEEPDEEP':'DEEP','REM':'REM','ASLEEPREM':'REM',
         'AWAKE':'AWAKE','WAKE':'AWAKE','AWAKE_IN_BED':'AWAKE','OUT_OF_BED':'AWAKE','SLEEPING':'ASLEEP_UNSPECIFIED',
         'ASLEEPUNSPECIFIED':'ASLEEP_UNSPECIFIED','ASLEEP':'ASLEEP_UNSPECIFIED','ASLEEP_UNSPECIFIED':'ASLEEP_UNSPECIFIED','UNKNOWN':'UNKNOWN'}


def key(value): return 'x-'+hashlib.sha256(str(value).encode()).hexdigest()[:24]


def clock_date(day,value,path):
    if not isinstance(value,str): fail(path,'HH:mm 문자열이 필요합니다.')
    try: return datetime.fromisoformat(day.isoformat()+'T'+value).replace(tzinfo=KST).astimezone(UTC)
    except ValueError: fail(path,'HH:mm 시각이 올바르지 않습니다.')


def expand_events(events,start,end,exceptions):
    result=[]
    for i,event in enumerate(events):
        path=f'events[{i}]'
        obj(event,path,{'eventId','type','startAt','endAt','allDay','repeat','occurrenceKey'},['eventId','type','startAt'])
        number(event['eventId'],path+'.eventId',1,2**53-1,True)
        enum(event['type'],path+'.type',['EXAM','ASSIGNMENT','PERSONAL','OTHER','CLASS'])
        if type(event.get('allDay',False)) is not bool: fail(path+'.allDay','boolean이 필요합니다.')
        if event.get('allDay'): continue
        first=at(event['startAt'],path+'.startAt')
        finish=at(event['endAt'],path+'.endAt') if event.get('endAt') else first+timedelta(hours=1)
        if finish<=first: fail(path,'일정 종료가 시작 이전입니다.')
        rep=event.get('repeat',{'freq':'NONE','interval':1,'until':None})
        obj(rep,path+'.repeat',{'freq','interval','until'},['freq'])
        enum(rep['freq'],path+'.repeat.freq',['NONE','DAILY','WEEKLY']);interval=number(rep.get('interval',1),path+'.repeat.interval',1,52,True)
        increment=timedelta(days=(7 if rep['freq']=='WEEKLY' else 1)*interval)
        dates=[first]
        if rep['freq']!='NONE':
            # Jump close to the bounded request instead of expanding years.
            steps=max(0,int((start-first).total_seconds()//increment.total_seconds())-1)
            dates=[];cursor=first+increment*steps
            for _ in range(80):
                if cursor>end+timedelta(days=1): break
                if rep.get('until'):
                    try: until=datetime.fromisoformat(rep['until']).date()
                    except (TypeError,ValueError): fail(path+'.repeat.until','yyyy-MM-dd가 필요합니다.')
                    if cursor.astimezone(KST).date()>until: break
                dates.append(cursor);cursor+=increment
        for a in dates:
            occurrence=event.get('occurrenceKey') or ('SINGLE' if rep['freq']=='NONE' else a.astimezone(KST).date().isoformat())
            b=a+(finish-first);original=a.astimezone(KST).date().isoformat()
            override=next((x for x in exceptions if x['eventId']==event['eventId'] and x['occurrenceDate']==original),None)
            if override:
                if override.get('cancelled'): continue
                a=at(override.get('startAt') or wire(a));b=at(override.get('endAt') or wire(b))
                if b<=a: fail('eventExceptions','수정 일정의 종료가 시작 이전입니다.')
            if max(a,start)<min(b,end) or start<a<=end:
                result.append(dict(eventId=event['eventId'],occurrenceKey=occurrence,start=a,end=b,type=event['type']))
    return result


def build_request(payload):
    fields={'contractVersion','asOfAt','sourceRevision','populationScope','selection','targetEventId','targetOccurrenceKey',
            'profile','events','eventExceptions','timetableEntries','sleepLogs','napLogs','historyCoverage','calendarState','includeNaps','napWindows','randomSeed'}
    obj(payload,'$',fields,['contractVersion','asOfAt','sourceRevision','selection','events','sleepLogs','historyCoverage'])
    r=deepcopy(payload);enum(r['contractVersion'],'contractVersion',[BACKEND_CONTRACT]);now=at(r['asOfAt'],'asOfAt');end=now+timedelta(hours=72)
    selection=obj(r['selection'],'selection',{'preparationState','examNature','assessedAt','wordingVersion','remainingStudyHours'},['preparationState','examNature','assessedAt'])
    enum(selection['preparationState'],'selection.preparationState',READINESS);enum(selection['examNature'],'selection.examNature',NATURES)
    exceptions=array(r.get('eventExceptions',[]),'eventExceptions',500)
    for i,x in enumerate(exceptions):
        obj(x,f'eventExceptions[{i}]',{'eventId','occurrenceDate','cancelled','startAt','endAt'},['eventId','occurrenceDate'])
        number(x['eventId'],f'eventExceptions[{i}].eventId',1,2**53-1,True)
        if type(x.get('cancelled',False)) is not bool: fail('eventExceptions.cancelled','boolean이 필요합니다.')
    events=expand_events(array(r['events'],'events',500),now,end,exceptions)
    exams=[x for x in events if x['type']=='EXAM' and x['start']>now]
    if r.get('targetEventId') is not None:
        number(r['targetEventId'],'targetEventId',1,2**53-1,True);exams=[x for x in exams if x['eventId']==r['targetEventId']]
    if r.get('targetOccurrenceKey') is not None: exams=[x for x in exams if x['occurrenceKey']==r['targetOccurrenceKey']]
    if not exams: fail('events','72시간 이내 시각이 있는 시험이 없습니다.','NO_UPCOMING_TIMED_EXAM',422)
    target=min(exams,key=lambda x:(x['start'],x['end'],x['eventId'],x['occurrenceKey']));end=target['start']
    profile=r.get('profile',{})
    obj(profile,'profile',{'sleepNeedMin','sleepLatencyMin','minSleepMin','commuteMin','prepMin','prepMinEstimate','bufferMin','usualBedtime','usualWakeTime'})
    mapped={k:v for k,v in profile.items() if k not in ('minSleepMin','prepMinEstimate')}
    mapped['minMainSleepOpportunityMin']=profile.get('minSleepMin',0)
    if profile.get('prepMinEstimate') is not None: mapped['prepMin']=profile['prepMinEstimate']
    fixed=[]
    for i,event in enumerate(events):
        if event is target: continue
        if max(now,event['start'])<min(end,event['end']):
            fixed.append(dict(key=key(('event',event['eventId'],event['occurrenceKey'])),startAt=wire(max(now,event['start'])),endAt=wire(min(end,event['end'])),kind='OTHER_EXAM' if event['type']=='EXAM' else 'OBLIGATION'))
    for i,row in enumerate(array(r.get('timetableEntries',[]),'timetableEntries',100)):
        path=f'timetableEntries[{i}]';obj(row,path,{'entryId','dayOfWeek','startTime','endTime','semesterStartDate','semesterEndDate'},['entryId','dayOfWeek','startTime','endTime'])
        enum(row['dayOfWeek'],path+'.dayOfWeek',WEEKDAYS);number(row['entryId'],path+'.entryId',1,2**53-1,True)
        for offset in range(4):
            day=now.astimezone(KST).date()+timedelta(days=offset)
            if WEEKDAYS[day.weekday()]!=row['dayOfWeek']: continue
            if row.get('semesterStartDate') and day.isoformat()<row['semesterStartDate']: continue
            if row.get('semesterEndDate') and day.isoformat()>row['semesterEndDate']: continue
            a=clock_date(day,row['startTime'],path+'.startTime');b=clock_date(day,row['endTime'],path+'.endTime')
            if b<=a: b+=timedelta(days=1)
            if max(a,now)<min(b,end): fixed.append(dict(key=key(('class',row['entryId'],str(day))),startAt=wire(max(a,now)),endAt=wire(min(b,end)),kind='OBLIGATION'))
    # Preparation/travel is reserved here exactly once; the engine consumes
    # fixedIntervals and does not reserve profile minutes a second time.
    reserve=0
    for field in ('commuteMin','prepMin','bufferMin'):
        value=mapped.get(field) or 0;number(value,'profile.'+field,0,240);reserve+=value
    if reserve:
        fixed.append(dict(key='exam-preparation-travel',startAt=wire(max(now,end-timedelta(minutes=reserve))),endAt=wire(end),kind='PREPARATION_TRAVEL'))
    coverage=deepcopy(r['historyCoverage']);sessions=[];seen=set();allrows=[]
    for i,row in enumerate(array(r['sleepLogs'],'sleepLogs',200)):
        path=f'sleepLogs[{i}]'
        obj(row,path,{'sleepLogId','bedAt','wakeAt','durationMin','durationBasis','provider','externalId','originApp','sourceUpdatedAt','deletedAt','source','deviceFamily','sessionKind','stages'},['sleepLogId','bedAt','wakeAt'])
        number(row['sleepLogId'],path+'.sleepLogId',1,2**53-1,True)
        for field in ('provider','externalId','originApp','source','deviceFamily','sessionKind'):
            if row.get(field) is not None: text(row[field],path+'.'+field)
        if row['wakeAt'] is None: continue
        a,b=at(row['bedAt'],path+'.bedAt'),at(row['wakeAt'],path+'.wakeAt')
        if b<=a: fail(path,'수면 종료가 시작 이전입니다.')
        identity=(row.get('provider'),row.get('originApp'),row.get('externalId') or str(row['sleepLogId']))
        update=at(row.get('sourceUpdatedAt') or row['wakeAt'])
        if update>now: fail(path+'.sourceUpdatedAt','미래 수정 시각입니다.')
        if row.get('deletedAt') and at(row['deletedAt'])>now: fail(path+'.deletedAt','미래 삭제 시각입니다.')
        allrows.append((identity,update,a,b,row))
    latest={}
    for identity,update,a,b,row in allrows:
        if identity not in latest or update>latest[identity][0]: latest[identity]=(update,a,b,row)
    # Prefer wearable observations over manual/nap duplicates, retaining one
    # canonical view without changing the caller's records or claiming a merge.
    rows=sorted(latest.values(),key=lambda x:(x[3].get('source')!='WEARABLE',-(x[2]-x[1]).total_seconds(),x[1]))
    selected=[]
    for update,a,b,row in rows:
        if row.get('deletedAt'): continue
        if any(max(a,x)<min(b,y) for x,y,_ in selected): continue
        selected.append((a,b,row))
    for a,b,row in sorted(selected,key=lambda x:x[0]):
        stages=[]
        for j,s in enumerate(array(row.get('stages',[]),'sleepLogs.stages',10000)):
            obj(s,'sleepLogs.stages',{'stage','startAt','endAt'},['stage','startAt','endAt'])
            native=text(s['stage'],'sleepLogs.stages.stage').upper()
            if native=='INBED': continue
            stages.append(dict(startAt=s['startAt'],endAt=s['endAt'],stage=MAPPING.get(native,'UNKNOWN')))
        stages.sort(key=lambda x:at(x['startAt']))
        basis=row.get('durationBasis') or 'UNKNOWN'
        duration=row.get('durationMin') if basis=='ASLEEP_ESTIMATE' else None
        sessions.append(dict(key=key(('sleep',row['sleepLogId'])),startAt=wire(a),endAt=wire(b),basis=basis,asleepDurationMin=duration,
                             sessionKind=row.get('sessionKind','UNKNOWN'),deviceFamily=row.get('deviceFamily','UNKNOWN'),stages=stages))
    for row in array(r.get('napLogs',[]),'napLogs',100):
        obj(row,'napLogs',{'napLogId','startAt','endAt','durationMin','canonicalSleepLogId'},['napLogId','startAt','endAt'])
        a,b=at(row['startAt']),at(row['endAt'])
        if any(max(a,x)<min(b,y) for x,y,_ in selected): continue
        sessions.append(dict(key=key(('nap',row['napLogId'])),startAt=wire(a),endAt=wire(b),basis='OPPORTUNITY',sessionKind='NAP',deviceFamily='MANUAL',stages=[]))
    if len(sessions)>100: fail('sleepLogs','정규화 후 100개 이하로 분할 전송하세요.')
    nap_windows=r.get('napWindows',[])
    if type(r.get('includeNaps',False)) is not bool: fail('includeNaps','boolean이 필요합니다.')
    nap_policy='ALLOWED' if r.get('includeNaps') and nap_windows else 'OPTIONAL_ONLY' if r.get('includeNaps') else 'DISABLED'
    request=dict(contractVersion=CONTRACT,asOfAt=r['asOfAt'],timezone='Asia/Seoul',populationScope=r.get('populationScope','ADULT_UNVERIFIED'),sourceRevision=r['sourceRevision'],
                 target={'key':key(('exam',target['eventId'],target['occurrenceKey'])),'startsAt':wire(end),'examNature':selection['examNature'],
                         'durationMin':min(480,max(1,(target['end']-target['start']).total_seconds()/60))},
                 preparation={'state':selection['preparationState'],'assessedAt':selection['assessedAt'],'wordingVersion':selection.get('wordingVersion','readiness-copy-1')},
                 profile=mapped,historyCoverage=coverage,sleepHistory=sessions,fixedIntervals=fixed,
                 constraints={'napPolicy':nap_policy,'napWindows':nap_windows,'policyVersion':'workload-balanced-2','calendarState':r.get('calendarState','UNKNOWN'),'experimentalSplit':False},randomSeed=r.get('randomSeed',20261113))
    if selection.get('remainingStudyHours') is not None:
        request['preparation']['remainingStudyHours']=deepcopy(selection['remainingStudyHours'])
    prepare(request)  # Validate the adapter output through the same strict DTO.
    return request


def calculate_backend(payload):
    from .service import calculate
    return calculate(build_request(payload))

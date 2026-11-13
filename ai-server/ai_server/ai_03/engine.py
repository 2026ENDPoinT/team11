"""Source-anchored memory comparison under explicit uncalibrated assumptions."""
from datetime import timedelta
from functools import lru_cache
import math
from . import fatigue
from .observations import initial_state, observed_spans
from .intervals import KST
from .schemas import at


def shifted(p,shift):
    if p<=0: return 0.
    if p>=1: p=1-1e-12
    z=math.log(p)-math.log1p(-p)+shift
    return 1/(1+math.exp(-min(700,z))) if z>=0 else math.exp(max(-700,z))/(1+math.exp(max(-700,z)))


def harm(cells,stage,severity,scale):
    a=max(0,cells[stage+'_Restriction']['mean']);b=max(0,cells[stage+'_Deprivation']['mean'])
    if a>b: a=b=(a+b)/2
    value=a*severity/.5 if severity<=.5 else a+(b-a)*(severity-.5)/.5
    if severity>1: value=b*severity
    return value*scale*math.pi/math.sqrt(3)


@lru_cache(maxsize=1440)
def reference(minute):
    elapsed=minute/60
    return fatigue.step(*fatigue.rested(),elapsed,False)[0] if elapsed<=16 else fatigue.step(*fatigue.step(*fatigue.rested(),16,False),elapsed-16,True)[0]


def severity(p,fp,t,theta):
    clock=p.raw['profile'].get('usualWakeTime') or '07:00';hh,mm=map(int,clock.split(':'));local=t.astimezone(KST)
    elapsed=(local.hour+local.minute/60-hh-mm/60-theta['phase'])%24
    circ=theta['circadian']*max(0,math.cos(2*math.pi*(local.hour+local.minute/60-5-theta['phase'])/24))
    return max(0.,(fp-reference(int(elapsed*60)%1440))/fatigue.REFERENCE)+circ


def phase_conditions(p, t):
    key = ('phaseConditions', t)
    if key not in p.cache:
        local = t.astimezone(KST)
        hh, mm = map(int, (p.raw['profile'].get('usualWakeTime') or '07:00').split(':'))
        clock = local.hour + local.minute/60
        refs = tuple(reference(int(((clock-hh-mm/60-phase)%24)*60)%1440)
                     for phase in (-2,0,2))
        circadian = tuple(max(0,math.cos(2*math.pi*(clock-5-phase)/24))
                          for phase in (-2,0,2))
        if len(p.cache) < 4000:
            p.cache[key] = refs, circadian
        return refs, circadian
    return p.cache[key]


def recent_sleep_deficit(p,t,planned,theta):
    """Explicit uncertain 24h sleep-dose proxy, not a fitted hourly curve.

    The fast fatigue state recovers rapidly and is not a memory dose model.
    Taking the maximum (not the sum) prevents a short sleep erasing the
    observed dose deficit or stacking two independently fitted impairments.
    Missing history remains uncertain; it is never observed wakefulness.
    """
    left=t-timedelta(hours=24)
    def hours(a,b): return max(0,(min(b,t)-max(a,left)).total_seconds()/3600)
    if 'doseData' not in p.cache:
        history=p.cache['history'];coverage=p.raw['historyCoverage']
        spans=[span for x,y,row in history for span in observed_spans(x,y,row)]
        p.cache['doseData']=(spans,[(a,b) for a,b,row in history],coverage['state']=='COMPLETE',at(coverage['startAt']),at(coverage['endAt']),max(1,p.raw['profile']['sleepNeedMin'])/60)
        p.cache['dosePastAt']={}
    spans,sessions,complete,lo,hi,need=p.cache['doseData']
    cache=p.cache['dosePastAt']
    if t not in cache:
        observed=sum(hours(a,b) for a,b in spans)
        past=max(0,(min(t,p.start)-left).total_seconds()/3600)
        known=(max(0,(min(t,p.start,hi)-max(left,lo)).total_seconds()/3600) if complete else sum(hours(a,b) for a,b in sessions))
        cache[t]=(observed,max(0,past-known))
    actual,unknown=cache[t]
    actual+=sum(hours(a,b) for a,b in planned)
    actual+=unknown*need/24*(1-theta['initial'])
    return max(0,1-actual/need)


def trajectory(p,candidate,theta,step_minutes):
    nap_fails=theta['napFails'] and any(k=='NAP' for a,b,k in candidate['layout'])
    key=('path',candidate['key'],theta['latencyDelta'],theta['initial'],nap_fails,step_minutes)
    if key in p.cache: return p.cache[key]
    sleeps=[];actual=0.;profile=p.raw['profile']
    residual=sum(row['mg']*2**(-max(0,(p.start-at(row['at'])).total_seconds()/3600)/5) for row in p.raw['optionalObservations']['caffeineEvents'])
    for a,b,kind in candidate['layout']:
        latency=profile['sleepLatencyMin']+theta['latencyDelta']
        # A recently awakened person may not immediately resume primary
        # sleep. This uncertain penalty is not a measured circadian diagnosis.
        last=p.cache.get('lastSleepEnd')
        if last and kind=='MAIN_SLEEP':
            awake=max(0,(a-last).total_seconds()/3600)
            latency+=max(0,8-awake)*theta['latencyDelta']/4
        # Recorded caffeine may delay onset; this is an engineering scenario,
        # not a dosage prescription or a clinically calibrated drug model.
        latency+=min(30,residual/10)*2**(-max(0,(a-p.start).total_seconds()/3600)/5)
        x=min(b,a+timedelta(minutes=latency));y=b-timedelta(minutes=min(15,(b-a).total_seconds()/60*.04))
        if kind=='NAP' and theta['napFails']: x=y
        if x<y: sleeps.append((x,y));actual+=(y-x).total_seconds()/60
    fp,fu=initial_state(p,theta);cursor=p.start;path=[];lastwake=None
    boundary={p.start,p.end,*[x for a,b,k in candidate['timeline'] for x in (a,b)],*[x for a,b in sleeps for x in (a,b)]}
    tick=p.start
    while tick<p.end:
        boundary.add(tick);tick+=timedelta(minutes=step_minutes)
    points=sorted(boundary)
    slot=0
    for a,b in zip(points,points[1:]):
        while candidate['timeline'][slot][1]<=a: slot+=1
        kind=candidate['timeline'][slot][2];asleep=any(x<=a and b<=y for x,y in sleeps)
        if any(y==a for x,y in sleeps): lastwake=a
        hours=(b-a).total_seconds()/3600;mid=a+(b-a)/2
        mp,mu=fatigue.step(fp,fu,hours/2,asleep)
        dose=recent_sleep_deficit(p,mid,sleeps,theta)
        refs,circadian=phase_conditions(p,mid)
        severities=tuple(max(dose,max(0.,(mp-ref)/fatigue.REFERENCE)) for ref in refs)
        inertia=math.exp(-(mid-lastwake).total_seconds()/1800) if lastwake is not None else 0.
        path.append((hours,kind,asleep,severities,circadian,inertia));fp,fu=fatigue.step(fp,fu,hours,asleep)
    # Performance is evaluated through the exam, not at its start only.
    exam_half=p.raw['target']['durationMin']/120
    exam_mid=p.end+timedelta(hours=exam_half)
    exam_fp,_=fatigue.step(fp,fu,exam_half,False)
    dose=recent_sleep_deficit(p,exam_mid,sleeps,theta)
    final_sev=tuple(max(dose,severity(p,exam_fp,exam_mid,dict(phase=phase,circadian=0))) for phase in (-2,0,2))
    final_inertia=math.exp(-(p.end-lastwake).total_seconds()/1800) if lastwake is not None else 0.
    result=(path,actual,final_sev,final_inertia)
    # Bound per-request cache without cross-user retained state.
    if len(p.cache)<6000: p.cache[key]=result
    return result


def simulate(p,candidate,theta,artifact,step_minutes=15):
    path,actual,final_values,final_inertia=trajectory(p,candidate,theta,step_minutes)
    phase_index={-2:0,0:1,2:2}[theta['phase']]
    exam_mid=p.end+timedelta(minutes=p.raw['target']['durationMin']/2)
    final_sev=final_values[phase_index]+severity(p,0,exam_mid,dict(phase=theta['phase'],circadian=theta['circadian']))
    old=theta['progress'];new=1-old;oldm=old*theta['recall'];newx=newm=0.;dose_old=dose_new=0.
    oldburden=newburden=0.;cells=artifact['sleepMeta']['cells'];base=theta['encodingRecall'];slope=theta['slope']
    ln2h=math.log(2)/theta['halfLife'];elapsed_study=0.;effective_hours=0.
    for dt,kind,asleep,severities,circadian,inertia in path:
        sev=severities[phase_index]+theta['circadian']*circadian[phase_index]
        decay=math.exp(-ln2h*dt);oldm*=decay;newm*=decay
        # Post-encoding exposure is accumulated once, separately from encoding.
        if not asleep:
            oldburden+=dt*sev*oldm
            newburden+=dt*sev*newm
        if kind!='STUDY_AVAILABLE': continue
        enc=shifted(base,-harm(cells,'Before',sev,theta['transfer'])-.25*inertia)/base
        effective=dt*theta['duty']*enc;effective_hours+=effective
        style=theta['studyStyle'];elapsed_study+=dt
        if style=='NEW_FIRST': newshare=1. if newx<new-1e-9 else 0.
        elif style=='REVIEW_FIRST': newshare=0. if elapsed_study<2 and old>0 else .6
        else: newshare=.6 if newx<new-1e-9 else 0.
        if old==0 and newx<new: newshare=1.
        # Remaining workload is defined in rested focused hours. Impaired
        # encoding needs more wall-clock time; it cannot count as completed
        # work and then be charged again as a second independent deficit.
        exposure=min(new-newx,effective*newshare/theta['tnew'])
        newx+=exposure;newm+=exposure*base
        spare=effective*(1-newshare)
        for isnew,x in ((False,old),(True,newx)):
            if x<=1e-12: continue
            allocation=spare*(.5 if old>0 and newx>0 else 1.)
            if isnew:
                mass=newm;prior=dose_new
            else: mass=oldm;prior=dose_old
            d=allocation/(x*theta['remainingHours']*theta['reviewRatio'])
            before=shifted(base,slope*math.log1p(prior));after=shifted(base,slope*math.log1p(prior+d))
            q=mass/x;q=1-(1-q)*(1-after)/max(1e-12,1-before)
            if isnew: newm=min(x,max(0,x*q));dose_new+=d
            else: oldm=min(x,max(0,x*q));dose_old+=d
    # Composite mode applies the total post-encoding effect once. A separated
    # structural alternative trades retention exposure against final retrieval;
    # it never stacks two independently fitted copies of the same effect.
    total_hours=max(1e-9,(p.end-p.start).total_seconds()/3600)
    recall=0.
    for x,m,burden in ((old,oldm,oldburden),(newx,newm,newburden)):
        if x<=0: continue
        retention_sev=burden/max(x*total_hours,1e-9)
        postsev=.5*retention_sev+.5*final_sev if theta['postMode']=='COMPOSITE' else .8*retention_sev+.2*final_sev
        recall+=x*shifted(m/x,-harm(cells,'After',postsev,theta['transfer'])-.5*final_inertia)
    attention=math.exp(-theta['transfer']*final_sev)*max(0,1-final_inertia)
    utility=(1-theta['omega'])*recall+theta['omega']*attention
    return {'utility':min(1,max(0,utility)),'memoryIndex':min(1,max(0,recall)),'actualSleepMin':actual,
            'effectiveStudyHours':effective_hours,'remainingWorkHours':max(0,theta['remainingHours']-effective_hours),
            'workBudgetCompleted':effective_hours>=theta['remainingHours'],
            'newMaterialCoveredFraction':newx/new if new>1e-9 else 1.,'examStateSeverity':final_sev}

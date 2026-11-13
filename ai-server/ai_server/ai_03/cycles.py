"""Coarse personal semi-Markov reported-stage model, never a PSG claim."""
from collections import Counter, defaultdict
import math
from .intervals import canonical_hash
from .schemas import at

STATES=('AWAKE','LIGHT','DEEP','REM')
PRIOR_MEANS={'AWAKE':10.,'LIGHT':25.,'DEEP':25.,'REM':20.}
PRIOR_TRANS={'AWAKE':{'LIGHT':1.},'LIGHT':{'DEEP':.55,'REM':.35,'AWAKE':.1},'DEEP':{'LIGHT':1.},'REM':{'LIGHT':.8,'AWAKE':.2}}


def fit(p,policy):
    transition=defaultdict(Counter);durations=defaultdict(list);nights=0;families=set();coverage=[]
    for a,b,row in p.cache['history']:
        if row['sessionKind']=='NAP' or (b-a).total_seconds()<3*3600: continue
        stages=[s for s in row['stages'] if s['stage'] in STATES]
        fraction=sum((at(s['endAt'])-at(s['startAt'])).total_seconds() for s in stages)/(b-a).total_seconds()
        if fraction<policy['cycleMinStageCoverage']: continue
        nights+=1;coverage.append(fraction);families.add(row['deviceFamily'])
        for i,s in enumerate(stages):
            durations[s['stage']].append((at(s['endAt'])-at(s['startAt'])).total_seconds()/60)
            if i and stages[i-1]['endAt']==s['startAt']: transition[stages[i-1]['stage']][s['stage']]+=1
    enabled=nights>=policy['cycleMinNights'] and len(families)==1 and families.issubset({'APPLE_WATCH','GALAXY_WATCH'}) and not any('OVERLAPPING' in w for w in p.warnings)
    model={'enabled':enabled,'mode':'PERSONAL_REPORTED_STAGE_SEMIMARKOV' if enabled else 'OFF','eligibleNights':nights,
           'stageCoverageMean':sum(coverage)/len(coverage) if coverage else 0.,'deviceFamilies':sorted(families),
           'priorStatus':'ENGINEERING_COARSE_DURATION_PRIOR_NOT_PSG_TRAINED','transitions':{},'means':{},'durations':{}}
    for state in STATES:
        observed=durations[state];n=len(observed)
        model['means'][state]=(sum(observed)+10*PRIOR_MEANS[state])/(n+10)
        model['durations'][state]=observed
        total=sum(transition[state].values())+10
        model['transitions'][state]={s:(transition[state][s]+10*PRIOR_TRANS[state].get(s,0))/total for s in STATES}
    model['hash']=canonical_hash(model)
    return model


def deep_risk(model,elapsed_min):
    """Duration-conditioned hazards, integrated forward in 5-minute bins."""
    if not model['enabled']: return 0.
    distribution={('LIGHT',0):1.}
    for _ in range(max(0,int(elapsed_min//5))):
        future=defaultdict(float)
        for (state,age),mass in distribution.items():
            samples=model['durations'][state]
            at_risk=sum(x>age for x in samples)
            ending=sum(age<x<=age+5 for x in samples)
            prior_hazard=1-math.exp(-5/model['means'][state])
            hazard=min(1.,max(0.,(ending+10*prior_hazard)/(at_risk+10)))
            future[(state,age+5)]+=mass*(1-hazard)
            for dest,prob in model['transitions'][state].items():
                if prob: future[(dest,0)]+=mass*hazard*prob
        distribution={key:value for key,value in future.items() if value>1e-7}
    return sum(v for (s,age),v in distribution.items() if s=='DEEP')

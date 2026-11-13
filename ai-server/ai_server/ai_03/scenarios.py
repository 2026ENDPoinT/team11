"""Deterministic common engineering scenarios, not personal probabilities."""
import random
MODEL_VERSION='exam-sleep-workload-2.0.0'
RANGES={'STARTING':(0.,.35,.1,.55),'STARTING_HEAVY':(0.,.2,.05,.35),'SEEN_NOT_RECALLABLE':(.45,1.,.1,.5),'PATCHING_GAPS':(.65,1.,.45,.85),'FINAL_REVIEW':(.85,1.,.7,.98)}

def generate(p,artifact,count=256,policy=None):
    if policy is None:
        from .artifacts import load
        policy=load()[1]
    rng=random.Random(p.raw['randomSeed']);lo,hi,ql,qh=RANGES[p.raw['preparation']['state']]
    if 'PREPARATION_STATE_STALE' in p.warnings: lo=max(0,lo-.2);ql=max(0,ql-.2)
    nature=p.raw['target']['examNature'];out=[];permutations=[]
    for _ in range(13):
        values=list(range(count));rng.shuffle(values);permutations.append(values)
    def choose(values,i,j): return values[permutations[j][i]%len(values)]
    for i in range(count):
        out.append(dict(progress=lo+(hi-lo)*choose([0,.5,1],i,0),recall=ql+(qh-ql)*choose([0,.5,1],i,1),
                        tnew=choose([3,8,24,48],i,2),reviewRatio=choose([.25,.5,1],i,3),duty=choose([.35,.6,.8],i,4),
                        halfLife=choose([12,48,96,168],i,5),transfer=choose([0,.5,1,2],i,6),latencyDelta=choose([0,15,30],i,7),
                        phase=choose([-2,0,2],i,8),initial=choose([0,.5,1],i,9),circadian=choose([0,.2],i,10),
                        omega=choose([0] if nature=='RECALL' else [0,.2,.4] if nature=='MIXED' else [.2,.5,.8],i,11),
                        studyStyle=choose(['NEW_FIRST','REVIEW_FIRST','MIXED'],i,12),postMode=('COMPOSITE','SEPARATED')[i%2],napFails=i%8==0,
                        encodingRecall=artifact['learning']['firstTestProbability'],slope=artifact['learning']['reviewLogExposureSlope']))
    estimate=p.raw['preparation'].get('remainingStudyHours')
    if 'PREPARATION_STATE_STALE' in p.warnings: estimate=None
    hours=([estimate['lower'],(estimate['lower']+estimate['upper'])/2,estimate['upper']] if estimate
           else policy['workloadHours'][p.raw['preparation']['state']])
    for i,theta in enumerate(out):
        required=choose(hours,i,2)
        theta['remainingHours']=required
        # Hours represent rested focused work on the remaining material,
        # not wall-clock time, or an observed course/exam-specific rate.
        # Keep total-scope rate consistent with the already-covered fraction.
        theta['tnew']=required/max(.05,1-theta['progress'])
    return out

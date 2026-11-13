"""Minimal-input AI-07: robust sleep strategy selection, no DB/network/LLM."""
from datetime import timedelta
from .artifacts import load
from .candidates import build, habitual_distance, sleep_timing_distance, timeline, valid, minutes
from .cycles import fit as fit_cycles, deep_risk
from .engine import simulate
from .intervals import KST, canonical_hash, free, wire
from .observations import initialize
from .scenarios import MODEL_VERSION, generate
from .schemas import prepare, at

REASONS={
    'BASELINE_WHEN_COMPARISON_UNCERTAIN':'공부와 수면의 이득 차이가 작아 비교 기준 수면안을 골랐어요. 평소와 같은 시각에 잠들 수 있다는 보장은 아니에요.',
    'ROBUST_SLEEP_STUDY_BALANCE':'안 자는 안과 짧게 자는 안까지 비교해, 남은 공부와 시험 중 기억·수행 손실을 함께 고려했어요.',
    'HABITUAL_SLEEP_WITH_WORK_BUDGET':'평소 수면에 가까운 계획으로도 평가한 모든 가정에서 남은 공부 시간을 확보할 수 있어 이 계획을 골랐어요.',
    'HABITUAL_BEDTIME_WITH_SAME_DURATION':'추천한 수면 길이를 유지하면서 일정에 맞는 평소 취침 시각에 가깝게 배치했어요.',
    'EXAM_PREPARATION_RESERVED':'시험 전 준비와 이동 시간을 비워 두었어요.',
    'PERSONAL_STAGE_TIE_BREAK':'비슷한 계획 중 과거 수면 단계 패턴을 참고해 기상을 조금 조정했어요.',
    'NO_FREE_WINDOW':'시험 전 시간이 모두 고정 일정으로 차 있어 수면·공부 계획을 배치할 수 없어요.',
    'WORKLOAD_UNFINISHED':'가정한 남은 분량을 모두 다루기 어려워요. 잠을 더 줄여도 끝낸다고 보장할 수 없어 우선순위를 줄이는 것이 필요해요.',
    'WORKLOAD_UNCERTAIN':'분량·난이도를 정확히 측정하지 않아 공부를 끝낼 수 있는지는 가정에 따라 달라져요.',
    'WORKLOAD_BUDGET_AVAILABLE':'가정한 공부 시간은 확보되지만, 범위를 한 번 다루는 것과 충분히 외우는 것은 달라요.',
    'SHORT_SLEEP_TRADEOFF':'공부 시간을 남기기 위해 수면 기회를 줄인 절충안이에요. 충분한 수면이나 시험 후 회복으로 시험 당시 손실이 없어짐을 뜻하지 않아요.',
    'NO_SLEEP_TRADEOFF':'이 가정들에서는 수면 없는 안이 선택됐지만, 밤샘의 안전성이나 성적 향상을 보장하는 추천은 아니에요.',
    'RECOVERY_CONTEXT':'최근 수면 부족이나 긴 각성을 반영했어요. 짧은 잠으로 누적 피로가 모두 회복되지는 않아요.',
    'SHIFTED_SLEEP_TIMING':'평소 취침 시각과 크게 달라 실제로 그때 잠들기 어려울 수 있어요.',
    'LONG_WAKE_TRADEOFF':'20시간을 넘는 긴 각성이 포함된 절충안이에요. 이 계산만으로 실행 가능성이나 안전성을 판단할 수 없어요.'}


def work_summary(scores):
    done=[x['workBudgetCompleted'] for x in scores]
    return dict(status='ALL_ASSUMPTIONS' if all(done) else 'SOME_ASSUMPTIONS' if any(done) else 'NO_ASSUMPTIONS',
                remainingFocusedHoursRange=dict(min=min(x['remainingWorkHours'] for x in scores),max=max(x['remainingWorkHours'] for x in scores)),
                effectiveStudyHoursRange=dict(min=min(x['effectiveStudyHours'] for x in scores),max=max(x['effectiveStudyHours'] for x in scores)),
                newMaterialCoverageRange=dict(min=min(x['newMaterialCoveredFraction'] for x in scores),max=max(x['newMaterialCoveredFraction'] for x in scores)),
                scope='UNCALIBRATED_WORK_BUDGET_NOT_MASTERY_OR_FINISH_PROBABILITY',scenarioShareIsProbability=False)


def max_scheduled_wake(p,layout):
    cursor=p.cache.get('lastSleepEnd') or p.start;longest=0.
    for a,b,k in sorted(layout):
        longest=max(longest,(a-cursor).total_seconds()/3600);cursor=b
    return max(longest,(p.end+timedelta(minutes=p.raw['target']['durationMin'])-cursor).total_seconds()/3600)


def serialize(candidate,scores=None,regret=None):
    slots=[dict(kind=k,startAt=wire(a),endAt=wire(b)) for a,b,k in candidate['timeline']]
    names={'MAIN_SLEEP':'sleepOpportunityMin','NAP':'sleepOpportunityMin','STUDY_AVAILABLE':'studyAvailableMin','FIXED':'fixedMin','WAKE_BUFFER':'wakeBufferMin','REST':'restMin'}
    totals={name:0. for name in names.values()}
    for a,b,k in candidate['timeline']: totals[names[k]]+=minutes(a,b)
    result=dict(candidateKey=candidate['key'],strategy=candidate['strategy'],cycleMode=candidate.get('cycleMode','OFF'),timeline=slots,totals=totals)
    if scores:
        result['actualSleepMinScenarioRange']={'min':min(x['actualSleepMin'] for x in scores),'max':max(x['actualSleepMin'] for x in scores)}
        result['internalUtilityScenarioRange']={'min':min(x['utility'] for x in scores),'max':max(x['utility'] for x in scores)}
        result['workloadAssessment']=work_summary(scores)
    if regret is not None: result['worstCaseRegret']=regret
    return result


def cycle_variants(p,pool,model,policy):
    if not model['enabled']: return
    extra=[]
    for parent in pool[:4]:
        if not parent['layout']: continue
        last=parent['layout'][-1]
        a,b,k=last
        if k!='MAIN_SLEEP': continue
        current=deep_risk(model,minutes(a,b)-p.raw['profile']['sleepLatencyMin'])
        for shift in (-15,15):
            layout=parent['layout'][:-1]+[(a,b+timedelta(minutes=shift),k)]
            risk=deep_risk(model,minutes(a,layout[-1][1])-p.raw['profile']['sleepLatencyMin'])
            if risk<current and valid(p,layout,policy):
                extra.append(dict(key=parent['key']+'-cycle'+str(shift),layout=layout,timeline=timeline(p,layout),strategy=parent['strategy'],distance=parent['distance'],baseline=False,parentKey=parent['key'],cycleMode='PERSONAL_REPORTED_STAGE_SEMIMARKOV',cycleRisk=risk))
    pool.extend(extra[:6])


def recovery_alternative(p,policy):
    if p.raw['constraints']['napPolicy']!='ALLOWED': return None
    from .schemas import at
    for window in p.raw['constraints']['napWindows']:
        for a,b in p.windows:
            left=max(a,at(window['startAt']));right=min(b,at(window['endAt']),p.end-timedelta(minutes=policy['wakeLeadMin']))
            if minutes(left,right)>=20:
                layout=[(left,min(right,left+timedelta(minutes=30)),'NAP')]
                if valid(p,layout,policy):
                    return serialize(dict(key='limited-recovery',strategy='NAP_ONLY',timeline=timeline(p,layout)))
    return None


def scenario_best(scores):
    return [max(row['utility'] for row in scenario)
            for scenario in zip(*scores.values())]


def relative_regret(utilities, best, floor):
    return max(((top-value)/max(floor,top) for value,top in zip(utilities,best)),default=0.)


def duration_variants(p, seed, pool, scenarios, scores, artifact, policy):
    """Screen local edits, then submit at most two to the full comparison."""
    if seed is None or r_unverified(p):
        return []
    index = next((i for i in reversed(range(len(seed['layout'])))
                  if seed['layout'][i][2] == 'MAIN_SLEEP'), None)
    if index is None:
        return []
    start, end, kind = seed['layout'][index]
    lower, upper = min(policy['primaryDurationsMin']), max(policy['primaryDurationsMin'])
    existing = {tuple(candidate['layout']) for candidate in pool}
    best = scenario_best(scores)
    # Spread screening across the existing scenarios. Full scores still
    # determine whether a finalist is eligible for recommendation.
    long_horizon = (p.end-p.start).total_seconds() > policy['longHorizonHours']*3600
    sample_count = 4 if long_horizon else 16
    if policy.get('stagedEvaluation'):
        sample_count = 2 if long_horizon else 4
    sample = range(0, len(scenarios), max(1, (len(scenarios)+sample_count-1)//sample_count))
    sample_best = [best[i] for i in sample]
    floor = policy['regretUtilityFloor']
    ranked = []

    def screen(edge, offset):
        delta = timedelta(minutes=offset)
        left, right = (start+delta,end) if edge == 'start' else (start,end+delta)
        if not lower <= minutes(left,right) <= upper:
            return
        layout = sorted([*seed['layout'][:index], (left,right,kind), *seed['layout'][index+1:]])
        if tuple(layout) in existing or not valid(p,layout,policy):
            return
        existing.add(tuple(layout))
        candidate = dict(key=f'duration-{edge}-{offset}', layout=layout, timeline=timeline(p,layout),
                         strategy=seed['strategy'], distance=habitual_distance(p,layout), baseline=False)
        utilities = [simulate(p,candidate,scenarios[i],artifact,policy['integrationStepMin'])['utility']
                     for i in sample]
        ranked.append((relative_regret(utilities,sample_best,floor), abs(offset),
                       edge, offset, candidate))

    for edge in ('start','end'):
        for offset in (-15,-10,-5,5,10,15):
            screen(edge,offset)
        # If all coarse edits on this edge are blocked, still try the
        # minute offsets close to the original time.
        coarse = min((row for row in ranked if row[2] == edge),default=None)
        center = coarse[3] if coarse else 0
        for offset in range(max(-15,center-4),min(15,center+4)+1):
            if offset % 5:
                screen(edge,offset)
        # Always cover the minutes adjacent to the original interval.
        for offset in (-1,1):
            screen(edge,offset)
    finalist_count = 1 if long_horizon or policy.get('stagedEvaluation') else 2
    return [row[-1] for row in sorted(ranked)[:finalist_count]]


def habitual_candidate(p, pool, baseline):
    if baseline is None:
        return None
    candidate = min((c for c in pool if c['strategy']=='MAIN_ONLY'
                     and len(c['layout'])==len(baseline['layout']) and not c.get('parentKey')),
                    key=lambda c:sleep_timing_distance(p,c['layout']),default=None)
    if candidate and sleep_timing_distance(p,candidate['layout'])<float('inf'):
        return candidate
    return None


def align_bedtime(p, seed, policy):
    """Find a feasible placement within an hour of the habitual bedtime."""
    bedtime = p.raw['profile'].get('usualBedtime')
    if seed is None or not bedtime or r_unverified(p):
        return None
    hour, minute = map(int, bedtime.split(':'))
    layout = list(seed['layout'])
    wake_deadline = p.end - timedelta(minutes=policy['wakeLeadMin'])
    tolerance = timedelta(hours=1)
    for index, (start, end, kind) in enumerate(seed['layout']):
        if kind != 'MAIN_SLEEP':
            continue
        local = start.astimezone(KST)
        today = local.replace(hour=hour, minute=minute, second=0, microsecond=0)
        target = min((today + timedelta(days=day) for day in (-1, 0, 1)),
                     key=lambda anchor: abs(anchor - start))
        duration = end - start
        others = layout[:index] + layout[index + 1:]
        occupied = [(a, b) for a, b, _ in others]
        placements = []
        for left, right in p.windows:
            for a, b in free(left, min(right, wake_deadline), occupied):
                earliest, latest = max(a, target - tolerance), min(b - duration, target + tolerance)
                if earliest > latest:
                    continue
                shifted = min(max(target, earliest), latest)
                if abs(shifted - target) >= abs(start - target):
                    continue
                slot = (shifted, shifted + duration, kind)
                if valid(p, [*others, slot], policy):
                    placements.append(slot)
        if placements:
            layout[index] = min(placements, key=lambda slot: abs(slot[0] - target))
    return sorted(layout) if layout != seed['layout'] else None


def compare_pool(p,pool,scores,policy,*,prefer_habitual=None):
    best=scenario_best(scores)
    regrets={key:relative_regret([row['utility'] for row in series],best,policy['regretUtilityFloor'])
             for key,series in scores.items()}
    baseline=next((c for c in pool if c['baseline']),None)
    selected=min(pool,key=lambda c:(regrets[c['key']],c['distance'],c['key'])) if pool else None
    tolerance=policy['nearTieTolerance'];assessment='UNCERTAIN';deltas=[]
    if selected and baseline:
        timing_tolerance=policy.get('habitualTimingRegretTolerance',0)
        if timing_tolerance and not r_unverified(p):
            best_regret=regrets[selected['key']]
            main_count=max(1,sum(k=='MAIN_SLEEP' for _,_,k in selected['layout']))
            nearby=[c for c in pool if sum(k=='MAIN_SLEEP' for _,_,k in c['layout'])==main_count
                    and regrets[c['key']]<=best_regret+timing_tolerance
                    and sleep_timing_distance(p,c['layout'])<sleep_timing_distance(p,selected['layout'])]
            if nearby:
                selected=min(nearby,key=lambda c:(sleep_timing_distance(p,c['layout']),regrets[c['key']],c['key']))
        if regrets[baseline['key']]<=min(regrets.values())+tolerance or r_unverified(p): selected=baseline
        else: assessment='SCENARIO_ROBUST_CHOICE'
        # Cycle variants are eligible only within their parent's near-tie and
        # the global regret tolerance; a stage estimate cannot change strategy.
        variations=[c for c in pool if c.get('parentKey')==selected['key'] and regrets[c['key']]<=regrets[selected['key']]+tolerance and min(a['utility']-b['utility'] for a,b in zip(scores[c['key']],scores[selected['key']]))>=-tolerance]
        if variations and not r_unverified(p): selected=min(variations,key=lambda c:(c['cycleRisk'],regrets[c['key']]))
        if prefer_habitual is not None:
            selected=prefer_habitual
            assessment='UNCERTAIN'
        deltas=[a['utility']-b['utility'] for a,b in zip(scores[selected['key']],scores[baseline['key']])]
        if min(deltas)<=0<=max(deltas): assessment='UNCERTAIN'
    return selected, baseline, regrets, assessment, deltas


def evaluate_pool(p, pool, scenarios, artifact, policy):
    step = policy['integrationStepMin']
    if not policy.get('stagedEvaluation'):
        return pool, scenarios, {c['key']:[simulate(p,c,t,artifact,step) for t in scenarios] for c in pool}

    long_horizon = (p.end-p.start).total_seconds() > policy['longHorizonHours']*3600
    screen_count, final_count, limit = (16,256,3) if long_horizon else (24,256,4)
    # Keep the scenario generator and seed fixed. Every finalist gets the same
    # extended sample, including all its screening scores.
    scenarios = scenarios[:final_count]
    screening = scenarios[:screen_count]
    scores = {c['key']:[simulate(p,c,t,artifact,step) for t in screening] for c in pool}
    _, baseline, regrets, _, _ = compare_pool(p,pool,scores,policy)
    ranked = sorted(pool,key=lambda c:(regrets[c['key']],c['distance'],c['key']))
    by_key = {c['key']:c for c in pool}
    finalists = [baseline] if baseline else []
    if baseline and policy.get('habitualTimingRegretTolerance'):
        habitual = habitual_candidate(p,pool,baseline)
        if habitual is not None and habitual not in finalists:
            finalists.append(habitual)
    for candidate in ranked:
        parent = by_key.get(candidate.get('parentKey'))
        additions = [c for c in (parent,candidate) if c is not None and c not in finalists]
        if len(finalists)+len(additions) <= limit:
            finalists.extend(additions)
        if len(finalists) >= limit:
            break
    scores = {c['key']:scores[c['key']] + [simulate(p,c,t,artifact,step) for t in scenarios[screen_count:]]
              for c in finalists}
    return finalists, scenarios, scores


def calculate(raw):
    p=prepare(raw);artifact,policy,artifact_hash,policy_hash=load();initialize(p)
    policy=dict(policy)
    policy['layoutLimit']=policy['longLayoutLimit'] if (p.end-p.start).total_seconds()>policy['longHorizonHours']*3600 else policy['shortLayoutLimit']
    policy_hash=canonical_hash(policy)
    model=fit_cycles(p,policy);pool,search=build(p,policy);cycle_variants(p,pool,model,policy)
    scenarios=generate(p,artifact,policy['scenarioCount'],policy)
    pool,scenarios,scores=evaluate_pool(p,pool,scenarios,artifact,policy)
    habitual = None
    if policy.get('habitualTimingRegretTolerance') and not r_unverified(p):
        baseline=next((c for c in pool if c['baseline']),None)
        candidate=habitual_candidate(p,pool,baseline)
        if candidate and all(row['workBudgetCompleted'] for row in scores[candidate['key']]):
            habitual=candidate
    selected, baseline, regrets, assessment, deltas=compare_pool(p,pool,scores,policy,prefer_habitual=habitual)
    variants=[] if habitual else duration_variants(p,selected,pool,scenarios,scores,artifact,policy)
    if variants:
        pool.extend(variants)
        scores.update({c['key']:[simulate(p,c,t,artifact,policy['integrationStepMin']) for t in scenarios]
                       for c in variants})
        selected, baseline, regrets, assessment, deltas=compare_pool(p,pool,scores,policy)
    original = selected
    aligned = None
    if policy.get('habitualTimingRegretTolerance'):
        layout = align_bedtime(p, original, policy)
        if layout is not None:
            candidate = next((c for c in pool if c['layout'] == layout and not c.get('parentKey')), None)
            if candidate is None:
                candidate = dict(key='habitual-bedtime', layout=layout, timeline=timeline(p,layout),
                                 strategy=original['strategy'], distance=habitual_distance(p,layout), baseline=False)
            series = scores.get(candidate['key'])
            if series is None:
                series = [simulate(p,candidate,t,artifact,policy['integrationStepMin']) for t in scenarios]
            # Keep any workload budget the original plan could satisfy.
            if all(not before['workBudgetCompleted'] or after['workBudgetCompleted']
                   for before, after in zip(scores[original['key']], series)):
                aligned = candidate
                if candidate not in pool:
                    pool.append(candidate)
                    scores[candidate['key']] = series
                selected, baseline, regrets, assessment, deltas = compare_pool(
                    p,pool,scores,policy,prefer_habitual=aligned)
    status=('BASELINE' if baseline and selected['key']==baseline['key'] else 'TRADEOFF' if not selected['layout'] else 'READY') if selected else 'NO_FEASIBLE_PLAN'
    codes=['BASELINE_WHEN_COMPARISON_UNCERTAIN' if status=='BASELINE' else 'ROBUST_SLEEP_STUDY_BALANCE'] if selected else ['NO_FREE_WINDOW']
    if aligned:
        codes=['HABITUAL_BEDTIME_WITH_SAME_DURATION']
    elif habitual:
        codes=['HABITUAL_SLEEP_WITH_WORK_BUDGET']
    if selected:
        work=work_summary(scores[selected['key']])
        codes.append({'ALL_ASSUMPTIONS':'WORKLOAD_BUDGET_AVAILABLE','SOME_ASSUMPTIONS':'WORKLOAD_UNCERTAIN','NO_ASSUMPTIONS':'WORKLOAD_UNFINISHED'}[work['status']])
        sleep=sum(minutes(a,b) for a,b,k in selected['layout'])
        horizon=(p.end-p.start).total_seconds()/3600
        if not sleep: codes.append('NO_SLEEP_TRADEOFF')
        elif sleep < p.raw['profile']['sleepNeedMin']*max(1,horizon/24): codes.append('SHORT_SLEEP_TRADEOFF')
        last=p.cache.get('lastSleepEnd')
        if last and ((p.start-last).total_seconds()>20*3600 or
                     any(row.get('asleepDurationMin') is not None and row['asleepDurationMin']<240 and b>=p.start-timedelta(days=3) for a,b,row in p.cache['history'])):
            codes.append('RECOVERY_CONTEXT')
        if max_scheduled_wake(p,selected['layout'])>policy['maxContinuousWakeHours']: codes.append('LONG_WAKE_TRADEOFF')
        bed=p.raw['profile'].get('usualBedtime')
        if bed:
            hh,mm=map(int,bed.split(':'));clock=hh*60+mm
            for a,b,k in selected['layout']:
                local=a.astimezone(KST);distance=abs(local.hour*60+local.minute-clock)
                if k=='MAIN_SLEEP' and min(distance,1440-distance)>=180: codes.append('SHIFTED_SLEEP_TIMING');break
    if any(x['kind']=='PREPARATION_TRAVEL' for x in p.raw['fixedIntervals']): codes.append('EXAM_PREPARATION_RESERVED')
    if selected and selected.get('cycleMode')!='OFF' and selected.get('parentKey'): codes.append('PERSONAL_STAGE_TIE_BREAK')
    alternative=None
    if selected:
        alt=original if aligned else baseline if baseline is not selected else min((c for c in pool if c['strategy']!=selected['strategy']),key=lambda c:regrets[c['key']],default=None)
        if alt: alternative=serialize(alt,scores[alt['key']],regrets[alt['key']])
    elif p.raw['constraints']['napPolicy']!='DISABLED': alternative=recovery_alternative(p,policy)
    warnings=list(p.warnings)
    if p.raw['constraints']['napPolicy']=='OPTIONAL_ONLY': warnings.append('NAP_LOCATION_NOT_ASSUMED')
    if not model['enabled']: warnings.append('CYCLE_ADJUSTMENT_DISABLED')
    if selected and not selected['layout']: warnings.append('NO_SLEEP_COMPARISON_SELECTED_NOT_SAFETY_ENDORSEMENT')
    if 'SHORT_SLEEP_TRADEOFF' in codes: warnings.append('INSUFFICIENT_SLEEP_TRADEOFF')
    if 'SHIFTED_SLEEP_TIMING' in codes: warnings.append('SLEEP_TIMING_FEASIBILITY_UNCERTAIN')
    if 'LONG_WAKE_TRADEOFF' in codes: warnings.append('LONG_WAKE_TRADEOFF_NOT_SAFETY_VALIDATED')
    if selected and 0<sum(minutes(a,b) for a,b,k in selected['layout'])<180: warnings.append('SHORT_SLEEP_DOSE_NOT_IDENTIFIED')
    if p.raw['optionalObservations']['caffeineState']!='COMPLETE': warnings.append('CAFFEINE_UNOBSERVED')
    result=dict(contractVersion=p.raw['contractVersion'],status=status,sourceRevision=p.raw['sourceRevision'],inputHash=p.input_hash,
                artifactHash=artifact_hash,parameterHash=policy_hash,scenarioHash=canonical_hash(scenarios),policyVersion=policy['version'],
                objectiveScope='EXAM_BENEFIT_VIA_UNCALIBRATED_MEMORY_AND_STATE',
                selected=serialize(selected,scores[selected['key']],regrets[selected['key']]) if selected else None,alternative=alternative,
                comparison=dict(baselineKey=baseline['key'] if baseline else None,assessment=assessment,scenarioDeltaRange={'min':min(deltas),'max':max(deltas)} if deltas else None,scenarioShareIsProbability=False),
                reasonCodes=codes,reasons=[REASONS[x] for x in codes],warnings=sorted(set(warnings)),
                quality=dict(cycleEligible=model['enabled'],cycleMode=model['mode'],cycleEligibleNights=model['eligibleNights'],cycleModelHash=model['hash'],targetCalibration='NOT_EVALUATED',shortSleepDoseCalibration='NOT_IDENTIFIED'),
                diagnostics=dict(selectionRule='HABITUAL_BEDTIME_WITH_SAME_DURATION' if aligned else 'RELATIVE_MINIMAX_REGRET_WITH_NEAR_TIE_BASELINE',regretScale='RELATIVE_TO_SCENARIO_BEST_WITH_0_05_FLOOR',scenarioCount=len(scenarios),candidateCount=len(pool),integrationStepMin=policy['integrationStepMin'],
                                 maxWakeHoursAssumingScheduledSleep=max_scheduled_wake(p,selected['layout']) if selected else None,
                                 candidateRegrets=[{'candidateKey':c['key'],'regret':regrets[c['key']]} for c in pool],**search),
                evidence=dict(sourceArtifactVersion=artifact['artifactVersion'],sourceFitStatus=artifact.get('fitStatus','OFFLINE_SOURCE_FITTED'),learningSource=artifact['learning']['source'],
                              learningTest=artifact['learning']['test'],sleepStudies=artifact['sleepMeta']['includedStudies'],targetValidation='NOT_EVALUATED',
                              cyclePrior=model['priorStatus'],licenses=artifact['license'],assumptions=['PREPARATION_RANGES','UNVALIDATED_REMAINING_WORK_HOURS','STUDY_RATE_AND_DUTY','PVT_TO_MEMORY_TRANSFER','RECENT_SLEEP_DOSE_PROXY_NOT_FITTED_CURVE','POST_ENCODING_COMPOSITE','CIRCADIAN_PHASE_PROXY','EXAM_MIDPOINT_STATE_PROXY']),
                workload=dict(source='BACKEND_RANGE' if p.raw['preparation'].get('remainingStudyHours') and 'PREPARATION_STATE_STALE' not in p.warnings else 'READINESS_ENGINEERING_RANGE',
                              remainingFocusedHoursRange=dict(min=min(t['remainingHours'] for t in scenarios),max=max(t['remainingHours'] for t in scenarios)),
                              measured=False,completionProbability=None,postExamRecoveryCredited=False),
                predictedExamScore=None,individualSuccessProbability=None,estimatedRecalledItems=None)
    return {'result':result,'modelVersion':MODEL_VERSION}


def r_unverified(p): return p.raw['populationScope']=='ADULT_UNVERIFIED'

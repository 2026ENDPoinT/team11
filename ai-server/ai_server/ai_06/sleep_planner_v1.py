"""Experimental scenario planner, not a causal or validated sleep recommendation."""
from datetime import datetime,timedelta
from zoneinfo import ZoneInfo
from pathlib import Path
import math
import numpy as np
import pandas as pd
import joblib
from .common import read_json,sha
from .sleep_only_v2 import OUT,FEATURES,input_frame
from .personal_sleep import PersonalSleep
from .sleep_windows import latency_for_bedtime

TZ=ZoneInfo('Asia/Seoul')


class NoFeasiblePlan(ValueError):
    pass

def clock(value):
    try:
        t=datetime.strptime(value,'%H:%M');return t.hour+t.minute/60
    except (ValueError,TypeError):raise ValueError('시각은 HH:MM 형식으로 입력해주세요.')

def finite(value,label):
    try:n=float(value)
    except (TypeError,ValueError):raise ValueError(label+'을 숫자로 입력해주세요.')
    if not math.isfinite(n):raise ValueError(label+'을 확인해주세요.')
    return n

def local_dt(value):
    try:d=datetime.fromisoformat(value)
    except (TypeError,ValueError):raise ValueError('날짜와 시각을 확인해주세요.')
    return d.replace(tzinfo=TZ) if d.tzinfo is None else d.astimezone(TZ)

def recommend(data,now=None,policy="pvt"):
    if policy not in ("pvt","maximum"):raise ValueError("Unknown planning policy")
    now=now or datetime.now(TZ)
    if now.tzinfo is None:now=now.replace(tzinfo=TZ)
    minimum=finite(data.get('min_hours'),'최소 침대 체류시간');maximum=finite(data.get('max_hours'),'최대 침대 체류시간')
    if not 0<minimum<=maximum<=16:raise ValueError('침대 체류시간은 0시간 초과~16시간 이하이며 최소가 최대보다 작거나 같아야 합니다.')
    if 'earliest' in data:raise ValueError('취침 가능 시각은 입력받지 않습니다. 서버 현재 시각을 사용합니다.')
    earliest=now;deadline=local_dt(data.get('deadline'))
    if deadline<=earliest:raise ValueError('API의 기상 마감은 현재 시각보다 늦어야 합니다.')
    if deadline-now>timedelta(hours=36):raise ValueError('현재 데모는 36시간 안의 한 번의 주 수면 계획만 지원합니다.')
    records=data.get('history')
    if not isinstance(records,list) or not 3<=len(records)<=90:raise ValueError('과거 수면 기록을 3~90일 입력해주세요.')
    history=[];eff=[];bedhours=[];wakehours=[];seen=set();latencies=[];awakening=[];personal_records=[];latency_records=[]
    for r in records:
        try:day=datetime.strptime(r['date'],'%Y-%m-%d').date()
        except (KeyError,TypeError,ValueError):raise ValueError('과거 기록의 기상 날짜를 확인해주세요.')
        bed=clock(r.get('bedtime'));wake=clock(r.get('wake'));hours=finite(r.get('sleep_hours'),'실제 수면시간')
        if day in seen:raise ValueError('같은 기상 날짜의 기록은 하나만 입력해주세요.')
        seen.add(day)
        ended=datetime.combine(day,datetime.min.time(),TZ)+timedelta(hours=wake)
        if ended>now or day<now.date()-timedelta(days=90):raise ValueError('최근 90일 내 이미 끝난 수면 기록만 사용할 수 있습니다.')
        tib=((round(wake*60)-round(bed*60))%1440)/60
        if not 0<hours<=tib<=16:raise ValueError('실제 수면은 침대 체류시간 이하여야 하고, 각 수면 구간은 16시간 이하여야 합니다.')
        latency=r.get('latency_minutes')
        if latency is None and r.get('sleep_onset'):
            latency=round(((clock(r['sleep_onset'])-bed)%24)*60,6)
        if latency is not None:
            latency=finite(latency,'잠복기')
            if not 0<=latency<=(tib-hours)*60+1e-6:raise ValueError('잠복기는 침대 체류시간에서 실제 수면을 뺀 시간 이하여야 합니다.')
            latencies.append(latency)
            latency_records.append({"bed_hour":bed,"minutes":latency})
        count=r.get('awakening_count')
        if count is not None:
            count=finite(count,'각성 횟수')
            if not 0<=count<=1000:raise ValueError('각성 횟수를 확인해주세요.')
            awakening.append(count)
        history.append({'wake_date':day.isoformat(),'sleep_hours':hours})
        personal_records.append({'date':day.isoformat(),'sleep_hours':hours,'inbed_hours':tib,'bed_hour':bed})
        eff.append(hours/tib);bedhours.append(bed);wakehours.append(wake)
    personal=PersonalSleep(personal_records)
    efficiency=personal.ratio;latency=float(np.median(latencies)) if latencies else None
    angle=np.asarray(bedhours)*2*np.pi/24
    habitual=float(np.arctan2(np.sin(angle).mean(),np.cos(angle).mean())*24/(2*np.pi)%24)
    wake_angle=np.asarray(wakehours)*2*np.pi/24
    habitual_wake=float(np.arctan2(np.sin(wake_angle).mean(),np.cos(wake_angle).mean())*24/(2*np.pi)%24)
    # A calendar-based 15-minute candidate grid; feasibility is never inferred from predictions.
    start=earliest.replace(second=0,microsecond=0)
    if start<earliest:start+=timedelta(minutes=1)
    start+=timedelta(minutes=(-start.minute)%15)
    duration_steps=range(math.ceil(minimum*4),math.floor(maximum*4)+1)
    if not duration_steps:raise NoFeasiblePlan('설정 범위에 15분 단위 수면 기회가 없습니다. 범위를 넓혀주세요.')
    candidates=[];frames=[]
    while start+timedelta(hours=minimum)<=deadline:
        bed_hour=start.hour+start.minute/60
        candidate_latency,latency_method,latency_count=latency_for_bedtime(latency_records,bed_hour)
        durations=[n/4 for n in duration_steps]
        for duration in durations:
            end=start+timedelta(hours=duration)
            if end>deadline:continue
            actual,estimate_method=personal.estimate(duration,start.hour+start.minute/60)
            buffer_minutes=max(0,(duration-actual)*60)
            if candidate_latency is not None and candidate_latency>buffer_minutes+1e-5:continue
            estimated_latency=candidate_latency if candidate_latency is not None else 0.
            onset=start+timedelta(minutes=estimated_latency)
            payload={'wake_date':end.date().isoformat(),'sleep_hours':actual,'inbed_hours':duration,
                'efficiency_pct':actual/duration*100,'onset_hour':onset.hour+onset.minute/60,'wake_hour':end.hour+end.minute/60,
                'prior_nights':[r for r in history if r['wake_date']<end.date().isoformat()]}
            if candidate_latency is not None:
                payload['latency_minutes']=estimated_latency
                payload['waso_hours']=max(0,duration-actual-estimated_latency/60)
            if awakening:payload['awakening_count']=float(np.median(awakening))
            frame=input_frame(payload);frames.append(frame)
            candidates.append({'bedtime':start.isoformat(),'wake_time':end.isoformat(),
                'sleep_opportunity_hours':duration,'estimated_actual_sleep_hours':actual,
                'actual_sleep_estimation_method':estimate_method,
                'estimated_sleep_onset':onset.isoformat() if candidate_latency is not None else None,
                'estimated_latency_minutes':candidate_latency,'latency_estimation_method':latency_method,
                'latency_records_used':latency_count,'total_buffer_minutes':buffer_minutes,
                'other_awake_buffer_minutes':max(0,buffer_minutes-candidate_latency) if candidate_latency is not None else None,
                'unallocated_buffer_minutes':buffer_minutes if candidate_latency is None else 0,
                'buffer_note':'잠복기 외 비수면 여유는 중간 각성·기상 후 침대 체류를 합한 추정이며 특정 시각에 몰아 배치하지 않습니다.' if candidate_latency is not None else '잠복기 기록이 없어 비수면 여유의 앞뒤 비율과 예상 입면 시각은 알 수 없습니다. PVT 시각 특징에는 침대 진입을 대리값으로 사용합니다.',
                'wake_deviation_hours':abs(((end.hour+end.minute/60)-habitual_wake+12)%24-12),
                'bedtime_deviation_hours':abs(((start.hour+start.minute/60)-habitual+12)%24-12)})
        start+=timedelta(minutes=15)
    if not candidates:raise NoFeasiblePlan('기상 마감 전까지 요청한 침대 체류시간과 개인별 비수면 여유를 함께 확보할 수 없습니다.')
    assert all(minimum<=c['sleep_opportunity_hours']<=maximum and local_dt(c['wake_time'])<=deadline for c in candidates)
    selection=read_json(OUT/'selection.json');name=selection['selected'];path=OUT/(name+'.joblib')
    if name != 'tree3' or sha(path) != selection['model_hashes'][name]:
        raise OSError('AI-06 model checksum mismatch')
    x=pd.concat(frames,ignore_index=True)[FEATURES]
    predictions=np.maximum(0,joblib.load(path).predict(x))
    for c,score,row in zip(candidates,predictions,x.to_dict('records')):
        c['predicted_pvt_lapse_score']=float(score)
        c['outside_train_features']=[f for f,v in row.items() if pd.notna(v) and not selection['train_feature_ranges'][f][0]<=v<=selection['train_feature_ranges'][f][1]]
        c['imputed_features']=[f for f,v in row.items() if pd.isna(v)]
    # An unsupported prediction must never force a shorter sleep plan.
    critical={'sleep_hours','inbed_hours','efficiency_pct'}
    for c in candidates:
        reasons=[]
        if critical.intersection(c['outside_train_features']):
            reasons.append('주요 수면 특징이 학습 범위를 벗어났습니다.')
        if local_dt(c['wake_time']).hour>=12:
            reasons.append('정오 이후 기상하는 수면은 인지 모델의 학습·검증 범위 밖입니다.')
        if any(h>=12 for h in wakehours):
            reasons.append('과거 기록에 정오 이후 기상이 포함되어 있어 이 수면 패턴에 대한 인지 예측은 검증되지 않았습니다.')
        c['prediction_supported']=not reasons
        c['cognitive_prediction_status']='research_reference' if not reasons else 'withheld_unvalidated'
        c['cognitive_prediction_reasons']=reasons
        if reasons:c['predicted_pvt_lapse_score']=None
    valid=[c for c in candidates if c['prediction_supported']]
    # Planning effects are currently unvalidated for ALL candidates. Do not use
    # regression differences or MAE as a made-up statistical significance test.
    ordered=sorted(candidates,key=lambda c:(-c['sleep_opportunity_hours'],
        round(c['bedtime_deviation_hours']+c['wake_deviation_hours'],8),
        round(c['bedtime_deviation_hours'],8),c['bedtime']))
    habitual_best=ordered[0];best=habitual_best
    ranking='longest_feasible_then_habitual_pattern'
    # Preserve the longest opportunity. Within it, use personal actual-sleep
    # predictions only for a large, in-range difference vs the habitual choice.
    peers=[c for c in ordered if c['sleep_opportunity_hours']==habitual_best['sleep_opportunity_hours'] and c['actual_sleep_estimation_method']=='personal_ridge']
    margin=max(.25,2*(personal.info['ridge_mae_hours'] or 0))
    if peers and habitual_best['actual_sleep_estimation_method']=='personal_ridge':
        achievable=max(peers,key=lambda c:c['estimated_actual_sleep_hours'])
        if achievable['estimated_actual_sleep_hours']-habitual_best['estimated_actual_sleep_hours']>margin:
            best=achievable;ranking='longest_feasible_then_personal_sleep_gain'
    maximum_best=best
    maximum_policy=ranking
    habitual_actual=float(np.median([r['sleep_hours'] for r in personal_records]))
    habitual_opportunity=float(np.median([r['inbed_hours'] for r in personal_records]))
    # Frozen exploratory objective: lowest point estimate, not proven causal benefit.
    pvt_ordered=sorted(valid,key=lambda c:(c['predicted_pvt_lapse_score'],
        round(c['bedtime_deviation_hours']+c['wake_deviation_hours'],8),
        abs(c['estimated_actual_sleep_hours']-habitual_actual),c['bedtime']))
    pvt_best=pvt_ordered[0] if pvt_ordered else None
    # Nominal cycle arithmetic is a transparent planning heuristic, not sleep staging.
    cycle_minutes=90
    assumed_latency=latency if latency is not None else 0.
    def cycle_key(c):
        minutes=c['estimated_actual_sleep_hours']*60
        count=max(1,int(math.floor(minutes/cycle_minutes+.5)))
        return (round(abs(minutes-count*cycle_minutes),8),
            round(abs(c['estimated_actual_sleep_hours']-habitual_actual),8),
            round(c['bedtime_deviation_hours']+c['wake_deviation_hours'],8),
            round(c['bedtime_deviation_hours'],8),c['bedtime'])
    cycle_ordered=sorted(candidates,key=cycle_key)
    cycle_best=cycle_ordered[0]
    cycle_info={'used':policy=='pvt' and pvt_best is None,
        'nominal_cycle_minutes':90,'cycle_length_is_measured':False,
        'assumed_latency_minutes':assumed_latency,'latency_missing':latency is None,
        'reference_opportunity_hours':habitual_opportunity,'reference_actual_sleep_hours':habitual_actual,'cycle_basis':'estimated_actual_sleep',
        'nominal_cycle_count':max(1,int(math.floor((cycle_best['estimated_actual_sleep_hours']*60)/90+.5))),
        'alignment_error_minutes':cycle_key(cycle_best)[0],
        'validated_for_pvt':False,
        'note':'90분은 계산용 가정입니다. 실제 수면주기·REM·각성 단계나 인지 개선을 예측하지 않습니다. 중간 각성으로 주기가 끊기는 현상도 반영하지 않습니다.',
        'source':'https://www.nhlbi.nih.gov/health/sleep/stages-of-sleep'}
    if policy=='pvt':
        if pvt_best is not None:
            best=pvt_best;ranking='lowest_predicted_pvt_experimental'
            ordered=pvt_ordered
        else:
            best=cycle_best;ordered=cycle_ordered
            ranking='pvt_unavailable_nominal_cycle_fallback'
    ordered=[best]+[c for c in ordered if c is not best]
    # 0.1 is only a display band, NOT a confidence/significance threshold.
    similar=[c for c in pvt_ordered if c['predicted_pvt_lapse_score']<=pvt_best['predicted_pvt_lapse_score']+.1] if pvt_best else []
    duration_comparison=[]
    for duration in sorted({c['sleep_opportunity_hours'] for c in candidates}):
        eligible=[c for c in pvt_ordered if c['sleep_opportunity_hours']==duration]
        fallback=[c for c in candidates if c['sleep_opportunity_hours']==duration]
        duration_comparison.append(eligible[0] if eligible else min(fallback,key=lambda c:c['bedtime_deviation_hours']+c['wake_deviation_hours']))
    alternatives=[]
    for c in ordered[1:]:
        if abs((local_dt(c['bedtime'])-local_dt(best['bedtime'])).total_seconds())>=1800 or abs(c['sleep_opportunity_hours']-best['sleep_opportunity_hours'])>=.5:
            alternatives.append(c)
        if len(alternatives)==2:break
    spread=max(c['predicted_pvt_lapse_score'] for c in valid)-min(c['predicted_pvt_lapse_score'] for c in valid) if valid else None
    summary=read_json(OUT/'summary.json')
    return {'recommendation':best,'alternatives':alternatives,'candidate_count':len(candidates),
        'constraint_basis':'opportunity','requested_inbed_hours':{'min':minimum,'max':maximum},
        'planning_now':now.isoformat(),'wake_deadline':deadline.isoformat(),
        'selection_policy':ranking,'prediction_used_for_ranking':policy=='pvt' and pvt_best is not None,
        'pvt_recommendation':pvt_best,'maximum_time_recommendation':maximum_best,
        'cycle_fallback':cycle_info,'cycle_recommendation':cycle_best if pvt_best is None else None,
        'maximum_time_policy':maximum_policy,'pvt_ranking_status':'experimental' if pvt_best else 'unavailable',
        'similar_score_candidate_count':len(similar),'similar_score_display_band':.1,
        'similarity_is_statistical_confidence':False,'duration_comparison':duration_comparison,
        'pvt_score_difference_vs_maximum':(maximum_best['predicted_pvt_lapse_score']-pvt_best['predicted_pvt_lapse_score']) if pvt_best and maximum_best['prediction_supported'] else None,
        'personal_sleep_model':personal.info,'personal_sleep_backtest':personal.cv,
        'personal_sleep_gain_threshold_hours':margin,
        'cognition_personalized':False,'cognitive_score_role':'experimental_ranking_not_validated_effect',
        'habitual_bedtime_hour':habitual,'habitual_wake_hour':habitual_wake,
        'maximum_feasible_opportunity_hours':maximum_best['sleep_opportunity_hours'],
        'maximum_requested_fully_available':max(c['sleep_opportunity_hours'] for c in candidates)>=maximum-1e-7,
        'unsupported_prediction_count':len(candidates)-len(valid),'model':'sleep-only-cognition-v2/'+name,
        'estimated_efficiency_pct':efficiency*100,'history_nights':len(history),'assumed_zero_latency_for_schedule':latency is None,
        'candidate_score_spread':spread,'validation_macro_mae':summary['validation'][name]['participant_macro_mae'],
        'status':'experimental_scenario_only','causal_improvement_validated':False,'recommendation_validated':False,
        'confidence':'후보의 인지 우열을 검증하지 못함','timezone':'Asia/Seoul',
        'limitations':['PVT 주의력 지표 예측이며 기억력·IQ·전체 인지능력 점수가 아닙니다.',
            '이 모델은 이미 끝난 수면 측정값으로 학습되었습니다. 미래 계획의 효과와 후보 순위는 검증되지 않았습니다.',
            '실제 수면량은 개인 기록의 날짜 순 검증을 거친 수면 모델 또는 개인 수면 비율로 추정합니다. 외부 성능과 개입 효과는 검증되지 않았습니다.',
            'REM·심박은 학습 자료에 없어 사용하지 않습니다. 없는 수면 특징은 학습 중앙값으로 보완됩니다.',
            '입력한 최소·최대는 잠복기·각성을 포함한 침대 체류시간입니다. 그 안의 실제 수면량은 추정치이며 확보를 보장하지 않습니다. 기기 차이·일반 사용자 적용은 검증되지 않았습니다.']}

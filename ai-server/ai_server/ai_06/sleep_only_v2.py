"""Wearable-compatible, post-sleep PVT prediction. No cognition inputs or test access."""
import argparse
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
from sklearn.pipeline import make_pipeline
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler, SplineTransformer
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import Ridge
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.dummy import DummyRegressor
from .common import ROOT, read_json, write_json, sha, metrics
from .chennai_wearable import PROJECT, TARGET, NIGHT, HISTORY, model as old_model
from .io_guard import development_io_guard

OUT = Path(__file__).with_name('sleep-only-cognition-v2')
DERIVED = ['duration_delta_3d','duration_delta_7d','sleep_8d_total_with_current']
FEATURES = NIGHT + HISTORY + DERIVED
CANDIDATES = ['median','old_tree','ridge100','spline100','tree3','tree7']

def features(frame):
    d = frame.copy()
    d['duration_delta_3d'] = d.sleep_hours - d.prior_3d_sleep_mean
    d['duration_delta_7d'] = d.sleep_hours - d.prior_7d_sleep_mean
    # Current night plus preceding seven nights, eight nights total.
    d['sleep_8d_total_with_current'] = d.sleep_hours + 7*d.prior_7d_sleep_mean
    return d

def estimator(name):
    if name == 'old_tree':
        # Exact previous algorithm and features for a fair baseline comparison.
        return make_pipeline(ColumnTransformer([('old', 'passthrough', NIGHT+HISTORY)]), old_model('night_history_tree'))
    if name == 'median':
        return make_pipeline(SimpleImputer(keep_empty_features=True), DummyRegressor(strategy='median'))
    if name.startswith('tree'):
        return make_pipeline(SimpleImputer(strategy='median', keep_empty_features=True),
            HistGradientBoostingRegressor(loss='absolute_error', max_iter=100,
                max_leaf_nodes=int(name[-1]), min_samples_leaf=60, l2_regularization=10,
                early_stopping=False, random_state=20261113))
    steps = [SimpleImputer(strategy='median', keep_empty_features=True)]
    if name == 'spline100':
        steps.append(SplineTransformer(n_knots=3, degree=2, include_bias=False, extrapolation='constant'))
    steps.extend([StandardScaler(), Ridge(alpha=100)])
    return make_pipeline(*steps)

def train():
    if OUT.exists(): raise FileExistsError('Immutable experiment already exists')
    OUT.mkdir()
    write_json(OUT/'protocol.json', {
        'target': TARGET, 'inputs': FEATURES, 'candidates': CANDIDATES,
        'selection': 'Lowest train 5-fold participant-macro OOF MAE among sleep models; median is separate reference. No validation tuning.',
        'excluded': ['current/past cognition','participant ID','age','sex','study day','test clock time','treatment'],
        'scope': 'Actual completed main sleep -> subsequent PVT daily mean lapse score. No bedtime policy, no general IQ or memory claim.',
        'historical_exposure': 'Prior experiments used this cohort. Validation is a development comparison, not a new external test.',
        'test': 'Not opened; no test evaluation authorized for this new model.',
        'training_source_sha256': sha(Path(__file__)),
    })
    with development_io_guard(PROJECT, ('train',)) as opened_train:
        manifest=read_json(PROJECT/'data/split_manifest.json')
        spec=manifest['partitions']['train']; path=PROJECT/spec['path']
        assert sha(path)==spec['sha256']
        tr=features(pd.read_csv(path,dtype={'participant_id':str}))
        assert set(tr.participant_id)==set(spec['participant_ids'])
        folds=tr.participant_id.map(manifest['train_folds']).to_numpy()
        assert not pd.isna(folds).any()
        preds=tr[['participant_id','row_id',TARGET]].copy(); preds['fold']=folds
        board=[]
        for name in CANDIDATES:
            pred=np.full(len(tr),np.nan)
            for fold in sorted(set(folds)):
                hold=folds==fold
                assert set(tr.loc[hold,'participant_id']).isdisjoint(tr.loc[~hold,'participant_id'])
                m=estimator(name).fit(tr.loc[~hold,FEATURES],tr.loc[~hold,TARGET])
                pred[hold]=np.maximum(0,m.predict(tr.loc[hold,FEATURES]))
            assert np.isfinite(pred).all()
            preds[name]=pred
            board.append({'candidate':name, **metrics(tr[TARGET],pred,tr.participant_id)})
            print(name,board[-1]['participant_macro_mae'],flush=True)
        best=min((r for r in board if r['candidate']!='median'),key=lambda r:r['participant_macro_mae'])['candidate']
        # Selection is frozen before opening validation. Save all candidates for reproducibility.
        hashes={}
        for name in CANDIDATES:
            m=estimator(name).fit(tr[FEATURES],tr[TARGET])
            path=OUT/(name+'.joblib');joblib.dump(m,path);hashes[name]=sha(path)
        preds.to_csv(OUT/'train_oof_predictions.csv',index=False)
        write_json(OUT/'selection.json',{'selected':best,'oof_leaderboard':board,'model_hashes':hashes,
            'train_rows':len(tr),'train_participants':tr.participant_id.nunique(),
            'train_feature_ranges':{c:[float(tr[c].min()),float(tr[c].max())] for c in FEATURES}})
    with development_io_guard(PROJECT, ('validation',)) as opened_val:
        spec=manifest['partitions']['validation'];path=PROJECT/spec['path']
        assert sha(path)==spec['sha256']
        va=features(pd.read_csv(path,dtype={'participant_id':str}))
        assert set(va.participant_id)==set(spec['participant_ids'])
        assert set(tr.participant_id).isdisjoint(va.participant_id)
        val=va[['participant_id','row_id',TARGET]].copy();scores={}
        # Only preselected model and predefined references evaluated on validation.
        for name in dict.fromkeys(['median','old_tree',best]):
            assert sha(OUT/(name+'.joblib'))==hashes[name]
            m=joblib.load(OUT/(name+'.joblib'))
            val[name]=np.maximum(0,m.predict(va[FEATURES]))
            scores[name]=metrics(va[TARGET],val[name],va.participant_id)
        dif=(abs(val[best]-val[TARGET])-abs(val['median']-val[TARGET])).groupby(val.participant_id).mean().to_numpy()
        rng=np.random.default_rng(20261113)
        ci=np.quantile(rng.choice(dif,(5000,len(dif)),replace=True).mean(axis=1),[.025,.975]).tolist()
        # No validated coverage claim: this CI concerns mean error difference, not a personal prediction interval.
        summary={'selected':best,'target':TARGET,'validation':scores,'sleep_minus_median_mae_ci95':ci,
            'sleep_minus_median_mae':float(dif.mean()),'deployment_ready':False,'test_evaluated':False,
            'sleep_value_supported_on_validation':bool(ci[1]<0),'chennai_existing_models_modified':False}
        val.to_csv(OUT/'validation_predictions.csv',index=False)
        write_json(OUT/'summary.json',summary)
    write_json(OUT/'access_audit.json',{'train_stage':sorted(opened_train),'validation_stage':sorted(opened_val)})
    print(summary,flush=True)

def input_frame(payload):
    allowed={'wake_date','sleep_hours','inbed_hours','latency_minutes','efficiency_pct','waso_hours','awakening_count','onset_hour','wake_hour','prior_nights'}
    if set(payload)-allowed: raise ValueError('Unsupported inputs: '+str(sorted(set(payload)-allowed)))
    if not {'wake_date','sleep_hours','onset_hour','wake_hour'}<=set(payload): raise ValueError('Required: wake_date, sleep_hours, onset_hour, wake_hour')
    date=pd.Timestamp(payload['wake_date'])
    if pd.isna(date) or date!=date.normalize() or date.tzinfo is not None: raise ValueError('wake_date must be local YYYY-MM-DD')
    d={c:np.nan for c in NIGHT+HISTORY}
    limits={'sleep_hours':24,'inbed_hours':24,'latency_minutes':1440,'efficiency_pct':100,'waso_hours':24,'awakening_count':1000,'onset_hour':24,'wake_hour':24}
    for key,limit in limits.items():
        if key in payload and payload[key] is not None:
            value=float(payload[key])
            if not np.isfinite(value) or not 0<=value<=limit: raise ValueError('Invalid '+key)
            if key in ('onset_hour','wake_hour') and value==24: raise ValueError('Clock hours must be below 24')
            if key in d: d[key]=value
    if not np.isfinite(d['sleep_hours']) or d['sleep_hours']<=0: raise ValueError('Completed sleep duration must be positive')
    if d['sleep_hours']>d['inbed_hours']: raise ValueError('Sleep duration exceeds in-bed duration')
    for label in ['onset','wake']:
        value=float(payload[label+'_hour'])
        d[label+'_sin']=np.sin(value*2*np.pi/24);d[label+'_cos']=np.cos(value*2*np.pi/24)
    previous=[]; seen=set()
    for item in payload.get('prior_nights',[]):
        if set(item)!={'wake_date','sleep_hours'}: raise ValueError('Prior nights need only wake_date and sleep_hours')
        dt=pd.Timestamp(item['wake_date']);hours=float(item['sleep_hours'])
        if pd.isna(dt) or dt.tzinfo is not None or dt!=dt.normalize() or dt>=date or dt in seen: raise ValueError('History must be unique, strictly past local dates')
        if not np.isfinite(hours) or not 0<=hours<=24: raise ValueError('Invalid past sleep duration')
        seen.add(dt);previous.append((dt,hours))
    for days in (3,7):
        values=[h for dt,h in previous if dt>=date-pd.Timedelta(days=days)]
        if len(values)==days:
            d[f'prior_{days}d_sleep_mean']=float(np.mean(values));d[f'prior_{days}d_sleep_std']=float(np.std(values))
    return features(pd.DataFrame([d]))[FEATURES]

def predict(payload, root=OUT):
    frame=input_frame(payload);selection=read_json(root/'selection.json');summary=read_json(root/'summary.json')
    name=selection['selected'];path=root/(name+'.joblib')
    if sha(path)!=selection['model_hashes'][name]:raise ValueError('Model checksum changed')
    value=float(max(0,joblib.load(path).predict(frame)[0]))
    out_of_range=[c for c in FEATURES if pd.notna(frame[c].iloc[0]) and not selection['train_feature_ranges'][c][0]<=frame[c].iloc[0]<=selection['train_feature_ranges'][c][1]]
    return {'predicted_pvt_lapse_score':value,'direction':'lower predicts fewer attention lapses',
        'model':name,'scope':'post-sleep research prediction; not a bedtime recommendation or overall cognition score',
        'missing_inputs_imputed':frame.columns[frame.isna().iloc[0]].tolist(),'outside_train_range':out_of_range,
        'validation_macro_mae':summary['validation'][name]['participant_macro_mae'],
        'sleep_value_supported_on_validation':summary['sleep_value_supported_on_validation'],'deployment_ready':False}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['train','predict']);parser.add_argument('--input',type=Path)
    args=parser.parse_args()
    if args.action=='train':train()
    else:
        if args.input is None:parser.error('--input required')
        import json
        print(json.dumps(predict(read_json(args.input)),ensure_ascii=False,indent=2))

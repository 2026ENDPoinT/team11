"""Chennai wearable-compatible sleep -> provided daily mean PVT lapse score.
Preparation and training are separate. Never a validated bedtime sleep policy.
"""
import argparse
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
from sklearn.pipeline import make_pipeline
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import Ridge
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.dummy import DummyRegressor
from .common import ROOT, sha, write_json, read_json, fold_assignment, metrics
from .io_guard import development_io_guard

PROJECT = ROOT / 'chennai-wearable-baseline-v1'
SOURCE = ROOT.parent / 'sleep-cognitive-engine/data/raw/chennai_GJ9QPC'
TARGET = 'pvt_lapse_score'
DURATION = ['sleep_hours']
NIGHT = DURATION + ['inbed_hours','latency_minutes','efficiency_pct','waso_hours','awakening_count',
    'onset_sin','onset_cos','wake_sin','wake_cos']
HISTORY = ['prior_3d_sleep_mean','prior_3d_sleep_std','prior_7d_sleep_mean','prior_7d_sleep_std']
FEATURES = {'duration_ridge':DURATION,'night_ridge':NIGHT,'night_history_ridge':NIGHT+HISTORY,
    'night_history_tree':NIGHT+HISTORY,'mean_baseline':DURATION,'median_baseline':DURATION}
# Frozen small comparison, no hyperparameter search.
CANDIDATES = list(FEATURES)
MAPPING = {'an_10_total_sleep_time_hrs':'sleep_hours','an_9_total_inbed_hrs':'inbed_hours',
 'an_7_latency':'latency_minutes','an_8_efficiency':'efficiency_pct',
 'an_11_wake_after_sleep_onset_hrs':'waso_hours','an_12_number_of_awakenings':'awakening_count'}


def calendar_history(nights):
    nights=nights.sort_values(['participant_id','wake_date']).copy()
    if nights.duplicated(['participant_id','wake_date']).any():
        raise ValueError('Duplicate person-night')
    for col in HISTORY:
        nights[col]=np.nan
    for pid,person in nights.groupby('participant_id'):
        for idx,row in person.iterrows():
            for days in (3,7):
                prior=person.loc[(person.wake_date<row.wake_date)&
                    (person.wake_date>=row.wake_date-pd.Timedelta(days=days)),'sleep_hours'].dropna()
                # Incomplete windows stay missing; never invent unobserved nights.
                if len(prior)==days:
                    nights.loc[idx,f'prior_{days}d_sleep_mean']=prior.mean()
                    nights.loc[idx,f'prior_{days}d_sleep_std']=prior.std(ddof=0)
    return nights


def prepare(root=PROJECT,source=SOURCE):
    root,source=Path(root),Path(source)
    if root.exists():
        raise FileExistsError('Do not overwrite splits')
    metadata=read_json(source/'download_manifest.json')
    for item in metadata['files']:
        if sha(source/item['file'])!=item['sha256']:
            raise ValueError('Source integrity mismatch')
    sleep=pd.read_stata(source/'actigraph_night_cleaned.dta',convert_categoricals=False)
    pvt=pd.read_stata(source/'pvt_dataset.dta',convert_categoricals=False)
    if pvt.duplicated(['pid','date']).any():raise ValueError('Duplicate PVT day')
    ids=set(pvt.pid.astype(int).astype(str))
    assert len(ids)==452
    sleep['participant_id']=sleep.pid.astype(int).astype(str)
    nights=sleep.loc[sleep.participant_id.isin(ids)].rename(columns=MAPPING).copy()
    nights['wake_date']=nights.date_nightbefore
    for label,col in [('onset','an_6_onset_time'),('wake','an_4_outbed_time')]:
        hour=nights[col].dt.hour+nights[col].dt.minute/60
        nights[label+'_sin']=np.sin(2*np.pi*hour/24)
        nights[label+'_cos']=np.cos(2*np.pi*hour/24)
    if not nights.sleep_hours.dropna().between(0,24).all():raise ValueError('Invalid sleep duration')
    if not nights.efficiency_pct.dropna().between(0,100).all():raise ValueError('Invalid efficiency units')
    nights=calendar_history(nights)
    pvt['participant_id']=pvt.pid.astype(int).astype(str)
    pvt=pvt.rename(columns={'date':'wake_date','pv_minorlapses':TARGET})
    selected=['participant_id','wake_date','an_4_outbed_time']+NIGHT+HISTORY
    frame=pvt[['participant_id','wake_date',TARGET]].merge(nights[selected],on=['participant_id','wake_date'],how='left',validate='one_to_one')
    valid=frame[TARGET].notna()&frame.sleep_hours.notna()&frame.sleep_hours.gt(0)
    before_noon=(frame.an_4_outbed_time.dt.normalize()==frame.wake_date)&(frame.an_4_outbed_time.dt.hour<12)
    rejected=int((valid&~before_noon).sum())
    frame=frame.loc[valid&before_noon].copy()
    frame['row_id']=frame.participant_id+':'+frame.wake_date.dt.strftime('%Y-%m-%d')
    frame=frame.drop(columns='an_4_outbed_time').sort_values(['participant_id','wake_date'])
    assert set(frame.participant_id)==ids and not frame.row_id.duplicated().any()
    assert frame[TARGET].ge(0).all()
    # Only reuse identities from earlier experiment, never outcome-based split search.
    old=read_json(ROOT.parent/'sleep-cognitive-engine/artifacts/chennai-intervention-validation-v1/evaluation.json')['splits']
    groups={x['fold']:set(x['test_participants']) for x in old}
    assignment={'test':groups[0],'validation':groups[1],'train':set.union(*(groups[i] for i in (2,3,4)))}
    assert set.union(*assignment.values())==ids
    assert sum(map(len,assignment.values()))==len(ids)
    manifest={'target':TARGET,'features':FEATURES,'partitions':{},
      'split_policy':'Reuse prior participant fold 0=test, 1=validation, 2-4=train; no new seed or outcome balancing',
      'historical_exposure':'Entire cohort previously used in CV and full-data fits; no fresh external holdout claim',
      'source_hashes':{x['file']:x['sha256'] for x in metadata['files']},'source_license':metadata['license'],
      'preparation_note':'Preparation reads full raw data to link and freeze splits; training reads train file only',
      'target_note':'Provided participant-date mean pv_minorlapses; fractional values possible; not raw trial-level integer counts',
      'timing_note':'PVT exact clock time absent. Source protocol afternoon testing; only prior main sleeps ending on target date before noon included. Within-day ordering not independently provable for every PVT.',
      'rejected_unknown_or_late_wake':rejected}
    for part,people in assignment.items():
        folder='heldout_test' if part=='test' else part
        path=root/'data'/folder/'chennai.csv';path.parent.mkdir(parents=True)
        sub=frame.loc[frame.participant_id.isin(people)]
        sub.to_csv(path,index=False)
        manifest['partitions'][part]={'path':str(path.relative_to(root)),'sha256':sha(path),'rows':len(sub),'participants':len(people),'participant_ids':sorted(people)}
    manifest['train_folds']=fold_assignment(assignment['train'],'chennai-wearable-v1:outer',5)
    write_json(root/'data/split_manifest.json',manifest)
    print({k:{x:v[x] for x in ['rows','participants']} for k,v in manifest['partitions'].items()},flush=True)


def model(name):
    if name.endswith('baseline'):
        return make_pipeline(SimpleImputer(strategy='median',keep_empty_features=True),DummyRegressor(strategy='median' if name.startswith('median') else 'mean'))
    if name.endswith('tree'):
        return make_pipeline(SimpleImputer(strategy='median',keep_empty_features=True),
           HistGradientBoostingRegressor(loss='absolute_error',max_iter=100,max_leaf_nodes=7,min_samples_leaf=30,l2_regularization=1,early_stopping=False,random_state=20261113))
    return make_pipeline(SimpleImputer(strategy='median',keep_empty_features=True),StandardScaler(),Ridge(alpha=10))


def cv(frame,assignment):
    folds=frame.participant_id.map(assignment).to_numpy()
    if pd.isna(folds).any():raise ValueError('Missing fold')
    predictions={name:np.full(len(frame),np.nan) for name in CANDIDATES}
    for fold in sorted(set(folds)):
        hold=folds==fold
        assert not set(frame.loc[hold,'participant_id'])&set(frame.loc[~hold,'participant_id'])
        for name,columns in FEATURES.items():
            fit=model(name).fit(frame.loc[~hold,columns],frame.loc[~hold,TARGET])
            predictions[name][hold]=np.maximum(0,fit.predict(frame.loc[hold,columns]))
    assert all(np.isfinite(x).all() for x in predictions.values())
    return predictions


def score(frame,predictions):
    return [{'candidate':name,**metrics(frame[TARGET],pred,frame.participant_id)} for name,pred in predictions.items()]


def train(root=PROJECT):
    root=Path(root);out=root/'artifacts/train-v1'
    if out.exists():raise FileExistsError('Immutable run exists')
    out.mkdir(parents=True)
    write_json(out/'protocol.json',{'candidates':FEATURES,'ridge_alpha':10,'tree_loss':'absolute_error','tree_iterations':100,
      'tree_leaves':7,'tree_min_leaf':30,'selection':'min participant macro MAE, train only; median/mean baselines eligible to avoid forced model improvement',
      'evaluation':'5 outer participant folds; each outer chooses using 3 inner folds. Full train 5-fold OOF selects final model.',
      'context_features_excluded':['age','sex','study day','PVT time','treatment assignment','current or historical cognition'],
      'scope':'Post-sleep retrospective predictor, not validated bedtime planner. REM absent. Prior sleep windows strictly exclude current wake date.',
      'validation_test':'Not opened during training; historically exposed cohort.'})
    with development_io_guard(root,allowed_partitions=('train',)) as opened:
        manifest=read_json(root/'data/split_manifest.json'); spec=manifest['partitions']['train']
        path=root/spec['path'];assert sha(path)==spec['sha256']
        frame=pd.read_csv(path,dtype={'participant_id':str})
        assert set(frame.participant_id)==set(spec['participant_ids'])
        assignment=manifest['train_folds']
        folds=frame.participant_id.map(assignment).to_numpy()
        nested=np.full(len(frame),np.nan);choices=[]
        for fold in sorted(set(folds)):
            hold=folds==fold;local=frame.loc[~hold].reset_index(drop=True)
            inner=fold_assignment(local.participant_id,f'chennai-wearable-v1:inner:{fold}',3)
            board=score(local,cv(local,inner));best=min(board,key=lambda x:x['participant_macro_mae'])['candidate']
            fitted=model(best).fit(local[FEATURES[best]],local[TARGET])
            nested[hold]=np.maximum(0,fitted.predict(frame.loc[hold,FEATURES[best]]))
            choices.append({'fold':int(fold),'chosen':best,'inner_leaderboard':board})
            print('Completed nested fold',fold,'chosen',best,flush=True)
        preds=cv(frame,assignment);board=score(frame,preds)
        best=min(board,key=lambda x:x['participant_macro_mae'])['candidate']
        hashes={}
        for name in CANDIDATES:
            fitted=model(name).fit(frame[FEATURES[name]],frame[TARGET])
            p=out/(name+'.joblib');joblib.dump(fitted,p);hashes[name]=sha(p)
        prediction=frame[['participant_id','row_id',TARGET]].copy();prediction['fold']=folds
        for name,pred in preds.items():prediction[name]=pred
        prediction['nested_selected']=nested;prediction.to_csv(out/'train_oof_predictions.csv',index=False)
        pd.DataFrame(board).to_csv(out/'leaderboard.csv',index=False)
        summary={'selected_candidate':best,'selected_features':FEATURES[best],'leaderboard':board,'nested_oof':metrics(frame[TARGET],nested,frame.participant_id),
         'outer_choices':choices,'model_hashes':hashes,'rows':len(frame),'participants':int(frame.participant_id.nunique()),
         'target':TARGET,'test_evaluated':False,'validation_evaluated':False,'deployment_ready':False,
         'train_feature_missing_fraction':frame[NIGHT+HISTORY].isna().mean().to_dict()}
        write_json(out/'summary.json',summary)
    write_json(out/'data_access_audit.json',{'opened':sorted(opened)})
    print(pd.DataFrame(board).to_string(index=False));print('Nested:',summary['nested_oof'],flush=True)
    return summary

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['prepare','train']);args=parser.parse_args()
    prepare() if args.action=='prepare' else train()

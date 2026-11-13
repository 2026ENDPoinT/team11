"""Within-user actual sleep prediction; no cognitive labels or cross-user data."""
import numpy as np
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import Ridge

def row(tib,bed,prior):
    angle=bed*2*np.pi/24
    return [tib,np.sin(angle),np.cos(angle),prior]

class PersonalSleep:
    def __init__(self,records):
        self.records=sorted(records,key=lambda r:r['date'])
        self.ratio=float(np.median([r['sleep_hours']/r['inbed_hours'] for r in self.records]))
        self.model=None;self.cv=[]
        self.info={'method':'personal_median_ratio','nights':len(records),'minimum_learning_nights':28,
            'selected_by':'chronological sleep-only backtest','cognition_used':False,
            'baseline_mae_hours':None,'ridge_mae_hours':None,'backtest_nights':0,
            'reason':'28일 미만: 개인 수면 비율 중앙값을 사용합니다.'}
        if len(records)<28:return
        x=[];y=[]
        for i,r in enumerate(self.records):
            past=self.records[max(0,i-3):i]
            if not past:continue
            prior=float(np.mean([a['sleep_hours'] for a in past]))
            x.append(row(r['inbed_hours'],r['bed_hour'],prior));y.append(r['sleep_hours'])
        x=np.asarray(x);y=np.asarray(y)
        for i in range(max(14,len(x)-14),len(x)):
            # x[i] belongs to records[i+1]. No current/future target in fit or baseline.
            model=make_pipeline(StandardScaler(),Ridge(alpha=10.)).fit(x[:i],y[:i])
            r=self.records[i+1];previous=self.records[:i+1]
            ratio=float(np.median([p['sleep_hours']/p['inbed_hours'] for p in previous]))
            actual=float(np.clip(model.predict(x[i:i+1])[0],0,r['inbed_hours']))
            baseline=ratio*r['inbed_hours']
            self.cv.append({'date':r['date'],'fit_through':self.records[i]['date'],
                'target':r['sleep_hours'],'predicted':actual,'baseline':baseline})
        base=float(np.mean([abs(r['target']-r['baseline']) for r in self.cv]))
        mae=float(np.mean([abs(r['target']-r['predicted']) for r in self.cv]))
        self.info.update(baseline_mae_hours=base,ridge_mae_hours=mae,backtest_nights=len(self.cv),
            reason='개인 회귀모델이 과거 날짜 검증에서 기준선보다 충분히 개선되지 않아 수면 비율을 사용합니다.')
        # Fixed development gate; not an independent external performance guarantee.
        if len(self.cv)>=10 and mae<=.95*base and base-mae>=.1:
            self.model=make_pipeline(StandardScaler(),Ridge(alpha=10.)).fit(x,y)
            self.info.update(method='personal_ridge',reason='과거 날짜 검증에서 수면 비율 기준보다 MAE가 5% 이상, 0.1시간 이상 감소했습니다.')
    def estimate(self,tib,bed):
        fallback=tib*self.ratio
        if self.model is None:return fallback,'personal_median_ratio'
        durations=[r['inbed_hours'] for r in self.records]
        distance=min(abs((bed-r['bed_hour']+12)%24-12) for r in self.records)
        if not min(durations)<=tib<=max(durations) or distance>1:
            return fallback,'ratio_outside_personal_training_range'
        previous=float(np.mean([r['sleep_hours'] for r in self.records[-3:]]))
        value=float(self.model.predict([row(tib,bed,previous)])[0])
        return float(np.clip(value,0.01,tib)),'personal_ridge'

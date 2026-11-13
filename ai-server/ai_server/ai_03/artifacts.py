"""Offline source fits, verified on read; no network or runtime fitting."""
from pathlib import Path
import hashlib
import json
import math
from ..errors import APIError
ARTIFACT_PATH=Path(__file__).with_name('artifacts')/'evidence_priors.json'
POLICY_PATH=Path(__file__).with_name('config')/'policy.json'

def load():
    try:
        blob=ARTIFACT_PATH.read_bytes();a=json.loads(blob);pb=POLICY_PATH.read_bytes();policy=json.loads(pb)
        if a['artifactVersion']!='minimal-sleep-source-v1' or policy['version']!='workload-balanced-2': raise ValueError
        if not 0<a['learning']['firstTestProbability']<1 or not a['learning']['acceptedByDevelopmentGate']: raise ValueError
        if not all(math.isfinite(v) for v in a['learning']['weights']): raise ValueError
        for key in ('Before_Restriction','Before_Deprivation','After_Restriction','After_Deprivation'):
            if not math.isfinite(a['sleepMeta']['cells'][key]['mean']): raise ValueError
        return a,policy,hashlib.sha256(blob).hexdigest(),hashlib.sha256(pb).hexdigest()
    except (OSError,ValueError,KeyError,TypeError):
        raise APIError('MODEL_UNAVAILABLE','AI-07 근거 또는 정책 산출물을 읽을 수 없습니다.',503) from None

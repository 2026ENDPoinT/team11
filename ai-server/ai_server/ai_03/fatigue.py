"""Exact triangular homeostatic transitions, not a validated PVT model."""
from functools import lru_cache
import math
WAKE=(-.0135,.000929,.00743)
SLEEP=(-2.17,.872,-.0397)
DELTA=19.8
MU=.472

@lru_cache(maxsize=4096)
def transition(hours,sleep):
    a,b,c=SLEEP if sleep else WAKE;ea,ec=math.exp(a*hours),math.exp(c*hours)
    return ea,ec,b*(ec-ea)/(c-a),MU*math.expm1(a*hours)/a

def step(p,u,hours,sleep):
    ea,ec,cross,forcing=transition(hours,sleep);offset=DELTA if sleep else 0.;v=u-offset
    return p*ea+v*cross+forcing,v*ec+offset

@lru_cache(maxsize=1)
def rested():
    p,u=4.49,29.9
    for _ in range(80): p,u=step(*step(p,u,16,False),8,True)
    return p,u

REFERENCE=max(1e-6,step(*rested(),24,False)[0]-rested()[0])

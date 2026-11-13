"""Invert a personal sleep estimate into a feasible in-bed window; no cognition data."""
import math

def durations_for_actual(personal, bed, minimum, maximum):
    targets=sorted({minimum,maximum,*[n/4 for n in range(math.ceil(minimum*4),math.floor(maximum*4)+1)]})
    # Include model regime boundaries: the ridge estimate switches to ratio outside history.
    points=sorted({.01,16.,*[n/4 for n in range(1,65)],
        *[r['inbed_hours'] for r in personal.records]})
    points=[p for p in points if 0<p<=16]
    values=[personal.estimate(p,bed)[0] for p in points]
    durations=set(points)
    for target in targets:
        ratio_duration=target/personal.ratio
        if 0<ratio_duration<=16:durations.add(ratio_duration)
        for left,right,y0,y1 in zip(points,points[1:],values,values[1:]):
            if min(y0,y1)<=target<=max(y0,y1) and y0!=y1:
                lo,hi=left,right
                for _ in range(32):
                    mid=(lo+hi)/2;value=personal.estimate(mid,bed)[0]
                    if (value<target)==(y0<y1):lo=mid
                    else:hi=mid
                candidate=(lo+hi)/2
                # Discontinuous regime changes must not be mistaken for a solution.
                if abs(personal.estimate(candidate,bed)[0]-target)<1e-7:durations.add(candidate)
    return sorted(d for d in {round(v,8) for v in durations} if minimum-1e-8<=personal.estimate(d,bed)[0]<=maximum+1e-8)

def latency_for_bedtime(records,bed):
    import statistics
    nearby=[r['minutes'] for r in records if abs((r['bed_hour']-bed+12)%24-12)<=1.5]
    if len(nearby)>=3:return float(statistics.median(nearby)),'similar_bedtime_history',len(nearby)
    if records:return float(statistics.median(r['minutes'] for r in records)),'all_recorded_latency',len(records)
    return None,'unavailable',0

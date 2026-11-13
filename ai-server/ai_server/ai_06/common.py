import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEED = 20261113
DATASETS = ("brightertime", "stockholm")
TARGETS = ("accuracy_pct", "reaction_time_ms")
FEATURES = {
    "brightertime": ["sleep_hours", "sleep_midpoint_sin", "sleep_midpoint_cos",
                    "clock_sin", "clock_cos", "study_day"],
    "stockholm": ["restricted_condition", "young_group", "sex_male", "sex_female"],
}
CONTEXT = {
    "brightertime": ["clock_sin", "clock_cos", "study_day"],
    "stockholm": ["young_group", "sex_male", "sex_female"],
}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False, allow_nan=False) + "\n")


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def ordered_ids(ids, salt):
    return sorted(set(map(str, ids)), key=lambda x: hashlib.sha256(f"{SEED}|{salt}|{x}".encode()).hexdigest())


def fold_assignment(ids, salt, folds=5):
    return {pid: i % folds for i, pid in enumerate(ordered_ids(ids, salt))}


def metrics(y, pred, groups):
    import numpy as np
    y, pred, groups = np.asarray(y, float), np.asarray(pred, float), np.asarray(groups)
    e = np.abs(y - pred)
    # Every participant contributes equally, irrespective of test frequency.
    macro = float(np.mean([e[groups == g].mean() for g in np.unique(groups)]))
    mse = float(np.mean((y - pred) ** 2))
    return {"participant_macro_mae": macro, "pooled_mae": float(e.mean()),
            "pooled_rmse": mse ** .5, "rows": len(y), "participants": len(np.unique(groups))}


def restrict_predictions(pred, target):
    import numpy as np
    return np.clip(pred, 0, 100) if target == "accuracy_pct" else np.maximum(pred, 0)


def read_development(root, dataset, partition):
    """The training entry point cannot request held-out test through this loader."""
    import pandas as pd
    if partition not in ("train", "validation"):
        raise PermissionError("Held-out test requires the separate approved evaluation entry point")
    root = Path(root)
    manifest = read_json(root / "data/split_manifest.json")
    spec = manifest["datasets"][dataset]["partitions"][partition]
    path = root / spec["path"]
    if sha(path) != spec["sha256"]:
        raise ValueError(f"Frozen {dataset}/{partition} file changed")
    frame = pd.read_csv(path)
    if set(frame.participant_id) != set(spec["participant_ids"]):
        raise ValueError("Participant assignment changed")
    return frame

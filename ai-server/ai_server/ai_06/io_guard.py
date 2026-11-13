"""Audit actual Python file opens during development; not an OS security boundary."""
from contextlib import contextmanager
from pathlib import Path
import sys


@contextmanager
def development_io_guard(root, allowed_partitions=("train", "validation")):
    data_root = (Path(root) / "data").resolve()
    state = {"active": True}
    opened = set()

    def audit(event, args):
        if not state["active"] or event != "open" or not isinstance(args[0], (str, bytes)):
            return
        raw = args[0].decode() if isinstance(args[0], bytes) else args[0]
        path = Path(raw).resolve()
        try:
            relative = path.relative_to(data_root)
        except ValueError:
            return
        if relative.parts[0] not in {*allowed_partitions, "split_manifest.json", "oof_folds"}:
            raise PermissionError(f"Development process forbidden data access: {relative}")
        opened.add(str(relative))

    sys.addaudithook(audit)
    try:
        yield opened
    finally:
        state["active"] = False

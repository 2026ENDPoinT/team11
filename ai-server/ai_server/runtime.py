"""Bounded process execution and admission control; model mathematics is unchanged."""

from dataclasses import dataclass
import math
import multiprocessing as mp
import os
from threading import BoundedSemaphore, Lock
from time import monotonic

from .errors import APIError


@dataclass(frozen=True)
class RuntimeConfig:
    isolate: bool = True
    workers: int = 4
    connections: int = 32
    timeout_seconds: float = 2.5
    exam_timeout_seconds: float = 10.0
    memory_timeout_seconds: float = 10.0
    rate_per_minute: int = 120
    burst: int = 20
    failure_threshold: int = 3
    cooldown_seconds: float = 60

    def __post_init__(self):
        for name, high in (("workers", 32), ("connections", 256), ("rate_per_minute", 1000000),
                           ("burst", 100000), ("failure_threshold", 100)):
            value = getattr(self, name)
            if type(value) is not int or not 1 <= value <= high:
                raise ValueError(f"{name} must be an integer in 1..{high}")
        for name in ("timeout_seconds", "exam_timeout_seconds", "memory_timeout_seconds", "cooldown_seconds"):
            value = getattr(self, name)
            if type(value) not in (int, float) or not math.isfinite(value) or not .05 <= value <= 300:
                raise ValueError(f"{name} must be in .05..300 seconds")

    @classmethod
    def from_environment(cls):
        defaults = cls()
        values = {}
        for name in ("workers", "connections", "timeout_seconds", "exam_timeout_seconds", "memory_timeout_seconds", "rate_per_minute", "burst", "failure_threshold", "cooldown_seconds"):
            key = "SLEEP_" + name.upper()
            if key in os.environ:
                values[name] = float(os.environ[key]) if name.endswith("seconds") else int(os.environ[key])
        return cls(**values)


class Admission:
    def __init__(self, config, clock=monotonic):
        self.config, self.clock = config, clock
        self.lock = Lock()
        self.tokens, self.updated = float(config.burst), clock()
        self.circuits = {}
        self.counts = {"accepted": 0, "rate_limited": 0, "circuit_rejected": 0,
                       "completed": 0, "failed": 0, "timeouts": 0, "busy": 0}

    def enter(self, path):
        with self.lock:
            now = self.clock()
            circuit = self.circuits.setdefault(path, {"failures": 0, "until": 0, "probe": False})
            if circuit["until"]:
                if now < circuit["until"] or circuit["probe"]:
                    self.counts["circuit_rejected"] += 1
                    raise APIError("CIRCUIT_OPEN", "해당 모델이 복구 대기 중입니다.", 503)
                circuit["probe"] = True
            self.tokens = min(self.config.burst, self.tokens + (now-self.updated)*self.config.rate_per_minute/60)
            self.updated = now
            if self.tokens < 1:
                circuit["probe"] = False
                self.counts["rate_limited"] += 1
                raise APIError("RATE_LIMITED", "AI 계산 요청 한도를 초과했습니다.", 429)
            self.tokens -= 1
            self.counts["accepted"] += 1

    def finish(self, path, failed=False, timed_out=False):
        with self.lock:
            item = self.circuits[path]
            item["probe"] = False
            self.counts["failed" if failed else "completed"] += 1
            if timed_out:
                self.counts["timeouts"] += 1
            if failed:
                item["failures"] += 1
                if item["failures"] >= self.config.failure_threshold:
                    item["until"] = self.clock() + self.config.cooldown_seconds
            else:
                item.update(failures=0, until=0)

    def snapshot(self):
        with self.lock:
            return {**self.counts, "openCircuits": sum(v["until"] > self.clock() for v in self.circuits.values())}


def _calculate_worker(connection, path, payload):
    try:
        from .routes import POST_ROUTES
        result = POST_ROUTES[path](payload)
        connection.send((200, result))
    except APIError as error:
        connection.send((error.status, error.body()))
    except OSError:
        connection.send((503, {"error": {"code": "MODEL_UNAVAILABLE", "message": "모델 자원을 사용할 수 없습니다."}}))
    except Exception:
        connection.send((500, {"error": {"code": "INTERNAL_ERROR", "message": "모델 계산 중 오류가 발생했습니다."}}))
    finally:
        connection.close()


class ModelRuntime:
    def __init__(self, config, worker=_calculate_worker):
        self.config, self.worker = config, worker
        self.slots = BoundedSemaphore(config.workers)
        self.memory_slots = BoundedSemaphore(1)
        self.admission = Admission(config)
        self.lock = Lock()
        self.active = set()
        self.closed = False

    def execute(self, path, payload, *, probe=False):
        from .routes import EXAM_SLEEP_ROUTE, FOCUS_SLEEP_ROUTE
        memory_slot = path == EXAM_SLEEP_ROUTE
        if memory_slot and not self.memory_slots.acquire(blocking=False):
            raise APIError("SERVER_BUSY", "AI-03 계산 슬롯이 사용 중입니다.", 503)
        if not self.slots.acquire(blocking=False):
            if memory_slot:
                self.memory_slots.release()
            with self.admission.lock:
                self.admission.counts["busy"] += 1
            raise APIError("SERVER_BUSY", "계산 슬롯이 모두 사용 중입니다.", 503)
        entered, failed, timed_out = False, False, False
        try:
            with self.lock:
                if self.closed:
                    raise APIError("SERVER_BUSY", "서버가 종료 중입니다.", 503)
            if not probe:
                self.admission.enter(path)
                entered = True
            if not self.config.isolate:
                from .routes import POST_ROUTES
                return POST_ROUTES[path](payload)
            context = mp.get_context("spawn")
            timeout = self.config.timeout_seconds
            if memory_slot:
                timeout = self.config.memory_timeout_seconds
            elif path == FOCUS_SLEEP_ROUTE:
                timeout = self.config.exam_timeout_seconds
            deadline = monotonic() + timeout
            receiver, sender = context.Pipe(duplex=False)
            process = context.Process(target=self.worker, args=(sender, path, payload), daemon=True)
            try:
                # Serialize start/close so shutdown cannot miss a newly created child.
                with self.lock:
                    if self.closed:
                        raise APIError("SERVER_BUSY", "서버가 종료 중입니다.", 503)
                    process.start()
                    self.active.add(process)
                sender.close()
                if not receiver.poll(max(0, deadline - monotonic())):
                    timed_out = True
                    raise APIError("MODEL_TIMEOUT", "모델 계산 제한 시간을 초과했습니다.", 504)
                try:
                    status, body = receiver.recv()
                except EOFError:
                    raise APIError("MODEL_UNAVAILABLE", "모델 작업이 비정상 종료되었습니다.", 503) from None
                if status != 200:
                    error = body["error"]
                    raise APIError(error["code"], error["message"], status, details=error.get("details"))
                return body
            finally:
                sender.close()
                receiver.close()
                if process.pid is not None:
                    process.join(.05)
                    if process.is_alive():
                        process.terminate()
                        process.join(.5)
                    if process.is_alive():
                        process.kill()
                        process.join()
                with self.lock:
                    self.active.discard(process)
                if process.pid is not None:
                    process.close()
        except APIError as error:
            failed = error.status >= 500
            raise
        except OSError:
            failed = True
            raise
        except Exception:
            failed = True
            raise
        finally:
            if entered:
                self.admission.finish(path, failed, timed_out)
            self.slots.release()
            if memory_slot:
                self.memory_slots.release()

    def close(self):
        with self.lock:
            self.closed = True
            for process in self.active:
                if process.is_alive():
                    process.terminate()
                process.join(.5)

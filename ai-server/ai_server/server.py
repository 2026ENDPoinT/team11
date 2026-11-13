"""Stateless internal HTTP server. Never fetches data from Spring."""

import hmac
import json
import logging
import math
import os
import re
from threading import BoundedSemaphore, Lock
from time import monotonic
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from uuid import uuid4

from .errors import APIError
from .routes import EXAM_SLEEP_ROUTE, FOCUS_SLEEP_ROUTE, POST_ROUTES, RECOVERY_BACKEND_ROUTE, SCHEDULE_BACKEND_ROUTE
from .ai_01.service import health
from .runtime import ModelRuntime, RuntimeConfig
from .resources import inspect_resources

MAX_BODY = 1024 * 1024
MAX_JSON_DEPTH = 64
LOGGER = logging.getLogger(__name__)


def reject_constant(value):
    raise ValueError("JSON에는 유한한 숫자만 사용할 수 있습니다.")


def finite_float(value):
    result = float(value)
    if not math.isfinite(result):
        raise ValueError("JSON 숫자가 유한 범위를 초과했습니다.")
    return result


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("중복 JSON 필드가 있습니다.")
        result[key] = value
    return result


def validate_depth(value):
    pending = [(value, 0)]
    while pending:
        item, depth = pending.pop()
        if isinstance(item, (dict, list)):
            if depth > MAX_JSON_DEPTH:
                raise ValueError("JSON 중첩이 너무 깊습니다.")
            children = item.values() if isinstance(item, dict) else item
            pending.extend((child, depth + 1) for child in children)


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(5)

    def log_message(self, *args):
        # Do not log request bodies, credentials, or user-supplied URLs.
        pass

    def respond(self, status, value):
        body = json.dumps(value, ensure_ascii=False, allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Request-Id", self.request_id)
        if status == 405:
            self.send_header("Allow", "GET" if self.path in {"/ai/v1/health", "/ai/v1/ready", "/ai/v1/metrics"} else "POST")
        if status in (429, 503):
            self.send_header("Retry-After", "5")
        try:
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass
        elapsed = (monotonic() - self.started) * 1000
        self.server.record_request(status, elapsed)
        route = self.path if self.path in POST_ROUTES or self.path in {"/ai/v1/health", "/ai/v1/ready", "/ai/v1/metrics"} else "unknown"
        LOGGER.info(json.dumps({"requestId": self.request_id, "route": route,
                               "status": status, "elapsedMs": round(elapsed, 2)}))

    def dispatch(self):
        self.started = monotonic()
        supplied = self.headers.get("X-Request-Id")
        self.request_id = uuid4().hex
        try:
            if supplied is not None:
                if not re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", supplied):
                    raise APIError("INVALID_INPUT", "X-Request-Id 형식이 올바르지 않습니다.")
                self.request_id = supplied
            if self.command == "GET" and self.path == "/ai/v1/health":
                self.respond(200, health())
                return
            if self.path == "/ai/v1/health":
                raise APIError("METHOD_NOT_ALLOWED", "헬스체크는 GET으로 호출하세요.", 405)
            key = self.headers.get("X-Internal-Key", "")
            if not hmac.compare_digest(key.encode("utf-8"), self.server.internal_key.encode("utf-8")):
                raise APIError("UNAUTHORIZED", "내부 인증에 실패했습니다.", 401)
            if self.path in {"/ai/v1/ready", "/ai/v1/metrics"}:
                if self.command != "GET":
                    raise APIError("METHOD_NOT_ALLOWED", "운영 상태는 GET으로 조회하세요.", 405)
                if self.path == "/ai/v1/metrics":
                    self.respond(200, self.server.snapshot_metrics())
                else:
                    report = self.server.readiness()
                    self.respond(200 if report["ready"] else 503, report)
                return
            if self.path not in POST_ROUTES:
                raise APIError("NOT_FOUND", "지원하지 않는 엔드포인트입니다.", 404)
            if self.command != "POST":
                raise APIError("METHOD_NOT_ALLOWED", "계산 API는 POST로 호출하세요.", 405)
            if self.headers.get_content_type() != "application/json":
                raise APIError("INVALID_INPUT", "Content-Type은 application/json이어야 합니다.", 415)
            if self.headers.get("Transfer-Encoding") or len(self.headers.get_all("Content-Length", [])) != 1:
                raise APIError("INVALID_INPUT", "단일 Content-Length가 필요합니다.")
            try:
                length = int(self.headers["Content-Length"])
            except ValueError:
                raise APIError("INVALID_INPUT", "요청 길이가 올바르지 않습니다.") from None
            if length <= 0:
                raise APIError("INVALID_INPUT", "요청 본문은 비어 있을 수 없습니다.")
            if length > MAX_BODY:
                raise APIError("INVALID_INPUT", "요청 본문은 최대 1MiB여야 합니다.", 413)
            try:
                body = self.rfile.read(length)
            except TimeoutError:
                raise APIError("REQUEST_TIMEOUT", "요청 본문 수신 시간이 초과되었습니다.", 408) from None
            except OSError:
                raise APIError("INVALID_INPUT", "요청 본문을 수신하지 못했습니다.") from None
            try:
                if len(body) != length:
                    raise ValueError("incomplete body")
                payload = json.loads(body.decode("utf-8"), parse_constant=reject_constant,
                                     parse_float=finite_float, object_pairs_hook=unique_object)
                validate_depth(payload)
            except (ValueError, UnicodeError, RecursionError):
                raise APIError("INVALID_INPUT", "유효한 JSON 본문이 필요합니다.") from None
            if not inspect_resources()["ok"]:
                raise APIError("MODEL_UNAVAILABLE", "배포된 모델 리소스가 검증 기준과 다릅니다.", 503)
            result = self.server.runtime.execute(self.path, payload)
            self.respond(200, result)
        except APIError as error:
            self.respond(error.status, error.body())
        except OSError:
            self.respond(503, APIError("MODEL_UNAVAILABLE", "모델 파일 또는 연결을 사용할 수 없습니다.", 503).body())
        except Exception:
            LOGGER.error("request_id=%s calculation_failed", self.request_id)
            self.respond(500, APIError("INTERNAL_ERROR", "계산 처리 중 오류가 발생했습니다.", 500).body())

    do_GET = dispatch
    do_POST = dispatch
    do_HEAD = dispatch
    do_PUT = dispatch
    do_PATCH = dispatch
    do_DELETE = dispatch
    do_OPTIONS = dispatch


class AIServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 128

    def __init__(self, address, runtime_config):
        self.runtime = ModelRuntime(runtime_config)
        self.connection_slots = BoundedSemaphore(runtime_config.connections)
        self.metric_lock = Lock()
        self.metrics = {"requests": 0, "serverErrors": 0, "connectionRejected": 0, "connectionsActive": 0,
                        "elapsedMsTotal": 0.0, "elapsedMsMax": 0.0}
        self.smoke_status = {"ok": False, "models": {}}
        super().__init__(address, Handler)

    def process_request(self, request, client_address):
        if not self.connection_slots.acquire(blocking=False):
            with self.metric_lock:
                self.metrics["connectionRejected"] += 1
            body = b'{"error":{"code":"SERVER_BUSY","message":"Connection limit reached"}}'
            try:
                # Drain a bounded request before closing: unread TCP bytes can
                # otherwise reset the connection before the 503 reaches Windows clients.
                deadline = monotonic() + .1
                received = bytearray()
                while b"\r\n\r\n" not in received and len(received) < 65536:
                    request.settimeout(max(.001, deadline-monotonic()))
                    chunk = request.recv(4096)
                    if not chunk:
                        break
                    received.extend(chunk)
                    if monotonic() >= deadline:
                        break
                if b"\r\n\r\n" in received:
                    headers, already_read = bytes(received).split(b"\r\n\r\n", 1)
                    match = re.search(br"\r\nContent-Length:\s*(\d+)", headers, re.I)
                    remaining = min(int(match[1]), MAX_BODY) - len(already_read) if match else 0
                    while remaining > 0 and monotonic() < deadline:
                        request.settimeout(max(.001, deadline-monotonic()))
                        chunk = request.recv(min(65536, remaining))
                        if not chunk:
                            break
                        remaining -= len(chunk)
                request.settimeout(.2)
                request.sendall(b"HTTP/1.0 503 Service Unavailable\r\nContent-Type: application/json\r\nCache-Control: no-store\r\nRetry-After: 5\r\nX-Request-Id: " + uuid4().hex.encode() +
                                b"\r\nContent-Length: " + str(len(body)).encode() + b"\r\n\r\n" + body)
            except OSError:
                pass
            finally:
                self.shutdown_request(request)
            return
        try:
            with self.metric_lock:
                self.metrics["connectionsActive"] += 1
            super().process_request(request, client_address)
        except Exception:
            self.connection_slots.release()
            with self.metric_lock:
                self.metrics["connectionsActive"] -= 1
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.connection_slots.release()
            with self.metric_lock:
                self.metrics["connectionsActive"] -= 1

    def record_request(self, status, elapsed):
        with self.metric_lock:
            self.metrics["requests"] += 1
            self.metrics["serverErrors"] += int(status >= 500)
            self.metrics["elapsedMsTotal"] += elapsed
            self.metrics["elapsedMsMax"] = max(self.metrics["elapsedMsMax"], elapsed)

    def snapshot_metrics(self):
        with self.metric_lock:
            result = dict(self.metrics)
        result["execution"] = self.runtime.admission.snapshot()
        result["executionMode"] = "process" if self.runtime.config.isolate else "inline-development"
        return result

    def warmup(self):
        from pathlib import Path
        models = {}
        probes = (("ai02", "/ai/v1/plan/tonight"), ("ai03", EXAM_SLEEP_ROUTE),
                  ("ai04", RECOVERY_BACKEND_ROUTE), ("ai05", SCHEDULE_BACKEND_ROUTE),
                  ("ai06", FOCUS_SLEEP_ROUTE))
        if inspect_resources()["ok"]:
            for name, path in probes:
                try:
                    raw = json.loads((Path(__file__).with_name("smoke") / (name + ".json")).read_text(encoding="utf-8"))
                    result = self.runtime.execute(path, raw, probe=True)
                    models[name] = bool(result.get("result") is not None and result.get("modelVersion"))
                except Exception:
                    models[name] = False
        self.smoke_status = {"ok": len(models) == len(probes) and all(models.values()), "models": models}
        return self.smoke_status

    def readiness(self):
        resources = inspect_resources()
        return {"ready": resources["ok"] and self.smoke_status["ok"],
                "resources": resources, "startupInference": self.smoke_status}

    def server_close(self):
        self.runtime.close()
        super().server_close()


def create_server(host="127.0.0.1", port=8000, *, internal_key=None, runtime_config=None, warmup=True):
    key = os.environ.get("AI_INTERNAL_KEY", "") if internal_key is None else internal_key
    if (not isinstance(key, str) or not key or not key.isascii()
            or any(not char.isprintable() or char.isspace() for char in key)):
        raise ValueError("AI_INTERNAL_KEY는 공백 없는 출력 가능한 ASCII 문자열로 설정해야 합니다.")
    server = AIServer((host, port), runtime_config or RuntimeConfig())
    server.internal_key = key
    if warmup:
        server.warmup()
    return server


def configure_tls(server, cert_file, key_file):
    import ssl
    if bool(cert_file) != bool(key_file):
        raise ValueError("TLS 인증서와 키를 함께 지정하세요.")
    if cert_file:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(cert_file, key_file)
        server.socket = context.wrap_socket(server.socket, server_side=True, do_handshake_on_connect=False)

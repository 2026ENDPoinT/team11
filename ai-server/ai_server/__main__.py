import argparse
import logging
import os
import ssl
import multiprocessing

from .server import create_server, configure_tls
from .runtime import RuntimeConfig


def main():
    parser = argparse.ArgumentParser(description="SLEEP 통합 AI 서버")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--tls-cert", default=os.environ.get("SLEEP_TLS_CERT"))
    parser.add_argument("--tls-key", default=os.environ.get("SLEEP_TLS_KEY"))
    parser.add_argument("--log-level", choices=("INFO", "WARNING", "ERROR"), default="INFO")
    args = parser.parse_args()
    logging.basicConfig(level=getattr(logging, args.log_level), format="%(message)s")
    try:
        if bool(args.tls_cert) != bool(args.tls_key):
            raise ValueError("TLS 인증서와 키를 함께 지정하세요.")
        server = create_server(args.host, args.port, runtime_config=RuntimeConfig.from_environment())
    except (ValueError, OSError) as error:
        parser.exit(2, f"설정 오류: {error}\n")
    with server:
        if args.tls_cert:
            try:
                configure_tls(server, args.tls_cert, args.tls_key)
            except (OSError, ssl.SSLError) as error:
                parser.exit(2, f"TLS 설정 오류: {error}\n")
        if not server.readiness()["ready"]:
            parser.exit(2, "모델 리소스 또는 시작 추론 검증에 실패했습니다. resource manifest를 확인하세요.\n")
        scheme = "https" if args.tls_cert else "http"
        print(f"SLEEP AI: {scheme}://{args.host}:{server.server_port}/ai/v1", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    multiprocessing.freeze_support()
    main()

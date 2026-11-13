FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENV OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1
WORKDIR /app
COPY ai_server/ai_06/requirements.txt /app/ai06-requirements.txt
RUN python -m pip install --no-cache-dir -r /app/ai06-requirements.txt
COPY ai_server /app/ai_server
USER 10001:10001
EXPOSE 8000
# Render가 주는 PORT 환경변수에 맞춰 바인딩 (없으면 8000, backend/Dockerfile과 동일한 패턴)
CMD ["sh", "-c", "python -B -X utf8 -m ai_server --host 0.0.0.0 --port ${PORT:-8000}"]

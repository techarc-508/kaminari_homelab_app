# Kaminari gateway image.
#
# Build context is the repository root so the shared contract can be copied in:
#   docker build -f gateway/Dockerfile -t kaminari-gateway .
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    KAMINARI_CONTRACT_PATH=/srv/contracts/kf_contract.v1.json

RUN groupadd --system --gid 10001 gateway \
    && useradd --system --uid 10001 --gid gateway --home /srv/gateway gateway \
    && mkdir -p /srv/gateway /srv/contracts /data \
    && chown -R 10001:10001 /srv /data

WORKDIR /srv/gateway

COPY gateway/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY gateway/app ./app
COPY contracts /srv/contracts

USER 10001:10001

VOLUME ["/data"]
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD python -c "import sys,urllib.request; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8080/healthz', timeout=3).status == 200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080", "--proxy-headers", "--no-server-header"]

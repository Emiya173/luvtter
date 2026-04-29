"""MinIO download wrapper. Reads object_key bytes into memory (small files: scans/handwriting <30MB)."""
from __future__ import annotations

import logging
from urllib.parse import urlparse

from minio import Minio

log = logging.getLogger(__name__)


class ObjectStore:
    def __init__(self, endpoint: str, access_key: str, secret_key: str, bucket: str, secure: bool):
        # MinIO Python client 接受 host:port,不要带 scheme
        host = endpoint
        if endpoint.startswith("http://") or endpoint.startswith("https://"):
            parsed = urlparse(endpoint)
            host = parsed.netloc
            secure = parsed.scheme == "https"
        self._client = Minio(host, access_key=access_key, secret_key=secret_key, secure=secure)
        self._bucket = bucket

    def get(self, object_key: str) -> bytes:
        resp = self._client.get_object(self._bucket, object_key)
        try:
            return resp.read()
        finally:
            resp.close()
            resp.release_conn()

"""Env-driven config for the image worker."""
from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    def __init__(self):
        pass

    database_url: str
    minio_endpoint: str
    minio_access_key: str
    minio_secret_key: str
    minio_bucket: str
    minio_use_ssl: bool

    poll_seconds: float
    ocr_lang: str
    log_level: str

    @classmethod
    def from_env(cls) -> "Config":
        # psycopg 接受标准 PG URI(postgresql://user:pass@host:port/db),不要传 jdbc 形式
        return cls(
            database_url=os.environ["DATABASE_URL"],
            minio_endpoint=os.environ["MINIO_ENDPOINT"],
            minio_access_key=os.environ["MINIO_ACCESS_KEY"],
            minio_secret_key=os.environ["MINIO_SECRET_KEY"],
            minio_bucket=os.environ.get("MINIO_BUCKET", "letter"),
            minio_use_ssl=os.environ.get("MINIO_USE_SSL", "false").lower() == "true",
            poll_seconds=float(os.environ.get("POLL_SECONDS", "2")),
            ocr_lang=os.environ.get("OCR_LANG", "chi_sim+eng"),
            log_level=os.environ.get("LOG_LEVEL", "INFO"),
        )

"""Image worker entry point: poll → claim → process → finish loop."""
from __future__ import annotations

import logging
import signal
import sys
import time
import traceback

from .config import Config
from .db import Db
from .handler import process_ocr_index
from .storage import ObjectStore

log = logging.getLogger("worker")


def _setup_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )


def _install_signal_handlers(stop_flag: list[bool]) -> None:
    def _handle(signum, _frame):
        log.info("received signal=%s, draining...", signum)
        stop_flag[0] = True
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, _handle)


def main() -> int:
    cfg = Config.from_env()
    _setup_logging(cfg.log_level)
    log.info(
        "starting image-worker poll=%ss lang=%s bucket=%s",
        cfg.poll_seconds, cfg.ocr_lang, cfg.minio_bucket,
    )

    store = ObjectStore(
        endpoint=cfg.minio_endpoint,
        access_key=cfg.minio_access_key,
        secret_key=cfg.minio_secret_key,
        bucket=cfg.minio_bucket,
        secure=cfg.minio_use_ssl,
    )
    db = Db(cfg.database_url)

    stop_flag = [False]
    _install_signal_handlers(stop_flag)

    try:
        while not stop_flag[0]:
            try:
                task = db.claim_ocr_task()
            except Exception as e:
                log.error("claim failed: %s", e)
                time.sleep(cfg.poll_seconds)
                continue

            if task is None:
                time.sleep(cfg.poll_seconds)
                continue

            log.info("claimed task=%s attempts=%d/%d", task.id, task.attempts, task.max_attempts)
            try:
                process_ocr_index(task, store, db, cfg.ocr_lang)
                db.finish_done(task.id)
            except Exception as e:
                log.warning(
                    "task=%s attempt=%d/%d failed: %s",
                    task.id, task.attempts, task.max_attempts, e,
                )
                log.debug("traceback:\n%s", traceback.format_exc())
                # finish_failed_or_retry 不能再抛 —— 失败的任务也要落库,否则永远 in_progress
                try:
                    db.finish_failed_or_retry(task.id, task.attempts, task.max_attempts, str(e))
                except Exception as inner:
                    log.error("could not record failure for task=%s: %s", task.id, inner)
    finally:
        db.close()
        log.info("worker stopped")

    return 0


if __name__ == "__main__":
    sys.exit(main())

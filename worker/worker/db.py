"""Postgres access: claim / finish tasks + read letter_contents.

Mirrors the Kotlin server's protocol:
  - claim: UPDATE async_tasks SET status='in_progress', attempts++, started_at=now()
           WHERE id = (SELECT id FROM async_tasks
                       WHERE status='pending' AND scheduled_at<=now() AND task_type='ocr_index'
                       ORDER BY scheduled_at, id FOR UPDATE SKIP LOCKED LIMIT 1)
           RETURNING ...
  - finish success: status='done', finished_at=now(), last_error=NULL
  - finish failure: if attempts>=max_attempts -> 'failed', else 'pending' + scheduled_at += backoff
"""
from __future__ import annotations

import json
import logging
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Iterator, Optional
from uuid import UUID

import psycopg
from psycopg.rows import dict_row

log = logging.getLogger(__name__)


@dataclass
class Task:
    id: UUID
    task_type: str
    payload: dict
    attempts: int
    max_attempts: int


class Db:
    def __init__(self, dsn: str):
        # 单连接 + autocommit=False 默认;每个 claim/finish 是独立 transaction
        self._conn = psycopg.connect(dsn, autocommit=False, row_factory=dict_row)

    def close(self) -> None:
        try:
            self._conn.close()
        except Exception:
            pass

    @contextmanager
    def _tx(self) -> Iterator[psycopg.Cursor]:
        try:
            with self._conn.cursor() as cur:
                yield cur
            self._conn.commit()
        except Exception:
            self._conn.rollback()
            raise

    def claim_ocr_task(self) -> Optional[Task]:
        with self._tx() as cur:
            cur.execute(
                """
                UPDATE async_tasks SET
                    status = 'in_progress',
                    started_at = now(),
                    attempts = attempts + 1,
                    updated_at = now()
                WHERE id = (
                    SELECT id FROM async_tasks
                    WHERE status = 'pending'
                      AND scheduled_at <= now()
                      AND task_type = 'ocr_index'
                    ORDER BY scheduled_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING id, task_type, payload, attempts, max_attempts
                """
            )
            row = cur.fetchone()
            if not row:
                return None
            return Task(
                id=row["id"],
                task_type=row["task_type"],
                payload=row["payload"] if isinstance(row["payload"], dict) else json.loads(row["payload"]),
                attempts=row["attempts"],
                max_attempts=row["max_attempts"],
            )

    def finish_done(self, task_id: UUID) -> None:
        with self._tx() as cur:
            cur.execute(
                """
                UPDATE async_tasks SET
                    status = 'done',
                    finished_at = now(),
                    updated_at = now(),
                    last_error = NULL
                WHERE id = %s
                """,
                (task_id,),
            )

    def finish_failed_or_retry(self, task_id: UUID, attempts: int, max_attempts: int, error: str) -> None:
        """Mirror Kotlin's behavior:
        - if attempts >= max_attempts -> 'failed' (terminal)
        - else 'pending' + scheduled_at = now() + (attempts*2 + 1) seconds linear backoff
        """
        # 错误信息截断 500 字符,跟 Kotlin 端一致
        err = (error or "unknown")[:500]
        if attempts >= max_attempts:
            with self._tx() as cur:
                cur.execute(
                    """
                    UPDATE async_tasks SET
                        status = 'failed',
                        last_error = %s,
                        finished_at = now(),
                        updated_at = now()
                    WHERE id = %s
                    """,
                    (err, task_id),
                )
        else:
            backoff_seconds = attempts * 2 + 1
            with self._tx() as cur:
                cur.execute(
                    """
                    UPDATE async_tasks SET
                        status = 'pending',
                        last_error = %s,
                        scheduled_at = now() + (%s || ' seconds')::interval,
                        updated_at = now()
                    WHERE id = %s
                    """,
                    (err, backoff_seconds, task_id),
                )

    def fetch_letter_content(self, letter_id: UUID) -> Optional[dict]:
        """Return scan_object_key / handwriting_object_key + content_type for a letter, or None."""
        with self._tx() as cur:
            cur.execute(
                """
                SELECT
                    content_type,
                    scan_object_key,
                    handwriting_object_key
                FROM letter_contents
                WHERE letter_id = %s
                """,
                (letter_id,),
            )
            row = cur.fetchone()
            return row

    def update_index_text(self, letter_id: UUID, text: str) -> int:
        """Write OCR result into letter_contents.index_text. The V6 trigger maintains tsvector."""
        with self._tx() as cur:
            cur.execute(
                """
                UPDATE letter_contents SET
                    index_text = %s,
                    updated_at = now()
                WHERE letter_id = %s
                """,
                (text, letter_id),
            )
            return cur.rowcount

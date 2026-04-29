"""ocr_index task handler."""
from __future__ import annotations

import logging
from uuid import UUID

from . import ocr
from .db import Db, Task
from .storage import ObjectStore

log = logging.getLogger(__name__)

# 当扫描内容是矢量手写笔迹(JSON),Tesseract 没用,先写一个语义占位让搜索不至于命中空字符串。
# 真要识别手写笔画得另接 ML 模型。
_HANDWRITING_VECTOR_PLACEHOLDER = "[手写笔迹]"


def process_ocr_index(task: Task, store: ObjectStore, db: Db, ocr_lang: str) -> None:
    letter_id_str = task.payload.get("letter_id")
    if not letter_id_str:
        raise ValueError("ocr_index payload missing letter_id")
    letter_id = UUID(letter_id_str)

    row = db.fetch_letter_content(letter_id)
    if not row:
        raise RuntimeError(f"letter_contents row not found for letter_id={letter_id}")

    content_type = row["content_type"]
    scan_key = row.get("scan_object_key")
    hw_key = row.get("handwriting_object_key")

    object_key = scan_key or hw_key
    if not object_key:
        raise RuntimeError(
            f"letter {letter_id} has content_type={content_type} but no scan/handwriting object_key"
        )

    log.info("downloading %s for letter %s", object_key, letter_id)
    blob = store.get(object_key)

    text = _recognize(object_key, blob, ocr_lang)
    affected = db.update_index_text(letter_id, text)
    log.info(
        "ocr_index letter=%s affected=%d chars=%d preview=%r",
        letter_id, affected, len(text), text[:40],
    )


def _recognize(object_key: str, blob: bytes, ocr_lang: str) -> str:
    """Dispatch by extension — content-type isn't stored in letter_contents, so use the key suffix."""
    key_lower = object_key.lower()
    if key_lower.endswith(".pdf"):
        return ocr.recognize_pdf_bytes(blob, ocr_lang)
    if key_lower.endswith(".json"):
        # 矢量笔迹,Tesseract 不适用
        return _HANDWRITING_VECTOR_PLACEHOLDER
    # 默认按图片处理(PNG/JPEG/WebP);Pillow 能 sniff 格式
    return ocr.recognize_image_bytes(blob, ocr_lang)

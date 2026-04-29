"""OCR engine — currently Tesseract.

接口故意做小,后续可换 PaddleOCR / EasyOCR 而不影响 handler.py:
    recognize_image_bytes(...)  # 单张图(PNG/JPEG/WebP)
    recognize_pdf_bytes(...)    # PDF 多页 → 拼接文本
"""
from __future__ import annotations

import io
import logging

import pypdfium2 as pdfium
import pytesseract
from PIL import Image

log = logging.getLogger(__name__)

# Tesseract 默认会丢极短结果,这里取消阈值;dpi 提示对小图扫描更准
_PSM = 6  # 假设单一文本块,中文段落场景表现比 default(3)更稳
_OEM = 3  # default,LSTM + legacy


def _normalize(text: str) -> str:
    # 去掉行内多余空格,但保留段落换行;中文 OCR 输出常带逐字空格
    lines = []
    for raw in text.splitlines():
        compact = "".join(raw.split())
        if compact:
            lines.append(compact)
    return "\n".join(lines)


def recognize_image_bytes(data: bytes, lang: str) -> str:
    img = Image.open(io.BytesIO(data))
    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")
    config = f"--psm {_PSM} --oem {_OEM}"
    raw = pytesseract.image_to_string(img, lang=lang, config=config)
    return _normalize(raw)


def recognize_pdf_bytes(data: bytes, lang: str, dpi: int = 200, max_pages: int = 20) -> str:
    """Render each PDF page to image and OCR it. Cap pages to prevent runaway tasks."""
    pdf = pdfium.PdfDocument(data)
    try:
        page_count = min(len(pdf), max_pages)
        chunks: list[str] = []
        for i in range(page_count):
            page = pdf[i]
            try:
                bitmap = page.render(scale=dpi / 72)
                pil = bitmap.to_pil()
                config = f"--psm {_PSM} --oem {_OEM}"
                chunk = pytesseract.image_to_string(pil, lang=lang, config=config)
                chunk = _normalize(chunk)
                if chunk:
                    chunks.append(chunk)
            finally:
                page.close()
        if len(pdf) > max_pages:
            log.warning("pdf has %d pages, only OCR'd first %d", len(pdf), max_pages)
        return "\n\n".join(chunks)
    finally:
        pdf.close()

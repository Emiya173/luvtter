# 用 uv 管理 venv + 依赖,Python 版本由 .python-version + pyproject.requires-python 决定。
# 选 python:3.12-slim 当 base,而不是 ghcr.io/astral-sh/uv:python3.12-* —— 后者是
# uv 自带 Python build,这里 Tesseract 已经要从 Debian apt 装,顺便用 Debian 自带 python 减一个分发渠道。

FROM python:3.12-slim

# uv:从官方多架构镜像直接拷可执行文件,不污染 apt
COPY --from=ghcr.io/astral-sh/uv:0.5.4 /uv /uvx /usr/local/bin/

# Tesseract + 简体中文 + 英文;libgl1 是 Pillow / pypdfium2 渲染依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-chi-sim \
        tesseract-ocr-eng \
        libgl1 \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 让 uv 把 venv 放进 /app/.venv 并禁止下载新 python(我们只用镜像里的 3.12)
ENV UV_LINK_MODE=copy \
    UV_PROJECT_ENVIRONMENT=/app/.venv \
    UV_PYTHON_DOWNLOADS=never \
    UV_COMPILE_BYTECODE=1

# 先 copy 锁文件 + pyproject,锁定的依赖单独成 layer,后续改源码不用重装依赖
COPY pyproject.toml uv.lock* .python-version ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-install-project --no-dev

# 再 copy 应用代码 —— 这一层经常变,放最后
COPY worker /app/worker

# 把 venv 的 bin 加进 PATH,这样 `python` 默认走 venv
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

CMD ["python", "-m", "worker.main"]

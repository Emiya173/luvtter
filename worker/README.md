# Luvtter Image Worker (Stage 4)

Python 进程,消费 `async_tasks.task_type = 'ocr_index'`,从 MinIO 拉扫描 / 手写图,
跑 Tesseract OCR,把识别文本写回 `letter_contents.index_text`。V6 的 `letter_bigram`
触发器会自动重建 `index_tsv`,扫描信因此可被全文搜索命中。

## 协议

跟 Kotlin 端 `AsyncTaskRunner` 完全一致(同一张表 `async_tasks`,同一组列):

- 认领:`UPDATE ... WHERE id = (SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING ...`
- 成功:`status='done', finished_at=now(), last_error=NULL`
- 失败重试:`status='pending', scheduled_at=now() + ((attempts*2+1) seconds), last_error=...`
- 失败终态:达到 `max_attempts` 后 `status='failed', finished_at=now()`

跟 Kotlin runner **共用同一张表**,通过 `task_type` 过滤天然分流:Python worker 只看
`task_type='ocr_index'`,Kotlin runner 在 `tasks.useStubOcr=false` 时不再认领该类型,
不会撞车。

## 工具链

- Python 3.12(`.python-version` 锁定)
- 包管理用 [uv](https://docs.astral.sh/uv/) —— 解析快、用 lockfile 保证可复现
- `pyproject.toml` 列依赖,`uv.lock` 提交进仓库

## 部署(docker-compose)

```bash
docker compose up -d image-worker
docker compose logs -f image-worker

# 让服务端把 ocr_index 让出来:
# 启动服务端时设 USE_STUB_OCR=false (application.yaml 默认 true 是为了让单元测试能跑)
```

镜像构建走 uv,首次构建需要解析依赖;之后 `pyproject.toml` 不变时 layer 命中,
仅源码改动会重建最后一层。

## 本地开发

第一次:

```bash
cd worker
# 生成 / 更新 lockfile
uv lock

# 创建 venv 并装依赖(uv 会按 .python-version 拉对应 Python 解释器)
uv sync
```

之后日常:

```bash
# 在 uv 管理的 venv 里运行
uv run python -m worker.main

# 或激活 venv 后正常 python
source .venv/bin/activate
python -m worker.main
```

加 / 删 / 升依赖:

```bash
uv add minio        # 加运行时依赖,自动写 pyproject + 重新 lock
uv add --dev pytest # 开发依赖
uv remove minio
uv lock --upgrade   # 全量升级到 lock 允许的最新版
```

本机要装 Tesseract(uv 不管 apt 包):

- Arch: `pacman -S tesseract tesseract-data-chi_sim tesseract-data-eng`
- Debian/Ubuntu: `apt install tesseract-ocr tesseract-ocr-chi-sim tesseract-ocr-eng`

环境变量(本地跑时):

```bash
export DATABASE_URL=postgresql://letter:letter@localhost:5432/letter
export MINIO_ENDPOINT=localhost:9000
export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin
export MINIO_BUCKET=letter
export MINIO_USE_SSL=false
export OCR_LANG=chi_sim+eng
uv run python -m worker.main
```

## 配置(env)

| 变量 | 默认 | 说明 |
|---|---|---|
| `DATABASE_URL` | — 必填 | psycopg URI:`postgresql://user:pass@host:port/db` |
| `MINIO_ENDPOINT` | — 必填 | `host:port`(不带 scheme) |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | — 必填 | |
| `MINIO_BUCKET` | `letter` | |
| `MINIO_USE_SSL` | `false` | 部署 HTTPS MinIO 时设 `true` |
| `POLL_SECONDS` | `2` | 空轮询间隔 |
| `OCR_LANG` | `chi_sim+eng` | 多语言用 `+` 拼接 |
| `LOG_LEVEL` | `INFO` | |

## 内容路由

`letter_contents.scan_object_key` 优先,其次 `handwriting_object_key`。识别引擎按 object key 后缀分发:

- `*.pdf` → pypdfium2 渲染每页 → Tesseract,取前 20 页
- `*.json` → 矢量手写笔迹,Tesseract 无效,写占位 `[手写笔迹]`(为搜索留个 token)
- 其他(图片) → 直接 Tesseract

## 后续

- 替换 Tesseract 为 PaddleOCR(更适合中文手写);引擎抽象在 `worker/ocr.py`,只需补一个新模块和对应 `_recognize` 分支
- 透视矫正 / 笔迹提取(`opencv-python` + 模型)— 路线图 §12 长期项
- 真正的手写矢量识别(JSON 笔画 → 文字)

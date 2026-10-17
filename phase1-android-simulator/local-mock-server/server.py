import hashlib
import json
import logging
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Annotated

from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse

APP_NAME = "Local Telegram API Simulator"
EXPECTED_TOKEN = "TEST_BOT_TOKEN"
EXPECTED_CHAT_ID = "TEST_CHAT_ID"
RESPONSE_MODE = os.getenv("MOCK_RESPONSE_MODE", "success").lower()
ALLOWED_MODES = {"success", "server_error", "rate_limit"}
MAX_IMAGE_BYTES = 5 * 1024 * 1024
LOG_PATH = Path(os.getenv("MOCK_LOG_PATH", "events.jsonl"))

logging.basicConfig(level=logging.INFO, format="%(message)s")
logger = logging.getLogger("mock-telegram")
app = FastAPI(title=APP_NAME, description="Local-only simulation; never forwards requests.", version="1.0.0")

if RESPONSE_MODE not in ALLOWED_MODES:
    raise RuntimeError(f"Invalid MOCK_RESPONSE_MODE: {RESPONSE_MODE}")


def record_event(event: dict) -> None:
    safe_event = {"timestamp": datetime.now(timezone.utc).isoformat(), **event}
    line = json.dumps(safe_event, ensure_ascii=True)
    logger.info(line)
    with LOG_PATH.open("a", encoding="utf-8") as output:
        output.write(line + "\n")


@app.get("/health")
async def health():
    return {"service": APP_NAME, "status": "ok", "external_forwarding": False, "response_mode": RESPONSE_MODE}


@app.post("/bot{token}/sendPhoto")
async def send_photo(
    request: Request,
    token: str,
    chat_id: Annotated[str, Form()],
    photo: Annotated[UploadFile, File()],
    caption: Annotated[str | None, Form()] = None,
):
    request_id = hashlib.sha256(
        f"{time.time_ns()}:{request.client.host if request.client else 'unknown'}".encode()
    ).hexdigest()[:16]
    client = request.client.host if request.client else None

    if token != EXPECTED_TOKEN:
        record_event({"event": "upload_rejected", "request_id": request_id, "method": "POST",
                      "path": "/bot<redacted>/sendPhoto", "client": client, "status": 401,
                      "reason": "invalid_placeholder_token"})
        raise HTTPException(status_code=401, detail="Invalid test token")

    if chat_id != EXPECTED_CHAT_ID:
        record_event({"event": "upload_rejected", "request_id": request_id, "method": "POST",
                      "path": "/bot<redacted>/sendPhoto", "status": 400,
                      "reason": "invalid_placeholder_chat_id"})
        raise HTTPException(status_code=400, detail="Invalid test chat ID")

    filename = photo.filename or ""
    if not re.fullmatch(r"synthetic_[0-9]{3,4}\.jpg", filename):
        record_event({"event": "upload_rejected", "request_id": request_id, "method": "POST",
                      "path": "/bot<redacted>/sendPhoto", "status": 400,
                      "reason": "unexpected_synthetic_filename"})
        raise HTTPException(status_code=400, detail="Unexpected test filename")

    data = await photo.read(MAX_IMAGE_BYTES + 1)
    await photo.close()
    if not data or len(data) > MAX_IMAGE_BYTES:
        status = 413 if len(data) > MAX_IMAGE_BYTES else 400
        record_event({"event": "upload_rejected", "request_id": request_id, "method": "POST",
                      "path": "/bot<redacted>/sendPhoto", "status": status,
                      "reason": "invalid_image_size", "size_bytes": len(data)})
        raise HTTPException(status_code=status, detail="Invalid image size")

    if not data.startswith(b"\xff\xd8\xff"):
        record_event({"event": "upload_rejected", "request_id": request_id, "method": "POST",
                      "path": "/bot<redacted>/sendPhoto", "status": 400,
                      "reason": "invalid_jpeg_signature"})
        raise HTTPException(status_code=400, detail="Expected synthetic JPEG")

    metadata = {"event": "upload_received", "request_id": request_id, "method": "POST",
                "path": "/bot<redacted>/sendPhoto", "client": client, "content_type": photo.content_type,
                "filename": filename, "size_bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                "caption_present": bool(caption), "response_mode": RESPONSE_MODE}
    del data
    record_event(metadata)

    if RESPONSE_MODE == "server_error":
        return JSONResponse(status_code=500, content={"ok": False, "description": "Simulated internal server error", "request_id": request_id})
    if RESPONSE_MODE == "rate_limit":
        return JSONResponse(status_code=429, headers={"Retry-After": "5"},
                             content={"ok": False, "description": "Simulated rate limit", "request_id": request_id})
    return {"ok": True, "result": {"message_id": int(time.time()) % 2_000_000_000,
            "chat": {"id": 0, "type": "private"}, "photo": [{"file_id": "SIMULATED_FILE_ID"}]},
            "request_id": request_id}

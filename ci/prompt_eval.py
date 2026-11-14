"""Runs the app's real prompts against Gemini on the files in samples/ and writes a report.

Needs the GEMINI_API_KEY_CI secret. Uses the same prompt and schema files the app ships
(app/src/main/assets/prompts), the same image size (2048 px JPEG, quality 85) and the same
"retry without schema on HTTP 400" rule, so results here predict what the phone will get.
"""
import base64
import glob
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request

from PIL import Image, ImageDraw

KEY = os.environ.get("GEMINI_API_KEY_CI", "").strip()
MODELS = [m.strip() for m in os.environ.get("MODELS", "gemini-3.5-flash,gemini-2.5-flash").split(",") if m.strip()]
OUT = sys.argv[1] if len(sys.argv) > 1 else "prompt-eval-out"
PROMPTS = "app/src/main/assets/prompts"
BASE = "https://generativelanguage.googleapis.com/v1beta"
COLORS = ["#FFD166", "#06D6A0", "#4CC9F0", "#F78C6B", "#C77DFF", "#FF6B9A", "#B5E48C", "#FFFFFF"]


def read(name):
    with open(os.path.join(PROMPTS, name), encoding="utf-8") as f:
        return f.read()


def call(model, prompt, schema, jpeg=None):
    parts = []
    if jpeg is not None:
        parts.append({"inline_data": {"mime_type": "image/jpeg", "data": base64.b64encode(jpeg).decode()}})
    parts.append({"text": prompt})

    def post(with_schema):
        cfg = {"temperature": 0.1, "responseMimeType": "application/json"}
        if with_schema:
            cfg["responseSchema"] = schema
        body = json.dumps({"contents": [{"role": "user", "parts": parts}], "generationConfig": cfg}).encode()
        req = urllib.request.Request(f"{BASE}/models/{model}:generateContent", data=body,
                                     headers={"x-goog-api-key": KEY, "Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=180) as r:
            return json.load(r)

    started = time.time()
    note = "schema accepted"
    try:
        data = post(True)
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")[:400]
        if e.code != 400:
            return None, f"HTTP {e.code}: {detail}", time.time() - started
        note = f"schema rejected (HTTP 400: {detail[:160]}); retried without schema"
        try:
            data = post(False)
        except urllib.error.HTTPError as e2:
            return None, f"HTTP {e2.code}: {e2.read().decode(errors='replace')[:400]}", time.time() - started
    cands = data.get("candidates") or []
    if not cands:
        return None, f"no candidates: {json.dumps(data)[:300]}", time.time() - started
    text = "".join(p.get("text", "") for p in cands[0].get("content", {}).get("parts", []) if not p.get("thought"))
    return text, note, time.time() - started


def as_json(text):
    start, end = text.find("{"), text.rfind("}")
    return json.loads(text[start:end + 1])


def prepare(path):
    img = Image.open(path).convert("RGB")
    scale = min(1.0, 2048 / max(img.size))
    if scale < 1:
        img = img.resize((round(img.width * scale), round(img.height * scale)))
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=85)
    return img, buf.getvalue()


def main():
    os.makedirs(OUT, exist_ok=True)
    lines = ["# Prompt evaluation", ""]
    if not KEY:
        lines.append("GEMINI_API_KEY_CI is not set, so nothing was run. Add it under Settings → Secrets and variables → Actions.")
        open(os.path.join(OUT, "report.md"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
        return
    year = time.gmtime().tm_year
    scan_prompt, scan_schema = read("scan_prompt.txt"), json.loads(read("scan_schema.json"))
    paste_prompt, paste_schema = read("paste_prompt.txt"), json.loads(read("paste_schema.json"))
    est_prompt, est_schema = read("estimate_prompt.txt"), json.loads(read("estimate_schema.json"))
    first_wine = None

    for model in MODELS:
        lines += [f"## Model `{model}`", ""]
        for path in sorted(glob.glob("samples/*.png") + glob.glob("samples/*.jpg") + glob.glob("samples/*.jpeg")):
            name = os.path.splitext(os.path.basename(path))[0]
            img, jpeg = prepare(path)
            text, note, secs = call(model, scan_prompt, scan_schema, jpeg)
            lines += [f"### Scan `{os.path.basename(path)}`: {secs:.1f}s, {note}", ""]
            if text is None:
                lines += ["FAILED", ""]
                continue
            open(os.path.join(OUT, f"{name}.{model}.scan.json"), "w", encoding="utf-8").write(text)
            try:
                bottles = as_json(text).get("bottles", [])
            except Exception as e:  # noqa: BLE001
                lines += [f"Unparseable answer: {e}", "", "```", text[:600], "```", ""]
                continue
            draw = ImageDraw.Draw(img)
            lines += ["| # | producer | wine | vintage | type | sticker | legible | conf | box |", "|---|---|---|---|---|---|---|---|---|"]
            for i, b in enumerate(bottles, 1):
                box = b.get("box_2d") or []
                ok = len(box) == 4 and all(isinstance(v, (int, float)) and 0 <= v <= 1000 for v in box) and box[0] < box[2] and box[1] < box[3]
                if ok:
                    y0, x0, y1, x1 = (v / 1000 for v in box)
                    color = COLORS[(i - 1) % len(COLORS)]
                    draw.rectangle([x0 * img.width, y0 * img.height, x1 * img.width, y1 * img.height], outline=color, width=6)
                    draw.text((x0 * img.width + 10, y0 * img.height + 6), str(i), fill=color)
                if first_wine is None and b.get("producer"):
                    first_wine = b
                lines.append(f"| {i} | {b.get('producer')} | {b.get('wine_name')} | {b.get('vintage')} | {b.get('wine_type')} | "
                             f"{b.get('sticker_text')} | {b.get('label_legible')} | {b.get('confidence')} | {'ok' if ok else 'INVALID ' + str(box)} |")
            lines.append("")
            img.thumbnail((1100, 1100))
            img.save(os.path.join(OUT, f"{name}.{model}.boxes.jpg"), "JPEG", quality=80)

        for path in sorted(glob.glob("samples/paste/*.txt")):
            pasted = open(path, encoding="utf-8").read()
            text, note, secs = call(model, paste_prompt + f"\n\nCurrent year: {year}\n\nTEXT:\n" + pasted[:12000], paste_schema)
            lines += [f"### Paste `{os.path.basename(path)}`: {secs:.1f}s, {note}", "", "```json", (text or "FAILED")[:2500], "```", ""]

        if first_wine is not None:
            wine = (f"\n\nCurrent year: {year}\n\nWINE:\nProducer: {first_wine.get('producer')}\nWine: {first_wine.get('wine_name')}\n"
                    f"Vintage: {first_wine.get('vintage') or 'non-vintage'}\nRegion: {first_wine.get('appellation_region')}\n"
                    f"Country: {first_wine.get('country')}\nType: {first_wine.get('wine_type')}\n"
                    f"Grapes: {', '.join(first_wine.get('primary_grapes') or [])}\nBottle size: 750 ml\n")
            text, note, secs = call(model, est_prompt + wine, est_schema)
            lines += [f"### Estimate for {first_wine.get('producer')} {first_wine.get('wine_name')}: {secs:.1f}s, {note}", "",
                      "```json", (text or "FAILED")[:2500], "```", ""]

    open(os.path.join(OUT, "report.md"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()

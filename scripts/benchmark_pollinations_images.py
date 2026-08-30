#!/usr/bin/env python3
"""Run a reproducible UCTale Pollinations image benchmark.

Requires POLLINATIONS_TOKEN in the environment. Results are written to a local
output directory and are intentionally not committed automatically.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import pathlib
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

MODELS = ("flux", "zimage", "dreamshaper")
SIZES = ((768, 432), (1024, 576))
FIXTURES = (
    ("indoor-single", "subjects: office worker; objects: briefcase; setting: subway platform"),
    ("bright-indoor", "subjects: student; objects: notebook; setting: sunlit classroom"),
    ("dark-outdoor", "subjects: detective; objects: umbrella; setting: rainy alley at night"),
    ("monster", "subjects: giant wolf; setting: forest clearing"),
    ("party", "subjects: knight, mage; objects: broken shield; setting: castle hall"),
    ("horde", "subjects: zombie horde; objects: shopping cart; setting: abandoned mall"),
    ("rooftop", "subjects: sniper; objects: radio; setting: rooftop at night"),
    ("npc", "subjects: elderly npc; objects: tea cup; setting: quiet apartment"),
    ("combat", "subjects: fighter, armored monster; objects: spear; setting: underground arena"),
    ("key-item", "objects: glowing relic; setting: ancient shrine"),
    ("travel", "subjects: traveler; objects: map; setting: snowy mountain pass"),
    ("bright-outdoor", "subjects: merchant, child; objects: fruit stand; setting: bright seaside town"),
    ("laboratory", "subjects: scientist; objects: sealed capsule; setting: dark laboratory"),
    ("battlefield", "subjects: soldier, dragon; objects: banner; setting: burning battlefield"),
    ("transit", "subjects: passenger, conductor; objects: ticket; setting: train interior"),
    ("ruins", "subjects: explorer; objects: ancient key; setting: desert ruins"),
)
STYLE = (
    "atmosphere: dramatic storybook scene; "
    "composition: clear focal point, readable silhouettes, cinematic depth; "
    "style[uctale-charcoal-v1]: rough charcoal sketch, high contrast black and white, "
    "gritty paper texture, expressive pencil strokes, no colors, story concept art"
)
FAIL_FAST_STATUSES = {401, 402, 403}
DEFAULT_HEADERS = {
    "Authorization": "",
    "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36 UCTaleBenchmark/1.1",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="build/pollinations-benchmark")
    parser.add_argument("--seed", type=int, default=20260830)
    parser.add_argument("--timeout", type=int, default=180)
    return parser.parse_args()


def build_request(token: str, url: str) -> urllib.request.Request:
    headers = dict(DEFAULT_HEADERS)
    headers["Authorization"] = f"Bearer {token}"
    return urllib.request.Request(url, headers=headers)


def request_image(token: str, prompt: str, model: str, width: int, height: int, seed: int, timeout: int):
    encoded = urllib.parse.quote(prompt, safe="")
    query = urllib.parse.urlencode({
        "model": model,
        "width": width,
        "height": height,
        "seed": seed,
        "safe": "true",
    })
    url = f"https://gen.pollinations.ai/image/{encoded}?{query}"
    req = build_request(token, url)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            body = response.read()
            return {
                "status": response.status,
                "latency_ms": round((time.perf_counter() - started) * 1000),
                "mime": response.headers.get_content_type(),
                "bytes": len(body),
                "body": body,
                "error": "",
                "error_detail": "",
            }
    except urllib.error.HTTPError as exc:
        raw_detail = exc.read()
        detail = raw_detail.decode("utf-8", errors="replace") if raw_detail else ""
        if detail:
            detail = detail.replace(prompt, "<redacted-prompt>")
            detail = detail.replace(encoded, "<redacted-prompt>")[:1000]
        return {
            "status": exc.code,
            "latency_ms": round((time.perf_counter() - started) * 1000),
            "mime": exc.headers.get_content_type() if exc.headers else "",
            "bytes": 0,
            "body": b"",
            "error": f"HTTP_{exc.code}",
            "error_detail": detail,
        }
    except Exception as exc:  # benchmark diagnostic only
        return {
            "status": 0,
            "latency_ms": round((time.perf_counter() - started) * 1000),
            "mime": "",
            "bytes": 0,
            "body": b"",
            "error": exc.__class__.__name__,
            "error_detail": str(exc),
        }


def summarize(rows: list[dict]) -> list[dict]:
    summary = []
    for model in MODELS:
        for width, height in SIZES:
            group = [r for r in rows if r["model"] == model and r["width"] == width and r["height"] == height]
            latencies = sorted(r["latency_ms"] for r in group if r["status"] == 200)
            success = len(latencies)
            p95 = latencies[min(len(latencies) - 1, max(0, round(len(latencies) * 0.95) - 1))] if latencies else None
            summary.append({
                "model": model,
                "size": f"{width}x{height}",
                "requests": len(group),
                "successes": success,
                "error_rate": round(1 - (success / len(group)), 4) if group else None,
                "avg_latency_ms": round(statistics.mean(latencies)) if latencies else None,
                "p95_latency_ms": p95,
            })
    return summary


def write_outputs(output: pathlib.Path, rows: list[dict]):
    if rows:
        with (output / "raw.csv").open("w", newline="", encoding="utf-8") as file:
            writer = csv.DictWriter(file, fieldnames=rows[0].keys())
            writer.writeheader()
            writer.writerows(rows)
        (output / "summary.json").write_text(json.dumps(summarize(rows), ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    args = parse_args()
    token = os.getenv("POLLINATIONS_TOKEN")
    if not token:
        raise SystemExit("POLLINATIONS_TOKEN is required; the benchmark never writes the token to output.")

    output = pathlib.Path(args.output)
    images = output / "images"
    images.mkdir(parents=True, exist_ok=True)
    rows: list[dict] = []

    for fixture_index, (fixture_id, scene) in enumerate(FIXTURES):
        prompt = f"{scene}; {STYLE}"
        seed = args.seed + fixture_index
        for model in MODELS:
            for width, height in SIZES:
                result = request_image(token, prompt, model, width, height, seed, args.timeout)
                filename = ""
                if result["status"] == 200 and result["body"]:
                    suffix = ".png" if result["mime"] == "image/png" else ".jpg"
                    filename = f"{fixture_id}__{model}__{width}x{height}{suffix}"
                    (images / filename).write_bytes(result.pop("body"))
                else:
                    result.pop("body", None)
                row = {
                    "fixture": fixture_id,
                    "model": model,
                    "width": width,
                    "height": height,
                    "seed": seed,
                    "image": filename,
                    **result,
                }
                rows.append(row)
                print(f"{fixture_id:16} {model:12} {width}x{height} status={result['status']} latency={result['latency_ms']}ms")
                if row["status"] in FAIL_FAST_STATUSES:
                    write_outputs(output, rows)
                    print("Fail-fast: authentication/billing/permission style error encountered.", file=sys.stderr)
                    print(row["error_detail"] or row["error"], file=sys.stderr)
                    return 2

    write_outputs(output, rows)
    print(f"Results written to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

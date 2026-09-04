#!/usr/bin/env python3
"""Replace questionable premium linear entries with verified public/free feeds."""

from __future__ import annotations

import json
import statistics
import time
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urljoin

import requests

M3U_PATH = Path("tubo/taiwan_100_50pct.m3u")
REPORT_PATH = Path("tubo/taiwan_100_50pct_report.json")
ROUNDS = 4
REQUIRED = 2
UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15"

REMOVE_NAMES = ("Willow Sports", "NHK World Premium")

REPLACEMENTS = [
    {
        "name": "NHK World-Japan",
        "group": "華語・亞洲",
        "tvg_id": "NHKWorldJapan.jp@SD",
        "urls": [
            "https://masterpl.hls.nhkworld.jp/hls/w/live/smarttv.m3u8",
            "https://media-tyo.hls.nhkworld.jp/hls/w/live/master.m3u8",
        ],
    },
    {
        "name": "Red Bull TV",
        "group": "國際｜運動",
        "tvg_id": "RedBullTV.at@EUMENA",
        "urls": [
            "https://3ea22335.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWdiX1JlZEJ1bGxUVl9ITFM/playlist.m3u8",
            "https://769a97d9.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWV1X1JlZEJ1bGxUVl9ITFM/playlist.m3u8",
        ],
    },
    {
        "name": "FIFA+",
        "group": "國際｜運動",
        "tvg_id": "FIFAPlus.uk@English",
        "urls": [
            "https://ba3e2e93.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/U2Ftc3VuZy1nYl9GSUZBUGx1c0VuZ2xpc2hfSExT/playlist.m3u8",
            "https://d2w9q46ikgrcwx.cloudfront.net/v1/sysdata_s_p_a_fifa_7/samsungheadend_us/latest/main/hls/playlist.m3u8",
        ],
    },
]


@dataclass
class ProbeResult:
    url: str
    passes: int = 0
    times: list[float] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


def fetch_manifest(session: requests.Session, url: str, depth: int = 0) -> tuple[str, str]:
    if depth > 3:
        raise ValueError("manifest nesting too deep")
    response = session.get(url, timeout=(4, 9), allow_redirects=True)
    response.raise_for_status()
    text = response.content.decode("utf-8", errors="replace")
    if "#EXTM3U" not in text[:1200]:
        raise ValueError("not HLS")
    base = response.url
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    variants: list[tuple[int, str]] = []
    for index, line in enumerate(lines[:-1]):
        if line.startswith("#EXT-X-STREAM-INF") and not lines[index + 1].startswith("#"):
            bandwidth = 999_999_999
            for token in line.split(","):
                if token.startswith("BANDWIDTH="):
                    try:
                        bandwidth = int(token.split("=", 1)[1])
                    except ValueError:
                        pass
            variants.append((bandwidth, urljoin(base, lines[index + 1])))
    if variants:
        return fetch_manifest(session, min(variants)[1], depth + 1)
    return text, base


def probe_once(url: str) -> tuple[bool, float, str]:
    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Accept": "*/*"})
    start = time.monotonic()
    try:
        manifest, base = fetch_manifest(session, url)
        segments = [urljoin(base, line.strip()) for line in manifest.splitlines()
                    if line.strip() and not line.strip().startswith("#")]
        if not segments:
            raise ValueError("no segment")
        received = 0
        with session.get(segments[-1], timeout=(4, 9), allow_redirects=True,
                         headers={"User-Agent": UA, "Range": "bytes=0-98303"}, stream=True) as response:
            response.raise_for_status()
            for chunk in response.iter_content(16384):
                if chunk:
                    received += len(chunk)
                if received >= 32768:
                    break
        if received < 1024:
            raise ValueError("segment too small")
        return True, time.monotonic() - start, ""
    except Exception as exc:  # noqa: BLE001
        return False, time.monotonic() - start, f"{type(exc).__name__}: {str(exc)[:180]}"


def choose_url(options: list[str]) -> ProbeResult:
    results = [ProbeResult(url=url) for url in options]
    for _ in range(ROUNDS):
        for result in results:
            ok, elapsed, error = probe_once(result.url)
            result.times.append(elapsed)
            if ok:
                result.passes += 1
            else:
                result.errors.append(error)
    valid = [result for result in results if result.passes >= REQUIRED]
    if not valid:
        raise RuntimeError(f"No replacement passed {REQUIRED}/{ROUNDS}: {results}")
    valid.sort(key=lambda result: (-result.passes, statistics.median(result.times)))
    return valid[0]


def parse_blocks(text: str) -> tuple[list[str], list[list[str]]]:
    header: list[str] = []
    blocks: list[list[str]] = []
    current: list[str] = []
    for line in text.splitlines():
        if line.startswith("#EXTINF"):
            if current:
                blocks.append(current)
            current = [line]
        elif current:
            current.append(line)
        else:
            header.append(line)
    if current:
        blocks.append(current)
    return header, blocks


def block_name(block: list[str]) -> str:
    return block[0].rsplit(",", 1)[-1].split(" [", 1)[0].strip()


def main() -> None:
    header, blocks = parse_blocks(M3U_PATH.read_text(encoding="utf-8"))
    kept_blocks = [block for block in blocks if not any(term.lower() in block_name(block).lower() for term in REMOVE_NAMES)]
    removed_names = [block_name(block) for block in blocks if block not in kept_blocks]

    report = json.loads(REPORT_PATH.read_text(encoding="utf-8"))
    report_rows = [row for row in report.get("channels", [])
                   if not any(term.lower() in row.get("name", "").lower() for term in REMOVE_NAMES)]

    replacement_rows = []
    for replacement in REPLACEMENTS:
        result = choose_url(replacement["urls"])
        median = statistics.median(result.times)
        speed = "快速" if median <= 4 else "一般" if median <= 9 else "慢速"
        block = [
            f'#EXTINF:-1 tvg-id="{replacement["tvg_id"]}" group-title="{replacement["group"]}｜{speed}",{replacement["name"]} [{result.passes}/{ROUNDS}・{speed}]',
            "#EXTVLCOPT:http-user-agent=Mozilla/5.0",
            result.url,
        ]
        kept_blocks.append(block)
        replacement_rows.append({
            "name": replacement["name"],
            "group": replacement["group"],
            "url": result.url,
            "source": "public replacement quality pass",
            "seed": False,
            "passes": result.passes,
            "rounds": ROUNDS,
            "availability_percent": round(result.passes / ROUNDS * 100),
            "median_start_seconds": round(median, 2),
            "distinct_segments": 1,
            "errors": result.errors,
        })

    if len(kept_blocks) != 100:
        raise RuntimeError(f"Expected 100 entries after finalization, got {len(kept_blocks)}; removed={removed_names}")

    output_lines = header + [line for block in kept_blocks for line in block]
    M3U_PATH.write_text("\n".join(output_lines).rstrip() + "\n", encoding="utf-8")

    report_rows.extend(replacement_rows)
    counts: dict[str, int] = {}
    for row in report_rows:
        group = row.get("group", "其他")
        counts[group] = counts.get(group, 0) + 1
    report["channels"] = report_rows
    report["selected_count"] = len(report_rows)
    report["category_counts"] = counts
    report["final_quality_pass"] = {
        "removed": removed_names,
        "added": [row["name"] for row in replacement_rows],
        "replacement_threshold": f">={REQUIRED}/{ROUNDS}",
    }
    REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report["final_quality_pass"], ensure_ascii=False))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Build a Taiwan-first IPTV playlist of up to 100 channels.

Policy:
- Keep the user's previously tested Taiwan list as the first-priority seed.
- Expand with public/free FAST feeds from iptv-org country/language/category lists.
- Probe each URL four times and keep it after at least two successful probes.
- Exclude sexually explicit/adult feeds and obvious subscription linear channels.
- Mature but non-explicit FAST content (crime, action, South Park, boxing, etc.)
  is allowed and placed in a separate group.
"""

from __future__ import annotations

import concurrent.futures
import json
import re
import statistics
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable
from urllib.parse import urljoin, urlparse

import requests

SEED_URL = "https://raw.githubusercontent.com/LaserJackyWu/ai-rider/tubo-playlist/tubo/taiwan_relaxed_verified.m3u"

PUBLIC_SOURCES = [
    ("台灣", "https://iptv-org.github.io/iptv/countries/tw.m3u"),
    ("華語", "https://iptv-org.github.io/iptv/languages/zho.m3u"),
    ("香港", "https://iptv-org.github.io/iptv/countries/hk.m3u"),
    ("新加坡", "https://iptv-org.github.io/iptv/countries/sg.m3u"),
    ("日本", "https://iptv-org.github.io/iptv/countries/jp.m3u"),
    ("韓國", "https://iptv-org.github.io/iptv/countries/kr.m3u"),
    ("新聞", "https://iptv-org.github.io/iptv/categories/news.m3u"),
    ("紀錄", "https://iptv-org.github.io/iptv/categories/documentary.m3u"),
    ("動畫", "https://iptv-org.github.io/iptv/categories/animation.m3u"),
    ("兒童", "https://iptv-org.github.io/iptv/categories/kids.m3u"),
    ("電影", "https://iptv-org.github.io/iptv/categories/movies.m3u"),
    ("影集", "https://iptv-org.github.io/iptv/categories/series.m3u"),
    ("娛樂", "https://iptv-org.github.io/iptv/categories/entertainment.m3u"),
    ("音樂", "https://iptv-org.github.io/iptv/categories/music.m3u"),
    ("運動", "https://iptv-org.github.io/iptv/categories/sports.m3u"),
    ("生活", "https://iptv-org.github.io/iptv/categories/lifestyle.m3u"),
    ("旅遊", "https://iptv-org.github.io/iptv/categories/travel.m3u"),
    ("科學", "https://iptv-org.github.io/iptv/categories/science.m3u"),
    ("教育", "https://iptv-org.github.io/iptv/categories/education.m3u"),
]

# Established broadcaster/CDN/FAST delivery domains. Subdomains are accepted.
TRUSTED_SUFFIXES = (
    "akamaized.net", "akamaihd.net", "cloudfront.net", "wurl.com",
    "amagi.tv", "amagi.com", "playout.now", "playouts.now",
    "tubi.io", "jmp2.uk", "pluto.tv", "rakuten.tv", "brightcove.com",
    "bcovlive.io", "streamlock.net", "streamingfast.net", "vgcdn.net",
    "dps.live", "telvue.com", "cablecast.tv", "azureedge.net",
    "windows.net", "livestream.com", "ntdtv.com", "suprememastertv.com",
    "ccdntech.com", "dalitv.com.tw", "nhkworld.jp", "france24.com",
    "getaj.net", "thehlive.com", "alarabiya.net", "bozztv.com",
    "redbull.com", "nasa.gov", "reuters.com", "euronews.com",
    "dw.com", "dwcdn.net", "streaming.media.ccc.de", "mediacp.eu",
    "cdnvideo.ru", "streamingcdn.net", "streamingvideo.net",
)

EXPLICIT_DENY = (
    "成人", "情色", "色情", "18+", "18禁", "xxx", "porn", "porno",
    "playboy", "hustler", "redlight", "brazzers", "private spice",
    "松視", "松视", "潘朵拉", "潘朵啦", "彩虹頻道", "彩虹频道",
    "香蕉台", "av頻道", "av频道", "erotic", "sex tv", "sextv",
)

PREMIUM_DENY = (
    "hbo ", "hbo_", "cinemax", "disney channel", "disney junior",
    "espn", "bein sports", "fox sports", "star movies", "衛視電影",
    "卫视电影", "緯來", "纬来", "龍華", "龙华", "博斯", "愛爾達",
    "爱尔达", "靖天", "靖洋", "東森電影", "东森电影", "東森洋片",
    "东森洋片", "animax asia", "cartoon network asia",
)

MATURE_WORDS = (
    "south park", "crime", "true crime", "action", "thriller", "horror",
    "boxing", "wrestling", "fight", "fear factor", "cops", "forensic",
    "mystery", "investigation", "court", "jail", "prison", "war",
)

TAIWAN_WORDS = (
    "台視", "台视", "中視", "中视", "華視", "华视", "民視", "民视",
    "公視", "公视", "三立", "東森", "东森", "tvbs", "中天", "大立",
    "大愛", "大爱", "唯心", "原住民", "客家", "momo", "taiwan",
    "台灣", "台湾", "新唐人亞太", "新唐人亚太", "good tv", "cgntv",
)

UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15"
CONNECT_TIMEOUT = 7
READ_TIMEOUT = 14
ROUNDS = 4
REQUIRED = 2
ROUND_GAP = 7
WORKERS = 28
MAX_TEST_CANDIDATES = 360
TARGET = 100


@dataclass
class Channel:
    name: str
    url: str
    source: str
    group: str = ""
    tvg_id: str = ""
    seed: bool = False
    passes: int = 0
    times: list[float] = field(default_factory=list)
    segments: set[str] = field(default_factory=set)
    errors: list[str] = field(default_factory=list)


def clean(value: str) -> str:
    value = re.sub(r"\[[^\]]*(?:\d/\d|啟動|快速|慢速)[^\]]*\]", "", value)
    return re.sub(r"\s+", " ", value).strip(" ,|-_")


def get_attr(line: str, key: str) -> str:
    match = re.search(rf'{re.escape(key)}="([^"]*)"', line, re.I)
    return match.group(1).strip() if match else ""


def parse_m3u(text: str, source: str, seed: bool = False) -> list[Channel]:
    channels: list[Channel] = []
    extinf = ""
    for raw in text.replace("\r", "").split("\n"):
        line = raw.strip().lstrip("\ufeff")
        if not line:
            continue
        if line.startswith("#EXTINF"):
            extinf = line
            continue
        if line.startswith("#"):
            continue
        if extinf and line.lower().startswith(("http://", "https://")):
            channels.append(Channel(
                name=clean(extinf.rsplit(",", 1)[-1]),
                url=line,
                source=source,
                group=get_attr(extinf, "group-title"),
                tvg_id=get_attr(extinf, "tvg-id"),
                seed=seed,
            ))
            extinf = ""
    return channels


def fetch_source(session: requests.Session, label: str, url: str, seed: bool = False) -> list[Channel]:
    response = session.get(url, timeout=(8, 35), allow_redirects=True)
    response.raise_for_status()
    text = response.content.decode("utf-8", errors="replace")
    parsed = parse_m3u(text, label, seed=seed)
    print(f"SOURCE {label}: {len(parsed)} entries")
    return parsed


def host_is_trusted(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower()
    if not host:
        return False
    if re.fullmatch(r"\d{1,3}(?:\.\d{1,3}){3}", host):
        return False
    return any(host == suffix or host.endswith("." + suffix) for suffix in TRUSTED_SUFFIXES)


def normalized_name(ch: Channel) -> str:
    key = ch.tvg_id.lower().strip()
    if key:
        return key
    name = ch.name.lower().replace("臺", "台").replace("視", "视").replace("華", "华").replace("東", "东")
    return re.sub(r"[^0-9a-z\u4e00-\u9fff]+", "", name)


def description(ch: Channel) -> str:
    return f"{ch.name} {ch.group} {ch.tvg_id}".lower()


def explicit_or_premium(ch: Channel) -> bool:
    text = description(ch)
    if any(word.lower() in text for word in EXPLICIT_DENY):
        return True
    # The previously accepted Taiwan seed is preserved, but no new premium
    # linear channel is added from public aggregators.
    return (not ch.seed) and any(word.lower() in text for word in PREMIUM_DENY)


def eligible(ch: Channel) -> bool:
    if not ch.name or not ch.url.lower().startswith(("http://", "https://")):
        return False
    if "youtube.com" in ch.url.lower() or "youtu.be" in ch.url.lower():
        return False
    if explicit_or_premium(ch):
        return False
    if ch.seed:
        return True
    return host_is_trusted(ch.url)


def priority(ch: Channel) -> tuple[int, int, str]:
    text = description(ch)
    if ch.seed:
        return (0, 0, normalized_name(ch))
    if any(word.lower() in text for word in TAIWAN_WORDS) or ch.source == "台灣":
        return (1, 0, normalized_name(ch))
    if ch.source in {"華語", "香港", "新加坡"}:
        return (2, 0, normalized_name(ch))
    if ch.source in {"日本", "韓國"}:
        return (3, 0, normalized_name(ch))
    if any(word in text for word in MATURE_WORDS):
        return (4, 0, normalized_name(ch))
    order = {"新聞": 5, "紀錄": 6, "電影": 7, "影集": 8, "娛樂": 9,
             "動畫": 10, "兒童": 11, "運動": 12, "音樂": 13,
             "生活": 14, "旅遊": 15, "科學": 16, "教育": 17}
    return (order.get(ch.source, 20), 0, normalized_name(ch))


def dedupe_and_limit(channels: Iterable[Channel]) -> list[Channel]:
    by_pair: dict[tuple[str, str], Channel] = {}
    for ch in channels:
        if not eligible(ch):
            continue
        pair = (normalized_name(ch), ch.url)
        by_pair.setdefault(pair, ch)

    # Keep at most two URLs per logical channel; choose seed/region priority first.
    grouped: dict[str, list[Channel]] = {}
    for ch in by_pair.values():
        grouped.setdefault(normalized_name(ch), []).append(ch)

    selected: list[Channel] = []
    for _, items in sorted(grouped.items(), key=lambda item: min(priority(ch) for ch in item[1])):
        items.sort(key=priority)
        seen_hosts: set[str] = set()
        kept = 0
        for ch in items:
            host = (urlparse(ch.url).hostname or "").lower()
            if host in seen_hosts and kept >= 1:
                continue
            selected.append(ch)
            seen_hosts.add(host)
            kept += 1
            if kept >= 2:
                break
        if len(selected) >= MAX_TEST_CANDIDATES:
            break
    return selected[:MAX_TEST_CANDIDATES]


def fetch_manifest(session: requests.Session, url: str, depth: int = 0) -> tuple[str, str]:
    if depth > 3:
        raise ValueError("manifest nesting too deep")
    response = session.get(url, timeout=(CONNECT_TIMEOUT, READ_TIMEOUT), allow_redirects=True)
    response.raise_for_status()
    if len(response.content) > 3_000_000:
        raise ValueError("manifest too large")
    text = response.content.decode("utf-8", errors="replace")
    if "#EXTM3U" not in text[:1200]:
        raise ValueError("not an HLS manifest")
    base = response.url
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    variants: list[tuple[int, str]] = []
    for index, line in enumerate(lines[:-1]):
        if line.startswith("#EXT-X-STREAM-INF"):
            next_line = lines[index + 1]
            if next_line.startswith("#"):
                continue
            bw = re.search(r"BANDWIDTH=(\d+)", line)
            variants.append((int(bw.group(1)) if bw else 999_999_999, urljoin(base, next_line)))
    if variants:
        return fetch_manifest(session, min(variants, key=lambda item: item[0])[1], depth + 1)
    return text, base


def media_segment(manifest: str, base: str) -> str:
    urls = [urljoin(base, line.strip()) for line in manifest.splitlines()
            if line.strip() and not line.strip().startswith("#")]
    if not urls:
        raise ValueError("no media segment")
    return urls[-1]


def probe(ch: Channel) -> tuple[bool, float, str, str]:
    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Accept": "*/*"})
    started = time.monotonic()
    try:
        manifest, base = fetch_manifest(session, ch.url)
        segment = media_segment(manifest, base)
        received = 0
        with session.get(segment, timeout=(CONNECT_TIMEOUT, READ_TIMEOUT), allow_redirects=True,
                         headers={"User-Agent": UA, "Range": "bytes=0-131071"}, stream=True) as response:
            response.raise_for_status()
            for chunk in response.iter_content(16384):
                if chunk:
                    received += len(chunk)
                if received >= 32768:
                    break
        if received < 1024:
            raise ValueError(f"media segment too small: {received}")
        return True, time.monotonic() - started, segment, ""
    except Exception as exc:  # noqa: BLE001
        return False, time.monotonic() - started, "", f"{type(exc).__name__}: {str(exc)[:220]}"


def category(ch: Channel) -> str:
    text = description(ch)
    if ch.seed or any(word.lower() in text for word in TAIWAN_WORDS) or ch.source == "台灣":
        if any(word in text for word in ("新聞", "news", "財經", "财经")):
            return "台灣｜新聞"
        if any(word in text for word in ("卡通", "kids", "兒童", "儿童")):
            return "台灣｜兒童"
        return "台灣｜綜合"
    if ch.source in {"華語", "香港", "新加坡", "日本", "韓國"}:
        return "華語・亞洲"
    if any(word in text for word in MATURE_WORDS):
        return "成熟向｜非露骨"
    if ch.source in {"新聞", "紀錄", "科學", "教育"}:
        return "國際｜新聞・知識"
    if ch.source in {"動畫", "兒童"}:
        return "國際｜卡通・兒童"
    if ch.source in {"電影", "影集", "娛樂"}:
        return "國際｜電影・影集"
    if ch.source == "運動":
        return "國際｜運動"
    return "國際｜音樂・生活"


def choose_100(passing: list[Channel]) -> list[Channel]:
    buckets: dict[str, list[Channel]] = {}
    for ch in passing:
        buckets.setdefault(category(ch), []).append(ch)
    for items in buckets.values():
        items.sort(key=lambda ch: (-ch.passes, statistics.median(ch.times), priority(ch)))

    quotas = {
        "台灣｜新聞": 15,
        "台灣｜綜合": 28,
        "台灣｜兒童": 5,
        "華語・亞洲": 18,
        "成熟向｜非露骨": 12,
        "國際｜新聞・知識": 12,
        "國際｜卡通・兒童": 8,
        "國際｜電影・影集": 12,
        "國際｜運動": 6,
        "國際｜音樂・生活": 8,
    }
    chosen: list[Channel] = []
    chosen_urls: set[str] = set()
    for bucket, quota in quotas.items():
        for ch in buckets.get(bucket, [])[:quota]:
            if ch.url not in chosen_urls:
                chosen.append(ch)
                chosen_urls.add(ch.url)

    remaining = sorted(
        (ch for ch in passing if ch.url not in chosen_urls),
        key=lambda ch: (priority(ch), -ch.passes, statistics.median(ch.times)),
    )
    for ch in remaining:
        if len(chosen) >= TARGET:
            break
        chosen.append(ch)
        chosen_urls.add(ch.url)
    return chosen[:TARGET]


def main() -> None:
    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Accept": "*/*"})
    candidates: list[Channel] = []
    try:
        candidates.extend(fetch_source(session, "既有台灣實測", SEED_URL, seed=True))
    except Exception as exc:  # noqa: BLE001
        print(f"SEED FAIL: {exc}")
    for label, url in PUBLIC_SOURCES:
        try:
            candidates.extend(fetch_source(session, label, url))
        except Exception as exc:  # noqa: BLE001
            print(f"SOURCE FAIL {label}: {exc}")

    candidates = dedupe_and_limit(candidates)
    print(f"TEST CANDIDATES: {len(candidates)}")

    for round_no in range(1, ROUNDS + 1):
        print(f"ROUND {round_no}/{ROUNDS}")
        with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
            future_map = {pool.submit(probe, ch): ch for ch in candidates}
            for future in concurrent.futures.as_completed(future_map):
                ch = future_map[future]
                ok, elapsed, segment, error = future.result()
                ch.times.append(elapsed)
                if ok:
                    ch.passes += 1
                    ch.segments.add(segment)
                    print(f"PASS {elapsed:5.2f}s | {ch.name}")
                else:
                    ch.errors.append(error)
                    print(f"FAIL {elapsed:5.2f}s | {ch.name} | {error}")
        if round_no < ROUNDS:
            time.sleep(ROUND_GAP)

    passing = [ch for ch in candidates if ch.passes >= REQUIRED]
    selected = choose_100(passing)
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")

    lines = [
        "#EXTM3U",
        f"#PLAYLIST:途播｜台灣優先 100 台・50% 實測版｜{now}",
        f"#NOTE:共測試 {len(candidates)} 個候選；4 輪中至少成功 2 輪才保留。",
        "#NOTE:露骨成人內容未收錄；成熟向非露骨節目另行分類。",
        "#NOTE:既有台灣候補中繼僅代表技術測試可播，授權狀態未逐台確認。",
        "",
    ]
    report_channels = []
    for ch in sorted(selected, key=lambda item: (category(item), statistics.median(item.times), item.name.lower())):
        median_time = statistics.median(ch.times)
        speed = "快速" if median_time <= 4 else "一般" if median_time <= 10 else "慢速"
        lines.append(
            f'#EXTINF:-1 tvg-id="{ch.tvg_id.replace(chr(34), "")}" '
            f'group-title="{category(ch)}｜{speed}",{ch.name.replace(chr(34), "")} '
            f'[{ch.passes}/{ROUNDS}・{speed}]'
        )
        lines.append("#EXTVLCOPT:http-user-agent=Mozilla/5.0")
        lines.append(ch.url)
        report_channels.append({
            "name": ch.name,
            "group": category(ch),
            "url": ch.url,
            "source": ch.source,
            "seed": ch.seed,
            "passes": ch.passes,
            "rounds": ROUNDS,
            "availability_percent": round(ch.passes / ROUNDS * 100),
            "median_start_seconds": round(median_time, 2),
            "distinct_segments": len(ch.segments),
            "errors": ch.errors,
        })

    output = Path(__file__).resolve().parent
    (output / "taiwan_100_50pct.m3u").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (output / "taiwan_100_50pct_report.json").write_text(json.dumps({
        "generated_at": now,
        "candidate_count": len(candidates),
        "passing_count": len(passing),
        "selected_count": len(selected),
        "target": TARGET,
        "policy": "4 probes; keep >=2; explicit adult excluded; mature non-explicit allowed",
        "channels": report_channels,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"RESULT selected={len(selected)} passing={len(passing)} candidates={len(candidates)}")


if __name__ == "__main__":
    main()

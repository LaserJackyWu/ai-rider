#!/usr/bin/env python3
"""Build a Taiwan-first 100-entry M3U with a 50% availability threshold.

The previously verified Taiwan playlist is retained as the seed. Expansion
channels are public/free FAST feeds, tested four times; at least two successful
manifest+media-segment probes are required. Sexually explicit feeds and obvious
subscription linear channels are excluded. Mature, non-explicit programming is
allowed in a separate group.
"""

from __future__ import annotations

import concurrent.futures
import hashlib
import json
import re
import statistics
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlparse

import requests

SEED_URL = "https://raw.githubusercontent.com/LaserJackyWu/ai-rider/tubo-playlist/tubo/taiwan_relaxed_verified.m3u"
SOURCES = [
    ("asia", "華語", "https://iptv-org.github.io/iptv/languages/zho.m3u"),
    ("asia", "香港", "https://iptv-org.github.io/iptv/countries/hk.m3u"),
    ("asia", "新加坡", "https://iptv-org.github.io/iptv/countries/sg.m3u"),
    ("asia", "日本", "https://iptv-org.github.io/iptv/countries/jp.m3u"),
    ("asia", "韓國", "https://iptv-org.github.io/iptv/countries/kr.m3u"),
    ("knowledge", "新聞", "https://iptv-org.github.io/iptv/categories/news.m3u"),
    ("knowledge", "紀錄", "https://iptv-org.github.io/iptv/categories/documentary.m3u"),
    ("knowledge", "科學", "https://iptv-org.github.io/iptv/categories/science.m3u"),
    ("knowledge", "教育", "https://iptv-org.github.io/iptv/categories/education.m3u"),
    ("kids", "動畫", "https://iptv-org.github.io/iptv/categories/animation.m3u"),
    ("kids", "兒童", "https://iptv-org.github.io/iptv/categories/kids.m3u"),
    ("entertainment", "電影", "https://iptv-org.github.io/iptv/categories/movies.m3u"),
    ("entertainment", "影集", "https://iptv-org.github.io/iptv/categories/series.m3u"),
    ("entertainment", "娛樂", "https://iptv-org.github.io/iptv/categories/entertainment.m3u"),
    ("sports", "運動", "https://iptv-org.github.io/iptv/categories/sports.m3u"),
    ("lifestyle", "音樂", "https://iptv-org.github.io/iptv/categories/music.m3u"),
    ("lifestyle", "生活", "https://iptv-org.github.io/iptv/categories/lifestyle.m3u"),
    ("lifestyle", "旅遊", "https://iptv-org.github.io/iptv/categories/travel.m3u"),
]

TRUSTED_SUFFIXES = (
    "akamaized.net", "akamaihd.net", "cloudfront.net", "wurl.com",
    "amagi.tv", "playout.now", "playouts.now", "tubi.io", "jmp2.uk",
    "pluto.tv", "rakuten.tv", "brightcove.com", "bcovlive.io",
    "streamlock.net", "streamingfast.net", "vgcdn.net", "dps.live",
    "telvue.com", "cablecast.tv", "azureedge.net", "windows.net",
    "livestream.com", "nhkworld.jp", "france24.com", "getaj.net",
    "thehlive.com", "alarabiya.net", "bozztv.com", "redbull.com",
    "nasa.gov", "euronews.com", "dw.com", "dwcdn.net", "ntdtv.com",
    "suprememastertv.com", "ccdntech.com", "dalitv.com.tw", "beatfm.nl",
    "tucableip.com", "freecast.com", "freecast-lukentvlive.vgcdn.net",
)

EXPLICIT_WORDS = (
    "成人", "情色", "色情", "限制級", "限制级", "18+", "18禁",
    "xxx", "porn", "porno", "playboy", "hustler", "redlight",
    "brazzers", "private spice", "erotic", "sex tv", "sextv",
    "松視", "松视", "潘朵拉", "潘朵啦", "彩虹頻道", "彩虹频道",
    "香蕉台", "av頻道", "av频道", "nude", "naked",
)

PREMIUM_WORDS = (
    "hbo ", "hbo_", "cinemax", "disney channel", "disney junior",
    "espn", "bein sports", "fox sports", "star movies", "衛視電影",
    "卫视电影", "緯來", "纬来", "龍華", "龙华", "博斯", "愛爾達",
    "爱尔达", "靖天", "靖洋", "東森電影", "东森电影", "東森洋片",
    "东森洋片", "animax asia", "cartoon network asia",
)

MATURE_WORDS = (
    "south park", "true crime", "crime", "action", "thriller", "horror",
    "boxing", "wrestling", "kickboxing", "fight", "fear factor", "cops",
    "forensic", "mystery", "investigation", "court", "jail", "prison",
    "war", "csi:", "storage wars",
)

PREFERRED_WORDS = (
    "cna", "nhk world", "al jazeera english", "france 24", "dw english",
    "bbc news", "abc news live", "cbs news", "nbc news now", "euronews",
    "nasa", "red bull tv", "court tv", "forensic files", "csi:",
    "south park", "mr bean", "pluto tv action", "pluto tv crime",
    "true crime", "fear factor", "glory kickboxing", "detective conan",
    "anime vision", "filmrise", "arirang", "weathernews", "pet club tv",
)

SOURCE_TEST_QUOTAS = {
    "asia": 52,
    "mature": 42,
    "knowledge": 48,
    "kids": 34,
    "entertainment": 34,
    "sports": 18,
    "lifestyle": 14,
}
FINAL_QUOTAS = {
    "asia": 16,
    "mature": 14,
    "knowledge": 14,
    "kids": 8,
    "entertainment": 8,
    "sports": 2,
    "lifestyle": 2,
}

ROUNDS = 4
REQUIRED_PASSES = 2
WORKERS = 64
CONNECT_TIMEOUT = 3.5
READ_TIMEOUT = 7.5
ROUND_GAP = 1
TARGET = 100
UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15"


@dataclass
class Channel:
    name: str
    url: str
    bucket: str
    source: str
    group: str = ""
    tvg_id: str = ""
    seed: bool = False
    passes: int = 0
    times: list[float] = field(default_factory=list)
    segments: set[str] = field(default_factory=set)
    errors: list[str] = field(default_factory=list)


def clean_name(value: str) -> str:
    value = re.sub(r"\[[^\]]*(?:\d/\d|啟動|快速|慢速|geo|not 24)[^\]]*\]", "", value, flags=re.I)
    return re.sub(r"\s+", " ", value).strip(" ,|-_")


def ext_attr(line: str, key: str) -> str:
    match = re.search(rf'{re.escape(key)}="([^"]*)"', line, flags=re.I)
    return match.group(1).strip() if match else ""


def parse_m3u(text: str, bucket: str, source: str, seed: bool = False) -> list[Channel]:
    out: list[Channel] = []
    pending = ""
    for raw in text.replace("\r", "").split("\n"):
        line = raw.strip().lstrip("\ufeff")
        if not line:
            continue
        if line.startswith("#EXTINF"):
            pending = line
            continue
        if line.startswith("#"):
            continue
        if pending and line.lower().startswith(("http://", "https://")):
            out.append(Channel(
                name=clean_name(pending.rsplit(",", 1)[-1]),
                url=line,
                bucket=bucket,
                source=source,
                group=ext_attr(pending, "group-title"),
                tvg_id=ext_attr(pending, "tvg-id"),
                seed=seed,
            ))
            pending = ""
    return out


def fetch_list(session: requests.Session, bucket: str, source: str, url: str, seed: bool = False) -> list[Channel]:
    response = session.get(url, timeout=(6, 30), allow_redirects=True)
    response.raise_for_status()
    parsed = parse_m3u(response.content.decode("utf-8", errors="replace"), bucket, source, seed)
    print(f"SOURCE {source}: {len(parsed)}")
    return parsed


def descriptor(ch: Channel) -> str:
    return f"{ch.name} {ch.group} {ch.tvg_id}".lower()


def name_key(ch: Channel) -> str:
    if ch.tvg_id.strip():
        return ch.tvg_id.lower().strip()
    value = ch.name.lower().replace("臺", "台").replace("視", "视").replace("華", "华").replace("東", "东")
    return re.sub(r"[^0-9a-z\u4e00-\u9fff]+", "", value)


def trusted_url(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower()
    if not host or re.fullmatch(r"\d{1,3}(?:\.\d{1,3}){3}", host):
        return False
    return any(host == suffix or host.endswith("." + suffix) for suffix in TRUSTED_SUFFIXES)


def blocked(ch: Channel) -> bool:
    text = descriptor(ch)
    return any(word in text for word in EXPLICIT_WORDS) or any(word in text for word in PREMIUM_WORDS)


def effective_bucket(ch: Channel) -> str:
    text = descriptor(ch)
    if any(word in text for word in MATURE_WORDS):
        return "mature"
    return ch.bucket


def family_key(ch: Channel) -> str:
    text = descriptor(ch)
    families = (
        ("south park", "south-park"),
        ("pluto tv horror", "pluto-horror"),
        ("pluto tv crime", "pluto-crime"),
        ("pluto tv action", "pluto-action"),
        ("true crime", "true-crime"),
        ("al jazeera", "al-jazeera"),
        ("abp ", "abp"),
        ("ntd tv", "ntd"),
    )
    for needle, family in families:
        if needle in text:
            return family
    return name_key(ch)


def family_limit(family: str) -> int:
    return {
        "south-park": 7,
        "pluto-horror": 4,
        "pluto-crime": 5,
        "pluto-action": 5,
        "true-crime": 7,
        "al-jazeera": 4,
        "abp": 3,
        "ntd": 4,
    }.get(family, 2)


def selection_score(ch: Channel) -> tuple[int, int, int, str]:
    text = descriptor(ch)
    preferred = 0 if any(word in text for word in PREFERRED_WORDS) else 1
    geo = 1 if "geo-blocked" in text else 0
    https = 0 if ch.url.lower().startswith("https://") else 1
    digest = hashlib.sha1(name_key(ch).encode("utf-8")).hexdigest()
    return preferred, geo, https, digest


def choose_test_candidates(all_channels: list[Channel], seed_keys: set[str]) -> list[Channel]:
    by_bucket: dict[str, list[Channel]] = {key: [] for key in SOURCE_TEST_QUOTAS}
    seen_pairs: set[tuple[str, str]] = set()
    for ch in all_channels:
        if not ch.name or not ch.url.lower().startswith(("http://", "https://")):
            continue
        if "youtube.com" in ch.url.lower() or "youtu.be" in ch.url.lower():
            continue
        if blocked(ch) or not trusted_url(ch.url):
            continue
        key = name_key(ch)
        if not key or key in seed_keys:
            continue
        bucket = effective_bucket(ch)
        if bucket not in by_bucket:
            continue
        pair = key, ch.url
        if pair in seen_pairs:
            continue
        seen_pairs.add(pair)
        ch.bucket = bucket
        by_bucket[bucket].append(ch)

    chosen: list[Channel] = []
    chosen_keys: set[str] = set()
    family_counts: dict[str, int] = {}
    for bucket, quota in SOURCE_TEST_QUOTAS.items():
        rows = sorted(by_bucket[bucket], key=selection_score)
        for ch in rows:
            key = name_key(ch)
            if key in chosen_keys:
                continue
            family = family_key(ch)
            if family_counts.get(family, 0) >= family_limit(family):
                continue
            chosen.append(ch)
            chosen_keys.add(key)
            family_counts[family] = family_counts.get(family, 0) + 1
            if sum(1 for item in chosen if item.bucket == bucket) >= quota:
                break
    return chosen


def fetch_manifest(session: requests.Session, url: str, depth: int = 0) -> tuple[str, str]:
    if depth > 3:
        raise ValueError("manifest nesting too deep")
    response = session.get(url, timeout=(CONNECT_TIMEOUT, READ_TIMEOUT), allow_redirects=True)
    response.raise_for_status()
    if len(response.content) > 2_500_000:
        raise ValueError("manifest too large")
    text = response.content.decode("utf-8", errors="replace")
    if "#EXTM3U" not in text[:1200]:
        raise ValueError("not an HLS manifest")
    base = response.url
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    variants: list[tuple[int, str]] = []
    for index, line in enumerate(lines[:-1]):
        if line.startswith("#EXT-X-STREAM-INF"):
            nxt = lines[index + 1]
            if nxt.startswith("#"):
                continue
            bandwidth = re.search(r"BANDWIDTH=(\d+)", line)
            variants.append((int(bandwidth.group(1)) if bandwidth else 999_999_999, urljoin(base, nxt)))
    if variants:
        return fetch_manifest(session, min(variants, key=lambda item: item[0])[1], depth + 1)

    media_uris = [line for line in lines if not line.startswith("#")]
    if media_uris and all(uri.lower().split("?", 1)[0].endswith(".m3u8") for uri in media_uris):
        return fetch_manifest(session, urljoin(base, media_uris[0]), depth + 1)
    return text, base


def last_segment(manifest: str, base: str) -> str:
    uris = [urljoin(base, line.strip()) for line in manifest.splitlines()
            if line.strip() and not line.strip().startswith("#")]
    if not uris:
        raise ValueError("no media segment")
    return uris[-1]


def probe(ch: Channel) -> tuple[bool, float, str, str]:
    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Accept": "*/*"})
    started = time.monotonic()
    try:
        manifest, base = fetch_manifest(session, ch.url)
        segment = last_segment(manifest, base)
        received = 0
        with session.get(
            segment,
            timeout=(CONNECT_TIMEOUT, READ_TIMEOUT),
            allow_redirects=True,
            headers={"User-Agent": UA, "Range": "bytes=0-98303"},
            stream=True,
        ) as response:
            response.raise_for_status()
            for chunk in response.iter_content(16384):
                if chunk:
                    received += len(chunk)
                if received >= 32768:
                    break
        if received < 1024:
            raise ValueError(f"segment too small: {received}")
        return True, time.monotonic() - started, segment, ""
    except Exception as exc:  # noqa: BLE001
        return False, time.monotonic() - started, "", f"{type(exc).__name__}: {str(exc)[:180]}"


def output_group(ch: Channel) -> str:
    if ch.seed:
        original = ch.group.split("｜")[0:2]
        return "｜".join(original) if original else "台灣｜綜合"
    return {
        "asia": "華語・亞洲",
        "mature": "成熟向｜非露骨",
        "knowledge": "國際｜新聞・知識",
        "kids": "國際｜卡通・兒童",
        "entertainment": "國際｜電影・影集",
        "sports": "國際｜運動",
        "lifestyle": "國際｜音樂・生活",
    }[ch.bucket]


def choose_final(seed: list[Channel], passing: list[Channel]) -> list[Channel]:
    selected = list(seed)
    selected_urls = {ch.url for ch in selected}
    selected_keys = {name_key(ch) for ch in selected}
    by_bucket: dict[str, list[Channel]] = {key: [] for key in FINAL_QUOTAS}
    for ch in passing:
        by_bucket[ch.bucket].append(ch)
    for rows in by_bucket.values():
        rows.sort(key=lambda ch: (-ch.passes, statistics.median(ch.times), selection_score(ch)))

    for bucket, quota in FINAL_QUOTAS.items():
        added = 0
        for ch in by_bucket[bucket]:
            if ch.url in selected_urls or name_key(ch) in selected_keys:
                continue
            selected.append(ch)
            selected_urls.add(ch.url)
            selected_keys.add(name_key(ch))
            added += 1
            if added >= quota or len(selected) >= TARGET:
                break

    remaining = sorted(
        (ch for ch in passing if ch.url not in selected_urls and name_key(ch) not in selected_keys),
        key=lambda ch: (-ch.passes, statistics.median(ch.times), selection_score(ch)),
    )
    for ch in remaining:
        if len(selected) >= TARGET:
            break
        selected.append(ch)
        selected_urls.add(ch.url)
        selected_keys.add(name_key(ch))
    return selected[:TARGET]


def main() -> None:
    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Accept": "*/*"})

    seed = fetch_list(session, "seed", "既有台灣實測", SEED_URL, seed=True)
    # The seed already passed the previous 3/3 actual decoding test. Preserve it
    # without re-testing slow direct-TS relays during this expansion run.
    for ch in seed:
        ch.passes = ROUNDS
        ch.times = [0.0] * ROUNDS

    expansion: list[Channel] = []
    source_errors: list[dict[str, str]] = []
    for bucket, label, url in SOURCES:
        try:
            expansion.extend(fetch_list(session, bucket, label, url))
        except Exception as exc:  # noqa: BLE001
            source_errors.append({"source": label, "url": url, "error": repr(exc)})
            print(f"SOURCE FAIL {label}: {exc}")

    candidates = choose_test_candidates(expansion, {name_key(ch) for ch in seed})
    print(f"EXPANSION CANDIDATES: {len(candidates)}")

    for round_no in range(1, ROUNDS + 1):
        print(f"ROUND {round_no}/{ROUNDS}")
        with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
            futures = {pool.submit(probe, ch): ch for ch in candidates}
            for future in concurrent.futures.as_completed(futures):
                ch = futures[future]
                ok, elapsed, segment, error = future.result()
                ch.times.append(elapsed)
                if ok:
                    ch.passes += 1
                    ch.segments.add(segment)
                    print(f"PASS {elapsed:5.2f}s | {ch.bucket:13s} | {ch.name}")
                else:
                    ch.errors.append(error)
                    print(f"FAIL {elapsed:5.2f}s | {ch.bucket:13s} | {ch.name} | {error}")
        if round_no < ROUNDS:
            time.sleep(ROUND_GAP)

    passing = [ch for ch in candidates if ch.passes >= REQUIRED_PASSES]
    selected = choose_final(seed, passing)
    now = datetime.now(timezone.utc).isoformat(timespec="seconds")

    lines = [
        "#EXTM3U",
        f"#PLAYLIST:途播｜台灣優先 100 台・50% 實測版｜{now}",
        f"#NOTE:既有台灣種子 {len(seed)} 台沿用前次 3/3 影像讀流結果；新增候選 {len(candidates)} 台測試 4 輪。",
        f"#NOTE:新增來源需至少成功 {REQUIRED_PASSES}/{ROUNDS} 輪；露骨成人與明顯付費線性頻道未收錄。",
        "#NOTE:成熟向非露骨內容另行分類；第三方台灣候補中繼之授權狀態未逐台確認。",
        "",
    ]
    report_rows = []
    category_counts: dict[str, int] = {}
    for ch in selected:
        group = output_group(ch)
        category_counts[group] = category_counts.get(group, 0) + 1
        if ch.seed:
            status = "既有3/3"
            speed = "沿用"
        else:
            median = statistics.median(ch.times)
            speed = "快速" if median <= 4 else "一般" if median <= 9 else "慢速"
            status = f"{ch.passes}/{ROUNDS}"
        safe_name = ch.name.replace('"', "")
        safe_id = ch.tvg_id.replace('"', "")
        lines.append(f'#EXTINF:-1 tvg-id="{safe_id}" group-title="{group}｜{speed}",{safe_name} [{status}・{speed}]')
        lines.append("#EXTVLCOPT:http-user-agent=Mozilla/5.0")
        lines.append(ch.url)
        report_rows.append({
            "name": ch.name,
            "group": group,
            "url": ch.url,
            "source": ch.source,
            "seed": ch.seed,
            "passes": 3 if ch.seed else ch.passes,
            "rounds": 3 if ch.seed else ROUNDS,
            "availability_percent": 100 if ch.seed else round(ch.passes / ROUNDS * 100),
            "median_start_seconds": None if ch.seed else round(statistics.median(ch.times), 2),
            "distinct_segments": len(ch.segments),
            "errors": ch.errors,
        })

    output_dir = Path(__file__).resolve().parent
    (output_dir / "taiwan_100_50pct.m3u").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (output_dir / "taiwan_100_50pct_report.json").write_text(json.dumps({
        "generated_at": now,
        "seed_count": len(seed),
        "expansion_candidate_count": len(candidates),
        "expansion_passing_count": len(passing),
        "selected_count": len(selected),
        "target": TARGET,
        "threshold": "new channels: >=2 successful probes out of 4",
        "explicit_adult_included": False,
        "mature_non_explicit_included": True,
        "category_counts": category_counts,
        "source_errors": source_errors,
        "channels": report_rows,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"RESULT selected={len(selected)} seed={len(seed)} passing={len(passing)} tested={len(candidates)}")
    print(json.dumps(category_counts, ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()

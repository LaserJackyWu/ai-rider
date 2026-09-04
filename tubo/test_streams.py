#!/usr/bin/env python3
"""Build a Taiwan-focused M3U from public/free sources and retain streams
that succeed in at least two of three probes.

The tester validates: manifest retrieval, nested HLS resolution, and a media
segment byte range. It intentionally excludes obvious premium/adult channels
and untrusted raw-IP relays from the generated public playlist.
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

SOURCE_URLS = [
    "http://wangziduoqing.com/yuan/zb.txt",
    "https://iptv-org.github.io/iptv/countries/tw.m3u",
    "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_taiwan.m3u8",
    "https://epg.pw/test_channels_taiwan.m3u",
    "https://raw.githubusercontent.com/hujingguang/ChinaIPTV/main/TaiWan.m3u8",
    "https://raw.githubusercontent.com/cs3306/IPTV-Sources/main/data/output/iptv_collection.m3u",
]

# Domains used by broadcasters or established CDNs in the public/free feeds.
TRUSTED_HOST_SUFFIXES = (
    "streamlock.net",
    "cloudfront.net",
    "streamingfast.net",
    "wanfudaluye.com",
    "dalitv.com.tw",
    "akamaized.net",
    "akamaihd.net",
    "ccdntech.com",
    "ntdtv.com",
    "suprememastertv.com",
    "hinet.net",
    "youtube.com",  # parsed but later rejected because it is not direct HLS
)

TAIWAN_WORDS = (
    "台灣", "台湾", "taiwan", "台視", "台视", "中視", "中视", "華視", "华视",
    "民視", "民视", "公視", "公视", "三立", "東森", "东森", "tvbs", "中天",
    "大愛", "大爱", "大立", "唯心", "原住民", "客家", "國會", "国会", "momo",
    "taiwanplus", "新唐人亞太", "新唐人亚太", "good tv", "美好生活", "cgntv",
)

# Do not republish obvious subscription, premium movie/sports, or adult feeds.
DENY_WORDS = (
    "hbo", "cinemax", "discovery", "國家地理", "国家地理", "緯來", "纬来", "龍華",
    "龙华", "愛爾達", "爱尔达", "博斯", "靖天", "衛視電影", "卫视电影", "東森電影",
    "东森电影", "東森洋片", "东森洋片", "好萊塢", "好莱坞", "成人", "限制級",
    "限制级", "18+", "playboy", "porn", "松視", "松视", "潘朵拉", "彩虹頻道",
    "彩虹频道", "hot", "redlight", "private spice",
)

STATIC_CANDIDATES = [
    ("美好生活電視台", "台灣｜綜合", "https://5ddce30eb4b55.streamlock.net/bltvhd/bltv1/playlist.m3u8", "BeautifulLifeTV.tw"),
    ("CGNTV 中文台", "台灣｜綜合", "https://d3e05csss9c272.cloudfront.net/out/v1/f0bf71c57581470fb9379f603e8f5d83/CGNWebLiveCN.m3u8", "CGNTVChinese.tw"),
    ("大立電視台", "台灣｜綜合", "http://www.dalitv.com.tw:4568/live/dali/index.m3u8", "DaliTV.tw"),
    ("新唐人亞太台", "台灣｜綜合", "https://live.ntdtv.com/aplive200/playlist.m3u8", "NTDTVAsiaPacific.us"),
    ("無上師電視台", "台灣｜綜合", "https://lbs-us1.suprememastertv.com/720p.m3u8", "SupremeMasterTV.tw"),
    ("唯心電視", "台灣｜綜合", "https://mobile.ccdntech.com/transcoder/_definst_/vod164_Live/live/chunklist_w1177047531.m3u8", "WXTV.tw"),
    ("大愛一台", "台灣｜公益", "https://pulltv1.wanfudaluye.com/live/tv1.m3u8", "DaAi1.tw"),
    ("大愛二台", "台灣｜公益", "https://pulltv2.wanfudaluye.com/live/tv2.m3u8", "DaAi2.tw"),
    ("原住民族電視台", "台灣｜公共", "https://streamipcfapp.akamaized.net/live/_definst_/live_720/key_b1500.m3u8", "IndigenousTV.tw"),
    ("TaiwanPlus TV", "台灣｜公共", "https://bcovlive-a.akamaihd.net/rce33d845cb9e42dfa302c7ac345f7858/ap-northeast-1/6282251407001/playlist.m3u8", "TaiwanPlusTV.tw"),
    ("GOOD TV 綜合台", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech1.m3u8", "GoodTV.tw"),
    ("GOOD TV 真理台", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech2.m3u8", "Good2.tw"),
    ("GOOD TV 真情部落格", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech3.m3u8", ""),
    ("GOOD TV 共享觀點", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech5.m3u8", ""),
    ("GOOD TV 詩歌音樂", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech6.m3u8", ""),
    ("GOOD TV 禱告大軍", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech7.m3u8", ""),
    ("GOOD TV 愛＋好醫生", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech9.m3u8", ""),
    ("GOOD TV 維他命施", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech12.m3u8", ""),
    ("GOOD TV 真情部落格完整版", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech14.m3u8", ""),
    ("GOOD TV 真情之夜", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech15.m3u8", ""),
    ("GOOD TV 葉光明", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech16.m3u8", ""),
    ("GOOD TV 大衛鮑森", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech17.m3u8", ""),
    ("GOOD TV 國際講員", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech18.m3u8", ""),
    ("GOOD TV 恩典時分", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech20.m3u8", ""),
    ("GOOD TV 華語講員", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech21.m3u8", ""),
    ("GOOD TV 劉三講古", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech24.m3u8", ""),
    ("GOOD TV 空中聖經學院", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech27.m3u8", ""),
    ("GOOD TV 現代詩歌", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech28.m3u8", ""),
    ("GOOD TV 經典音樂河", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech29.m3u8", ""),
    ("GOOD TV 天堂敬拜", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech30.m3u8", ""),
    ("GOOD TV 福音佈道音樂會", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech31.m3u8", ""),
    ("GOOD TV 研經培靈", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech33.m3u8", ""),
    ("GOOD TV 青年特會", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech38.m3u8", ""),
    ("GOOD TV 家庭八點檔", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech40.m3u8", ""),
    ("GOOD TV 卡通", "台灣｜兒童", "https://live.streamingfast.net/osmflivech45.m3u8", ""),
    ("GOOD TV 牧者頻道", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech47.m3u8", ""),
    ("GOOD TV 禱告頻道", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech49.m3u8", ""),
    ("GOOD TV 國際講員中文", "台灣｜GOOD TV", "https://live.streamingfast.net/osmflivech50.m3u8", ""),
]

UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15"
TIMEOUT = (7, 15)
ROUNDS = 3
ROUND_GAP_SECONDS = 8
MAX_WORKERS = 16


@dataclass
class Channel:
    name: str
    group: str
    url: str
    tvg_id: str = ""
    source: str = ""
    probe_successes: int = 0
    latencies: list[float] = field(default_factory=list)
    observed_segments: set[str] = field(default_factory=set)
    errors: list[str] = field(default_factory=list)


def clean_text(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def attr(extinf: str, key: str) -> str:
    m = re.search(rf'{re.escape(key)}="([^"]*)"', extinf, re.I)
    return m.group(1).strip() if m else ""


def parse_playlist(text: str, source: str) -> list[Channel]:
    lines = [line.strip().lstrip("\ufeff") for line in text.splitlines()]
    channels: list[Channel] = []
    pending: str | None = None

    for line in lines:
        if not line:
            continue
        if line.startswith("#EXTINF"):
            pending = line
            continue
        if line.startswith("#"):
            continue
        if pending and re.match(r"https?://", line, re.I):
            name = clean_text(pending.rsplit(",", 1)[-1])
            group = clean_text(attr(pending, "group-title"))
            tvg_id = clean_text(attr(pending, "tvg-id"))
            channels.append(Channel(name=name, group=group, url=line, tvg_id=tvg_id, source=source))
            pending = None
            continue

        # Common TXT formats: name,url / name$URL / name#genre#URL.
        match = re.match(r"^(.*?)[,$|](https?://\S+)$", line)
        if not match:
            match = re.match(r"^(.*?)(?:#genre#)?(https?://\S+)$", line)
        if match:
            name = clean_text(match.group(1).strip(" ,$|#")) or "未命名頻道"
            channels.append(Channel(name=name, group="", url=match.group(2), source=source))

    return channels


def download_sources() -> list[Channel]:
    out: list[Channel] = []
    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Accept": "*/*"})
    for source in SOURCE_URLS:
        try:
            response = session.get(source, timeout=(8, 35), allow_redirects=True)
            response.raise_for_status()
            response.encoding = response.apparent_encoding or "utf-8"
            out.extend(parse_playlist(response.text, source))
            print(f"SOURCE OK {source}: {len(response.content)} bytes")
        except Exception as exc:  # noqa: BLE001
            print(f"SOURCE FAIL {source}: {type(exc).__name__}: {exc}")
    return out


def is_direct_hls(url: str) -> bool:
    lowered = url.lower()
    return lowered.startswith(("http://", "https://")) and "youtube.com" not in lowered and "youtu.be" not in lowered


def is_public_candidate(ch: Channel) -> bool:
    descriptor = f"{ch.name} {ch.group}".lower()
    if not any(word.lower() in descriptor for word in TAIWAN_WORDS):
        return False
    if any(word.lower() in descriptor for word in DENY_WORDS):
        return False
    if not is_direct_hls(ch.url):
        return False
    host = (urlparse(ch.url).hostname or "").lower()
    if not host or re.fullmatch(r"\d{1,3}(?:\.\d{1,3}){3}", host):
        return False
    return any(host == suffix or host.endswith("." + suffix) for suffix in TRUSTED_HOST_SUFFIXES)


def dedupe(channels: Iterable[Channel]) -> list[Channel]:
    unique: dict[str, Channel] = {}
    for ch in channels:
        key = ch.url.strip()
        if not key or key in unique:
            continue
        unique[key] = ch
    return list(unique.values())


def fetch_text(session: requests.Session, url: str) -> tuple[str, str, float]:
    start = time.monotonic()
    response = session.get(url, timeout=TIMEOUT, allow_redirects=True)
    elapsed = time.monotonic() - start
    response.raise_for_status()
    if len(response.content) > 3_000_000:
        raise ValueError("manifest too large")
    text = response.content.decode("utf-8", errors="replace")
    if "#EXTM3U" not in text[:1000]:
        raise ValueError(f"not HLS ({response.headers.get('content-type', '')})")
    return text, response.url, elapsed


def resolve_media_playlist(session: requests.Session, url: str, depth: int = 0) -> tuple[str, str, float]:
    if depth > 3:
        raise ValueError("nested manifest depth exceeded")
    text, final_url, elapsed = fetch_text(session, url)
    lines = [line.strip() for line in text.splitlines() if line.strip()]

    variants: list[tuple[int, str]] = []
    for index, line in enumerate(lines):
        if line.startswith("#EXT-X-STREAM-INF") and index + 1 < len(lines):
            next_line = lines[index + 1]
            if not next_line.startswith("#"):
                bandwidth_match = re.search(r"BANDWIDTH=(\d+)", line)
                bandwidth = int(bandwidth_match.group(1)) if bandwidth_match else 999_999_999
                variants.append((bandwidth, urljoin(final_url, next_line)))

    if variants:
        # Lowest bitrate is more representative of whether the channel can start reliably.
        _, selected = min(variants, key=lambda item: item[0])
        nested_text, nested_url, nested_elapsed = resolve_media_playlist(session, selected, depth + 1)
        return nested_text, nested_url, elapsed + nested_elapsed

    return text, final_url, elapsed


def last_media_uri(manifest: str, base_url: str) -> str:
    candidates: list[str] = []
    for line in manifest.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        candidates.append(urljoin(base_url, line))
    if not candidates:
        raise ValueError("no media segments")
    return candidates[-1]


def probe(ch: Channel) -> dict[str, object]:
    session = requests.Session()
    session.headers.update({"User-Agent": UA, "Accept": "*/*"})
    started = time.monotonic()
    try:
        manifest, media_url, manifest_latency = resolve_media_playlist(session, ch.url)
        segment_url = last_media_uri(manifest, media_url)
        segment_start = time.monotonic()
        with session.get(
            segment_url,
            timeout=TIMEOUT,
            allow_redirects=True,
            headers={"Range": "bytes=0-131071", "User-Agent": UA},
            stream=True,
        ) as response:
            response.raise_for_status()
            received = 0
            for chunk in response.iter_content(chunk_size=16_384):
                if chunk:
                    received += len(chunk)
                if received >= 32_768:
                    break
        segment_latency = time.monotonic() - segment_start
        if received < 1_024:
            raise ValueError(f"segment too small ({received} bytes)")
        return {
            "ok": True,
            "latency": time.monotonic() - started,
            "manifest_latency": manifest_latency,
            "segment_latency": segment_latency,
            "segment": segment_url,
            "bytes": received,
        }
    except Exception as exc:  # noqa: BLE001
        return {
            "ok": False,
            "latency": time.monotonic() - started,
            "error": f"{type(exc).__name__}: {str(exc)[:240]}",
        }


def main() -> None:
    candidates = [Channel(name=n, group=g, url=u, tvg_id=t, source="static") for n, g, u, t in STATIC_CANDIDATES]
    candidates.extend(ch for ch in download_sources() if is_public_candidate(ch))
    candidates = dedupe(candidates)
    print(f"CANDIDATES {len(candidates)}")

    for round_index in range(ROUNDS):
        print(f"ROUND {round_index + 1}/{ROUNDS}")
        with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            future_map = {executor.submit(probe, ch): ch for ch in candidates}
            for future in concurrent.futures.as_completed(future_map):
                ch = future_map[future]
                result = future.result()
                if result["ok"]:
                    ch.probe_successes += 1
                    ch.latencies.append(float(result["latency"]))
                    ch.observed_segments.add(str(result["segment"]))
                    print(f"OK {ch.name}: {result['latency']:.2f}s")
                else:
                    ch.errors.append(str(result.get("error", "unknown")))
                    print(f"FAIL {ch.name}: {result.get('error')}")
        if round_index + 1 < ROUNDS:
            time.sleep(ROUND_GAP_SECONDS)

    kept = [ch for ch in candidates if ch.probe_successes >= 2]
    kept.sort(key=lambda ch: (ch.group, statistics.median(ch.latencies), ch.name.lower()))

    generated = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    lines = [
        '#EXTM3U',
        f'#PLAYLIST:途播｜台灣公開頻道 60% 實測版（{generated}）',
        '#NOTE:保留三次探測中至少成功兩次的公開／免費來源；慢速頻道會標示。',
        '',
    ]
    report_channels = []
    for ch in kept:
        median_latency = statistics.median(ch.latencies)
        speed = "快速" if median_latency <= 3.0 else "一般" if median_latency <= 8.0 else "慢速"
        dynamic = len(ch.observed_segments) >= 2
        group = f"{ch.group or '台灣｜其他'}｜{speed}"
        tvg = f' tvg-id="{ch.tvg_id}"' if ch.tvg_id else ""
        lines.append(f'#EXTINF:-1{tvg} group-title="{group}",{ch.name} [{ch.probe_successes}/{ROUNDS}・{speed}]')
        lines.append(ch.url)
        report_channels.append({
            "name": ch.name,
            "group": ch.group,
            "url": ch.url,
            "source": ch.source,
            "successes": ch.probe_successes,
            "rounds": ROUNDS,
            "availability_percent": round(ch.probe_successes / ROUNDS * 100),
            "median_start_seconds": round(median_latency, 2),
            "distinct_segments_seen": len(ch.observed_segments),
            "live_segment_changed": dynamic,
            "errors": ch.errors,
        })

    report = {
        "generated_at": generated,
        "policy": "public/free Taiwan-focused streams; keep >=2 of 3 successful probes",
        "sources": SOURCE_URLS,
        "candidate_count": len(candidates),
        "kept_count": len(kept),
        "channels": report_channels,
    }

    output_dir = Path(__file__).resolve().parent
    (output_dir / "taiwan_public_60pct.m3u").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (output_dir / "taiwan_test_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"KEPT {len(kept)}/{len(candidates)}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Stage 21X — Riot Mac Payload Harvester for DarwinBridge.

Discovers official Riot macOS League manifests, inventories them without
installing the full game, selectively downloads Mach-O candidates, and emits
a DarwinBridge compatibility manifest from Apple's Mach-O tooling.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import requests
from riotmanifest import PatcherManifest

KEYSTONE_URL = (
    "https://clientconfig.rpg.riotgames.com/api/v1/config/public"
    "?namespace=keystone.products.league_of_legends.patchlines"
)
SIEVE_URL = "https://sieve.services.riotcdn.net/api/v1/products/lol/version-sets/{region}?q[platform]=macos"
BUNDLE_URLS = (
    "https://lol.dyn.riotcdn.net/channels/public/bundles/",
    "https://lol.secure.dyn.riotcdn.net/channels/public/bundles/",
)
REGION_MAP = {
    "BR":"BR1","EUNE":"EUN1","EUW":"EUW1","JP":"JP1","KR":"KR",
    "LA1":"LA1","LA2":"LA2","ME1":"ME1","NA":"NA1","OC1":"OC1",
    "PH2":"PH2","RU":"RU","SG2":"SG2","TH2":"TH2","TR":"TR1",
    "TW2":"TW2","VN2":"VN2","PBE":"PBE1",
}
MACHO_MAGICS = {
    b"\xfe\xed\xfa\xce", b"\xce\xfa\xed\xfe", b"\xfe\xed\xfa\xcf", b"\xcf\xfa\xed\xfe",
    b"\xca\xfe\xba\xbe", b"\xbe\xba\xfe\xca", b"\xca\xfe\xba\xbf", b"\xbf\xba\xfe\xca",
}


@dataclass
class Release:
    kind: str
    url: str
    version: str | None = None
    revision: int | None = None
    source: str | None = None


def get_json(url: str) -> Any:
    r = requests.get(url, timeout=30)
    r.raise_for_status()
    return r.json()


def _label(release: dict, name: str, default: str = "") -> str:
    try:
        return release["release"]["labels"][name]["values"][0]
    except (KeyError, IndexError, TypeError):
        return default


def discover(region: str) -> dict[str, Any]:
    region = region.upper()
    if region not in REGION_MAP:
        raise SystemExit(f"Unsupported region {region}; choose one of: {', '.join(REGION_MAP)}")

    cfg = get_json(KEYSTONE_URL)
    lcu_candidates: list[Release] = []
    for key, patchline in cfg.items():
        # Prefer live, but tolerate schema/key naming changes.
        platforms = patchline.get("platforms", {}) if isinstance(patchline, dict) else {}
        mac = platforms.get("mac", {})
        for item in mac.get("configurations", []) or []:
            if str(item.get("id", "")).upper() == region and item.get("patch_url"):
                lcu_candidates.append(Release(
                    kind="league-client",
                    url=item["patch_url"],
                    version=item.get("version"),
                    source=key,
                ))
    if not lcu_candidates:
        raise RuntimeError(f"No official Keystone mac patch_url found for {region}")
    live_lcu = next((x for x in lcu_candidates if x.source and x.source.endswith(".live")), lcu_candidates[0])

    sieve = get_json(SIEVE_URL.format(region=REGION_MAP[region]))
    game_candidates: list[Release] = []
    for item in sieve.get("releases", []):
        artifact = _label(item, "riot:artifact_type_id")
        if artifact != "lol-game-client":
            continue
        platform = _label(item, "riot:platform") or _label(item, "platform")
        if platform and platform.lower() not in {"macos", "mac"}:
            continue
        try:
            revision = int(_label(item, "riot:revision", "0"))
        except ValueError:
            revision = 0
        game_candidates.append(Release(
            kind="lol-game-client",
            url=item["download"]["url"],
            version=_label(item, "riot:artifact_version_id").split("+", 1)[0] or None,
            revision=revision,
            source="sieve",
        ))
    if not game_candidates:
        raise RuntimeError(f"No macOS lol-game-client release returned by Sieve for {REGION_MAP[region]}")
    game = max(game_candidates, key=lambda x: x.revision or 0)

    return {
        "region": region,
        "version_set": REGION_MAP[region],
        "platform": "macos",
        "league_client": asdict(live_lcu),
        "game_client": asdict(game),
    }


def candidate_reason(name: str) -> str | None:
    p = name.replace("\\", "/")
    low = p.lower()
    base = p.rsplit("/", 1)[-1]
    if low.endswith(".dylib"):
        return "dylib"
    if ".framework/" in low:
        # Framework binary normally has no suffix and sits at framework root or Versions/*.
        fw = re.search(r"/([^/]+)\.framework/(?:versions/[^/]+/)?([^/]+)$", p, re.I)
        if fw and fw.group(1).lower() == fw.group(2).lower():
            return "framework-binary"
    if "/contents/macos/" in low:
        return "app-executable"
    if any(x in low for x in (
        "leagueclient", "leagueclientux", "leagueoflegends",
        "crashpad_handler", "helper.app/contents/macos/",
    )):
        if not re.search(r"\.(json|plist|pak|bin|dat|txt|log|png|jpg|webp|icns|strings)$", low):
            return "named-executable"
    return None


def build_manifest(release: Release, root: Path) -> PatcherManifest:
    out = root / release.kind
    out.mkdir(parents=True, exist_ok=True)
    return PatcherManifest(
        release.url,
        path=str(out),
        bundle_urls=BUNDLE_URLS,
        concurrency_limit=16,
    )


def inventory_one(release: Release, root: Path) -> tuple[PatcherManifest, list[dict[str, Any]], list[Any]]:
    manifest = build_manifest(release, root)
    rows, selected = [], []
    for f in manifest.files.values():
        reason = candidate_reason(f.name)
        row = {
            "manifest": release.kind,
            "path": f.name,
            "size": f.size,
            "flags": f.flags,
            "chunks": len(f.chunks),
            "candidate_reason": reason,
        }
        rows.append(row)
        if reason:
            selected.append(f)
    return manifest, rows, selected


async def download_selected(manifest: PatcherManifest, files: list[Any]) -> list[bool]:
    if not files:
        return []
    return await manifest.download_files_concurrently(files, concurrency_limit=16)


def is_macho(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size < 4:
        return False
    with path.open("rb") as fh:
        return fh.read(4) in MACHO_MAGICS


def run_tool(args: list[str]) -> str:
    try:
        p = subprocess.run(args, check=False, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        return p.stdout
    except FileNotFoundError:
        return ""


def parse_otool_l(text: str) -> tuple[list[dict[str, str]], list[str]]:
    dylibs: list[dict[str, str]] = []
    rpaths: list[str] = []
    command = None
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith("cmd "):
            command = line.split(None, 1)[1]
        elif command in {"LC_LOAD_DYLIB","LC_LOAD_WEAK_DYLIB","LC_REEXPORT_DYLIB","LC_LOAD_UPWARD_DYLIB"} and line.startswith("name "):
            dylibs.append({"command": command, "name": line[5:].split(" (offset ", 1)[0]})
        elif command == "LC_RPATH" and line.startswith("path "):
            rpaths.append(line[5:].split(" (offset ", 1)[0])
    return dylibs, sorted(set(rpaths))


def nm_symbols(path: Path, mode: str) -> list[str]:
    args = ["nm"]
    if mode == "imports":
        args += ["-u", str(path)]
    else:
        args += ["-gU", str(path)]
    out = run_tool(args)
    symbols: list[str] = []
    for line in out.splitlines():
        s = line.strip()
        if not s or s.endswith(":") or "no symbols" in s.lower():
            continue
        token = s.split()[-1]
        if token.startswith(("_", "$")):
            symbols.append(token)
    return sorted(set(symbols))


def analyze_binary(path: Path, root: Path) -> dict[str, Any]:
    file_desc = run_tool(["file", "-b", str(path)]).strip()
    dylibs, rpaths = parse_otool_l(run_tool(["otool", "-l", str(path)]))
    imports = nm_symbols(path, "imports")
    exports = nm_symbols(path, "exports")
    objc_classes = sorted({s.split("_OBJC_CLASS_$_",1)[1] for s in imports + exports if "_OBJC_CLASS_$_" in s})
    objc_metaclasses = sorted({s.split("_OBJC_METACLASS_$_",1)[1] for s in imports + exports if "_OBJC_METACLASS_$_" in s})
    return {
        "path": str(path.relative_to(root)),
        "file": file_desc,
        "dylibs": dylibs,
        "rpaths": rpaths,
        "imports": imports,
        "exports": exports,
        "objc_classes": objc_classes,
        "objc_metaclasses": objc_metaclasses,
    }


def aggregate(releases: dict[str, Any], inventory: list[dict[str, Any]], binaries: list[dict[str, Any]]) -> dict[str, Any]:
    unique_dylibs = sorted({d["name"] for b in binaries for d in b["dylibs"]})
    unique_imports = sorted({s for b in binaries for s in b["imports"]})
    objc_classes = sorted({s for b in binaries for s in b["objc_classes"]})
    objc_metaclasses = sorted({s for b in binaries for s in b["objc_metaclasses"]})
    framework_imports = sorted({d for d in unique_dylibs if ".framework/" in d or d.startswith("/System/Library/Frameworks/")})
    return {
        "schema": "darwinbridge.lol-compatibility.v1",
        "source": releases,
        "summary": {
            "release_files": len(inventory),
            "macho_binaries": len(binaries),
            "unique_dylibs": len(unique_dylibs),
            "unique_imports": len(unique_imports),
            "objc_classes": len(objc_classes),
            "objc_metaclasses": len(objc_metaclasses),
        },
        "requirements": {
            "dylibs": unique_dylibs,
            "frameworks": framework_imports,
            "imports": unique_imports,
            "objc_classes": objc_classes,
            "objc_metaclasses": objc_metaclasses,
        },
        "binaries": binaries,
    }


async def harvest(args: argparse.Namespace) -> None:
    root = Path(args.output).resolve()
    root.mkdir(parents=True, exist_ok=True)
    releases = discover(args.region)
    (root / "release-sources.json").write_text(json.dumps(releases, indent=2), encoding="utf-8")

    all_inventory: list[dict[str, Any]] = []
    plans: list[tuple[PatcherManifest, list[Any]]] = []
    for key in ("league_client", "game_client"):
        rel = Release(**releases[key])
        manifest, rows, selected = inventory_one(rel, root)
        all_inventory.extend(rows)
        plans.append((manifest, selected))

    (root / "release-file-inventory.json").write_text(json.dumps(all_inventory, indent=2), encoding="utf-8")
    candidates = [x for x in all_inventory if x["candidate_reason"]]
    (root / "macho-candidates.json").write_text(json.dumps(candidates, indent=2), encoding="utf-8")

    if args.inventory_only:
        print(json.dumps({"release_files": len(all_inventory), "candidates": len(candidates), "output": str(root)}, indent=2))
        return

    for manifest, selected in plans:
        print(f"[21X] downloading {len(selected)} Mach-O candidates from {manifest.path}")
        results = await download_selected(manifest, selected)
        failed = sum(1 for x in results if not x)
        if failed:
            print(f"[21X] warning: {failed} selective downloads failed", file=sys.stderr)

    binaries: list[dict[str, Any]] = []
    for path in root.rglob("*"):
        if is_macho(path):
            binaries.append(analyze_binary(path, root))
    binaries.sort(key=lambda x: x["path"])
    compat = aggregate(releases, all_inventory, binaries)
    (root / "lol-compatibility-manifest.json").write_text(json.dumps(compat, indent=2), encoding="utf-8")
    print(json.dumps(compat["summary"], indent=2))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--region", default="EUW", choices=sorted(REGION_MAP))
    ap.add_argument("--output", default="artifacts/riot-mac-harvest")
    ap.add_argument("--inventory-only", action="store_true", help="Parse manifests and list candidates without downloading binaries")
    args = ap.parse_args()
    asyncio.run(harvest(args))


if __name__ == "__main__":
    main()

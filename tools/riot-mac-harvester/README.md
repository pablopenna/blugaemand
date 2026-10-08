# Stage 21X — Riot Mac Payload Harvester

This tool replaces DarwinBridge's crash→fix dependency discovery loop with a reproducible, batch analysis of Riot's current official macOS League payload.

## Data flow

1. Query Riot ClientConfig / Keystone and select the live macOS LeagueClient `patch_url`.
2. Query Riot Sieve for the current `lol-game-client` release with `platform=macos`.
3. Parse both RMAN release manifests. The full file inventory is emitted without installing the game.
4. Select executable candidates: app `Contents/MacOS` entries, dylibs, framework binaries, League executables, CEF helpers and crashpad.
5. Download only those candidates from Riot's chunk/bundle CDN.
6. Verify Mach-O magic, then analyze real Mach-O files with macOS `file`, `otool`, and `nm`.
7. Emit `lol-compatibility-manifest.json` with per-binary and aggregate dylib/rpath/import/export/ObjC requirements.

## Local use

Requires macOS for full Mach-O analysis:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r tools/riot-mac-harvester/requirements.txt
python tools/riot-mac-harvester/harvester.py --region EUW
```

Inventory-only mode can run anywhere:

```bash
python tools/riot-mac-harvester/harvester.py --region EUW --inventory-only
```

Outputs are written under `artifacts/riot-mac-harvest/`:

- `release-sources.json` — exact Keystone + Sieve manifest URLs/version metadata
- `release-file-inventory.json` — every file from both RMAN manifests
- `macho-candidates.json` — selective-download plan
- `lol-compatibility-manifest.json` — DarwinBridge batch compatibility input

The GitHub Actions workflow `Riot Mac Payload Harvester` runs this on a macOS runner and uploads the JSON manifests as an artifact.

## Why two manifests?

LeagueClient and the game are published as separate artifacts. Keystone exposes the current LeagueClient mac patch URL; Sieve exposes the current macOS `lol-game-client` release. Harvesting both prevents client-side CEF/framework dependencies or game-side dylibs from being omitted.

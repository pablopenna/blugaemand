#!/usr/bin/env python3
"""Download a pinned official ARM64 game binary for an audit, never a release.
The user's on-device selected binary is not available here: do not equate these.
"""
import asyncio, json, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'tools/riot-mac-harvester'))
from harvester import Release, inventory_one, download_selected
from macho_audit import MachO
ROOT=Path('artifacts/loader-audit/riot-pinned');ROOT.mkdir(parents=True,exist_ok=True)
release=Release(kind='lol-game-client',url='https://lol.secure.dyn.riotcdn.net/channels/public/releases/8841D91E6665286D.manifest',version='16.19.8230722',revision=8230722,source='handoff-harvester-37496253357')
manifest,_,candidates=inventory_one(release,ROOT)
selected=[f for f in candidates if f.name.endswith('/Contents/MacOS/LeagueofLegends')]
assert len(selected)==1,'expected exactly one real game executable'
assert all(asyncio.run(download_selected(manifest,selected))),'download failed'
path=ROOT/release.kind/selected[0].name
m=MachO(path.read_bytes());r=m.report()
r['source_manifest']=release.url
r['scope']='Pinned Riot game executable; NOT the installed/selected user IPA'
r['missing_from_minimal_package']=[d for d in m.dependencies if d['path'].startswith('@rpath/')]
r['desktop_paths_requiring_port']=[d for d in m.dependencies if any(n in d['path'] for n in ['LDAP.framework','OpenGL.framework','ApplicationServices.framework'])]
r['full_game_candidate_approved']=False
(ROOT/'real-game-audit.json').write_text(json.dumps(r,indent=2))
print(json.dumps({k:r[k] for k in ['sha256','bytes','missing_from_minimal_package','desktop_paths_requiring_port','full_game_candidate_approved']},indent=2))
# Keep metadata and hashes. Do not upload/distribute the copyrighted game binary.
path.unlink()

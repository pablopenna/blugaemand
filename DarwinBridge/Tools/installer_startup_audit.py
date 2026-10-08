#!/usr/bin/env python3
"""Audit the official installer separately from the League client/game payload.

No Riot executable or assets are uploaded. The rolling official URL is guarded
by a content hash: an upstream change must be reviewed instead of misidentified.
"""
import argparse, hashlib, json, os, plistlib, shutil, signal, subprocess, sys
import tempfile, urllib.request, zipfile
from pathlib import Path
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
from macho_audit import MachO, check

URL = 'https://lol.secure.dyn.riotcdn.net/channels/public/x/installer/current/live.euw.zip'
ZIP_SHA = '4f02590ef8a1bd41ad77c6c5a07d33029220a2e421d7bb4dee31653ef17a9181'
ARM_SHA = '199f80081c87b9dd0f3a996ea85dbbdedd0680d14dc2f003f90d1ef9f29da782'
ap = argparse.ArgumentParser()
ap.add_argument('--installer-zip', type=Path)
ap.add_argument('--packager', type=Path)
ap.add_argument('--runtime', type=Path)
ap.add_argument('--out', type=Path, default=Path('artifacts/loader-audit/installer'))
a = ap.parse_args()
a.out.mkdir(parents=True, exist_ok=True)

with tempfile.TemporaryDirectory(prefix='db-installer-audit-') as tmp:
    tmp = Path(tmp).resolve()
    archive = a.installer_zip or tmp/'installer.zip'
    if not a.installer_zip:
        with urllib.request.urlopen(URL, timeout=60) as r, archive.open('wb') as f:
            shutil.copyfileobj(r, f)
    check(hashlib.sha256(archive.read_bytes()).hexdigest() == ZIP_SHA,
          'official installer changed; review and update its identity pin')
    with zipfile.ZipFile(archive) as z:
        executable = next(n for n in z.namelist() if n.endswith('/Contents/MacOS/RiotClientServices'))
        app_prefix = executable.split('/Contents/')[0]
        binary = z.read(executable)
        info = plistlib.loads(z.read(app_prefix+'/Contents/Info.plist'))
        yaml = z.read(app_prefix+'/Contents/Resources/system.yaml')
        resource_names = [n.split('/Contents/Resources/', 1)[1] for n in z.namelist()
                          if '/Contents/Resources/' in n and not n.endswith('/')]
    m = MachO(binary)
    check(hashlib.sha256(m.data).hexdigest() == ARM_SHA, 'installer ARM64 identity mismatch')
    report = {
        'source_url': URL, 'zip_sha256': ZIP_SHA, 'binary': m.report(),
        'bundle_executable': info['CFBundleExecutable'],
        'bundle_identifier': info['CFBundleIdentifier'],
        'resource_count': len(resource_names),
        'system_yaml_sha256': hashlib.sha256(yaml).hexdigest(),
        'scope': 'Official RiotClientServices installer; installed iPad binary UUID/hash still unavailable',
        'device_uuid_match_proven': False,
    }
    md = Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN)
    # These are image-relative offsets in the hash-pinned ARM64 image, not iPad PCs.
    report['exit_252_sites'] = []
    for off, meaning in [(0x9d519c, 'embedded system.yaml load failure'),
                         (0x9d51c4, 'publisher missing from system-settings'),
                         (0x9d5670, 'later initialization failure'),
                         (0x9d64d8, 'later initialization failure')]:
        raw = m.data[off:off+4]
        instruction = next(md.disasm(raw, 0x100000000+off))
        check(instruction.mnemonic == 'mov' and instruction.op_str == 'w23, #0xfc',
              'exit-site disassembly changed')
        report['exit_252_sites'].append(dict(image_offset=hex(off), bytes=raw.hex(), meaning=meaning))
    entry = int(m.report()['lc_main'][0], 16)
    report['entry_disassembly'] = [dict(offset=hex(i.address-0x100000000), instruction=i.mnemonic+' '+i.op_str)
                                  for i in md.disasm(m.data[entry:entry+0xc4], 0x100000000+entry)]

    # Verify rejection even after changing the filename, as the old packager did.
    if a.packager:
        check(a.runtime is not None, '--runtime is required with --packager')
        report['packager_rejections'] = []
        for name in ('RiotClientServices', 'LeagueOfLegends'):
            source = tmp/name
            source.write_bytes(binary)
            ipa = tmp/(name+'.ipa')
            r = subprocess.run([str(a.packager.resolve()), str(source), str(a.runtime.resolve()), str(ipa)],
                               capture_output=True, text=True, timeout=30)
            check(r.returncode == 2 and 'RiotClientServices installer/bootstrap detected' in r.stderr,
                  'installer was not rejected by content')
            check(not ipa.exists(), 'rejected installer still produced an IPA')
            report['packager_rejections'].append(dict(input_name=name, returncode=r.returncode, error=r.stderr.strip()))

    # Run only a deliberately resource-less copy. Sandbox network access, writes
    # outside the temporary directory and child creation; never run the installer UI.
    if sys.platform == 'darwin':
        sandbox = shutil.which('sandbox-exec')
        check(sandbox, 'sandbox-exec is required for the native installer regression')
        app = tmp/'NativeProbe.app'
        (app/'Contents/MacOS').mkdir(parents=True)
        target = app/'Contents/MacOS/LeagueOfLegends'
        target.write_bytes(m.data)
        target.chmod(0o755)
        (app/'Contents/Info.plist').write_bytes(plistlib.dumps(dict(
            CFBundleExecutable='LeagueOfLegends', CFBundleIdentifier='com.darwinbridge.installerregression',
            CFBundlePackageType='APPL', CFBundleName='Resource-less regression')))
        # Thinning the vendor binary and replacing its bundle/Info.plist is a
        # mutation. Validate and re-sign the *final* native harness before using
        # its exit status as evidence about Riot startup. A killed unsigned copy
        # says nothing about the missing-resource branch.
        before = subprocess.run(['codesign', '--verify', '--strict', '--verbose=4', str(app)],
                                capture_output=True, text=True)
        report['native_signature_before'] = dict(returncode=before.returncode, stderr=before.stderr)
        signed = subprocess.run(['codesign', '--force', '--sign', '-', '--timestamp=none', str(app)],
                                capture_output=True, text=True)
        report['native_resign'] = dict(returncode=signed.returncode, stderr=signed.stderr)
        verified = subprocess.run(['codesign', '--verify', '--strict', '--verbose=4', str(app)],
                                  capture_output=True, text=True)
        report['native_signature_after'] = dict(returncode=verified.returncode, stderr=verified.stderr)
        (a.out/'installer-audit.json').write_text(json.dumps(report, indent=2))
        check(signed.returncode == 0 and verified.returncode == 0,
              'native harness must have a valid final ad-hoc signature before execution')
        final = MachO(target.read_bytes())
        check(final.report()['lc_main'] == m.report()['lc_main'], 'native re-sign changed LC_MAIN')
        check(final.report()['section_hashes'] == m.report()['section_hashes'], 'native re-sign changed section content')
        profile = '(version 1)(allow default)(deny network*)(deny process-fork)(deny file-write*)' \
                  + '(allow file-write* (subpath '+json.dumps(str(tmp))+') (literal "/dev/null"))'
        env = dict(os.environ, HOME=str(tmp), CFFIXED_USER_HOME=str(tmp), TMPDIR=str(tmp))
        p = subprocess.Popen([sandbox, '-p', profile, str(target)], cwd=tmp, env=env,
                             stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                             text=True, start_new_session=True)
        try:
            stdout, stderr = p.communicate(timeout=15)
            report['native_resource_less_run'] = dict(returncode=p.returncode, stdout=stdout[:8000], stderr=stderr[:8000])
        except subprocess.TimeoutExpired:
            os.killpg(p.pid, signal.SIGKILL)
            stdout, stderr = p.communicate()
            report['native_resource_less_run'] = dict(timeout=True, stdout=stdout[:8000], stderr=stderr[:8000])
        # Always retain observations before asserting, including environmental failures.
        (a.out/'installer-audit.json').write_text(json.dumps(report, indent=2))
        check(report['native_resource_less_run'].get('returncode') == 252,
              'native resource-less installer did not reproduce exit 252; inspect audit evidence')
    else:
        report['native_resource_less_run'] = {'not_run': 'requires macOS; static inspection only'}
    (a.out/'installer-audit.json').write_text(json.dumps(report, indent=2))
    print(json.dumps({k: v for k, v in report.items() if k not in ('binary', 'entry_disassembly')}, indent=2))

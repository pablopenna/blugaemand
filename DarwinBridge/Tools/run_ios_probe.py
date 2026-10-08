#!/usr/bin/env python3
"""Run the complete runtime source in an iOS simulator; never label as device proof."""
import json,os,platform,plistlib,shutil,subprocess
from pathlib import Path
out=Path('artifacts/loader-audit/ios-simulator').resolve();out.mkdir(parents=True,exist_ok=True)
def run(args,**kw):return subprocess.run(args,check=True,**kw)
def output(args):return subprocess.check_output(args,text=True).strip()
devices=json.loads(output(['xcrun','simctl','list','devices','available','--json']))['devices']
pairs=[(runtime,d) for runtime,ds in devices.items() if '.iOS-' in runtime for d in ds if d['name'].startswith('iPhone')]
assert pairs,'No available iPhone simulator on CI runner'
runtime,device=pairs[-1];udid=device['udid']
arch='arm64' if platform.machine()=='arm64' else 'x86_64'
app=out/'DBRuntimeProbe.app';app.mkdir(exist_ok=True);frameworks=app/'Frameworks'
env=dict(os.environ,DB_RUNTIME_SDK='iphonesimulator',DB_RUNTIME_TARGET=f'{arch}-apple-ios17.0-simulator',DB_RUNTIME_OUT=str(frameworks))
run(['bash','DarwinBridge/LiveContainerPlugin/build-plugin.sh'],env=env)
sdk=output(['xcrun','--sdk','iphonesimulator','--show-sdk-path'])
run(['xcrun','--sdk','iphonesimulator','clang','-target',f'{arch}-apple-ios17.0-simulator','-isysroot',sdk,'-fobjc-arc','-fblocks','DarwinBridge/Tests/ios_runtime_probe.m','-framework','UIKit','-framework','Foundation','-Wl,-rpath,@executable_path/Frameworks','-Wl,-needed_library,'+str(frameworks/'DBFoundation.dylib'),'-o',str(app/'DBRuntimeProbe')])
bundle='com.darwinbridge.runtimeprobe'
(app/'Info.plist').write_bytes(plistlib.dumps(dict(CFBundleIdentifier=bundle,CFBundleExecutable='DBRuntimeProbe',CFBundleName='DBRuntimeProbe',CFBundlePackageType='APPL',CFBundleVersion='1',CFBundleShortVersionString='1.0',MinimumOSVersion='17.0',UIDeviceFamily=[1,2],UILaunchScreen={})))
run(['codesign','--force','--sign','-',str(app)])
if device['state']!='Booted':run(['xcrun','simctl','boot',udid])
run(['xcrun','simctl','bootstatus',udid,'-b'],timeout=240)
run(['xcrun','simctl','install',udid,str(app)])
# Avoid --console-pty: it can keep the simctl process open indefinitely
# even after the app has launched. The probe writes a persistent log itself.
launch=subprocess.run(['xcrun','simctl','launch','--terminate-running-process',udid,bundle],
                      text=True,capture_output=True,timeout=45)
(out/'launch.log').write_text(launch.stdout+launch.stderr)
print(launch.stdout+launch.stderr)
assert launch.returncode == 0,'simctl launch failed'
import time
container=Path(output(['xcrun','simctl','get_app_container',udid,bundle,'data']))
logfile=container/'Documents/DarwinBridge-runtime.log'
deadline=time.monotonic()+75
contents=''
while time.monotonic()<deadline:
    if logfile.exists():
        contents=logfile.read_text(errors='replace')
        if 'DB_IOS_RUNTIME_PROBE_PASS' in contents:break
    time.sleep(2)
(out/'launch.log').write_text(launch.stdout+launch.stderr+'\n'+contents)
assert 'is implemented in both' not in contents+launch.stderr,'runtime class name collision'
assert 'DB_IOS_RUNTIME_PROBE_PASS' in contents,'iOS runtime probe did not reach successful UIKit startup; see launch.log'
container=Path(output(['xcrun','simctl','get_app_container',udid,bundle,'data']))
shutil.copy2(container/'Documents/DarwinBridge-runtime.log',out/'DarwinBridge-runtime.log')
(out/'result.json').write_text(json.dumps(dict(passed=True,runtime=runtime,device=device['name'],architecture=arch,physical_ipad_tested=False),indent=2))
# Simulator binaries are test artifacts only; retain logs/metadata, not a misleading IPA.
shutil.rmtree(app)

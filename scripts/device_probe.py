"""Capture actual attached device evidence; no compatibility claims from marketing specs."""
import argparse, datetime, json, subprocess
from pathlib import Path

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--adb', default=str(Path(__file__).resolve().parents[1]/'.tools/android-sdk/platform-tools/adb.exe'))
    p.add_argument('--serial', required=True)
    p.add_argument('--output',type=Path,default=Path('reports/device-probe.json'))
    args=p.parse_args()
    def adb(*command):
        return subprocess.check_output([args.adb,'-s',args.serial,*command],text=True,timeout=30).strip()
    if adb('get-state')!='device':
        raise RuntimeError('Authorized device required')
    props=['ro.product.manufacturer','ro.product.model','ro.soc.manufacturer','ro.soc.model','ro.product.cpu.abilist','ro.build.version.sdk','ro.build.version.release','ro.build.fingerprint']
    result={'version':1,'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'properties':{key:adb('shell','getprop',key) for key in props},
            'cpuinfo':adb('shell','cat','/proc/cpuinfo'), 'meminfo':adb('shell','cat','/proc/meminfo'),'storage':adb('shell','df','-k','/data'),'pageSize':adb('shell','getconf','PAGESIZE'),
            'battery':adb('shell','dumpsys','battery'), 'thermal':adb('shell','dumpsys','thermalservice'),
            'airplaneMode':adb('shell','settings','get','global','airplane_mode_on'),
            'wifi':adb('shell','settings','get','global','wifi_on'),
            'bluetooth':adb('shell','settings','get','global','bluetooth_on'),
            'note':'Hardware evidence only. Successful PTE generation is required for runtime compatibility.'}
    if 'arm64-v8a' not in result['properties']['ro.product.cpu.abilist']:
        raise RuntimeError('Device does not advertise ARM64 ABI')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(args.output)
if __name__=='__main__': main()

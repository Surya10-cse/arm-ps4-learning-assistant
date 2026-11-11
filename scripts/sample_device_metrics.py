"""Timestamped actual-device sampling. A sampled maximum is not an unsampled true peak.
No Llama benchmark is implied unless the caller actually runs a provisioned model.
"""
import argparse, datetime, json, re, subprocess, time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def parse_pss(raw):
    match=re.search(r'TOTAL PSS:\s*(\d+)',raw) or re.search(r'^\s*TOTAL\s+(\d+)\s',raw,re.M)
    return int(match.group(1)) if match else None

def parse_cpu_ticks(raw):
    try:
        fields=raw.rsplit(')',1)[1].split()
        return int(fields[11])+int(fields[12]),int(fields[19])
    except (IndexError,ValueError):return None

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--serial',required=True);p.add_argument('--package',default='ai.adapt.app')
    p.add_argument('--seconds',type=int,default=60);p.add_argument('--interval',type=float,default=2)
    p.add_argument('--label',required=True);p.add_argument('--output',type=Path,required=True)
    args=p.parse_args()
    if not 1<=args.seconds<=3600 or not 0.5<=args.interval<=60: raise ValueError('Invalid sampling duration/interval')
    if not re.fullmatch(r'[A-Za-z0-9_.]+',args.package): raise ValueError('Invalid package')
    adb=str(ROOT/'.tools/android-sdk/platform-tools/adb.exe')
    def read(*command):
        result=subprocess.run([adb,'-s',args.serial,'shell',*command],capture_output=True,text=True,timeout=15)
        return result.stdout.strip() if result.returncode==0 else None
    hertz_raw=read('getconf','CLK_TCK') or ''
    hertz=int(hertz_raw) if hertz_raw.isdigit() and int(hertz_raw)>0 else None
    started=time.monotonic();rows=[];previous=None
    while time.monotonic()-started<args.seconds:
        mem=read('dumpsys','meminfo',args.package) or ''
        pid=(read('pidof',args.package) or '').split()
        pid=pid[0] if pid and pid[0].isdigit() else None
        ticks=parse_cpu_ticks(read('cat',f'/proc/{pid}/stat') or '') if pid else None
        cpu_at=time.monotonic();cpu_percent=None
        if hertz and ticks and previous and previous[:2]==(pid,ticks[1]) and ticks[0]>=previous[2] and cpu_at>previous[3]:
            cpu_percent=(ticks[0]-previous[2])/hertz/(cpu_at-previous[3])*100
        previous=(pid,ticks[1],ticks[0],cpu_at) if ticks else None
        cpu=read('dumpsys','cpuinfo') or ''
        cpu_line=next((line.strip() for line in cpu.splitlines() if pid and re.search(r'\s'+re.escape(pid)+r'/',line)),'')
        battery=read('dumpsys','battery') or ''
        charge=re.search(r'Charge counter:\s*(-?\d+)',battery,re.I)
        temperature=re.search(r'^\s*temperature:\s*(-?\d+)',battery,re.M)
        rows.append({'elapsedSeconds':round(time.monotonic()-started,3),'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'pssKb':parse_pss(mem),'cpuDumpsysLine':cpu_line or None,'cpuPercentOneCore':cpu_percent,'cpuSampleElapsedSeconds':round(cpu_at-started,3),
            'batteryChargeCounterRaw':int(charge.group(1)) if charge else None,
            'batteryTemperatureTenthsC':int(temperature.group(1)) if temperature else None,
            'batteryRaw':battery,'thermalRaw':read('dumpsys','thermalservice')})
        if time.monotonic()-started<args.seconds: time.sleep(min(args.interval,max(0,args.seconds-(time.monotonic()-started))))
    pss=[r['pssKb'] for r in rows if r['pssKb'] is not None]
    cpu_values=[r['cpuPercentOneCore'] for r in rows if r['cpuPercentOneCore'] is not None]
    result={'version':1,'label':args.label,'package':args.package,'requestedIntervalSeconds':args.interval,'actualDurationSeconds':round(time.monotonic()-started,3),'clockTicksPerSecond':hertz,'maximumObservedCpuPercentOneCore':max(cpu_values) if cpu_values else None,
        'samples':rows,'maximumSampledPssKb':max(pss) if pss else None,
        'limits':'Sampled process PSS, not true peak. CPU percent derives process utime+stime deltas over actual monotonic sample intervals; 100% means one fully busy core and multi-core values can exceed 100%. dumpsys CPU has an OS-defined averaging window. Raw charge counter is not calibrated energy or model-only power. An idle/core-UI baseline is not Llama inference performance.'}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print('Captured',len(rows),'actual device samples in',args.output)
if __name__=='__main__':main()

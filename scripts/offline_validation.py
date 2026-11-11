"""Run actual-phone tests with radios disabled; always restore the original radio settings.
Tests target isolated package data. No cloud or synthetic inference result is accepted.
"""
import argparse, datetime, json, os, subprocess, time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True);args=p.parse_args()
    adb=str(ROOT/'.tools/android-sdk/platform-tools/adb.exe')
    def command(*parts): return subprocess.check_output([adb,'-s',args.serial,*parts],text=True,timeout=30).strip()
    def radios():
        return {name:command('shell','settings','get','global',key) for name,key in [('airplane','airplane_mode_on'),('wifi','wifi_on'),('bluetooth','bluetooth_on')]}
    before=radios();report={'version':1,'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'before':before,'scope':'Actual-phone core learning and missing-model behavior. Real Llama generation remains blocked if runtime test is skipped.'}
    try:
        command('shell','cmd','connectivity','airplane-mode','enable')
        command('shell','svc','wifi','disable')
        command('shell','cmd','bluetooth_manager','disable')
        for _ in range(15):
            observed=radios()
            if observed=={'airplane':'1','wifi':'0','bluetooth':'0'}:break
            time.sleep(1)
        report['during']=observed
        if observed!={'airplane':'1','wifi':'0','bluetooth':'0'}: raise RuntimeError('Phone radios did not reach verified offline state')
        log=ROOT/'reports/offline-device-tests.log';log.parent.mkdir(exist_ok=True)
        env=os.environ.copy();env['ANDROID_SERIAL']=args.serial
        with log.open('w',encoding='utf-8') as output:
            result=subprocess.run(['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'scripts/validate-device.ps1')],cwd=ROOT,env=env,stdout=output,stderr=subprocess.STDOUT,timeout=900)
        report['testExitCode']=result.returncode
        report['result']='PASS_CORE' if result.returncode==0 else 'FAIL'
    finally:
        restoration=[]
        for parts in [('cmd','connectivity','airplane-mode','enable' if before['airplane']=='1' else 'disable'),('svc','wifi','enable' if before['wifi'] in ('1','2') else 'disable'),('cmd','bluetooth_manager','enable' if before['bluetooth']=='1' else 'disable')]:
            try: command('shell',*parts)
            except Exception as error: restoration.append(type(error).__name__)
        for _ in range(15):
            after=radios()
            if after==before:break
            time.sleep(1)
        report['after']=after;report['restoreErrors']=restoration
        report['restored']=after==before and not restoration
        report['finishedAt']=datetime.datetime.now(datetime.timezone.utc).isoformat()
        target=ROOT/'reports/offline-device-validation.json';target.parent.mkdir(exist_ok=True)
        target.write_text(json.dumps(report,indent=2),encoding='utf-8')
        print(target)
    if report.get('testExitCode')!=0 or not report['restored']: raise SystemExit(1)
if __name__=='__main__':main()

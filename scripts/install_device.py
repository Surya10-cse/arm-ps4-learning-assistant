"""Install debug update without uninstalling; verify retained learner row counts and schema.
Private snapshots are ignored local files; submission gets counts, never learner values.
"""
import argparse, datetime, json, sqlite3, subprocess, time, uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser();p.add_argument('--serial',required=True);args=p.parse_args()
    adb=str(ROOT/'.tools/android-sdk/platform-tools/adb.exe')
    def raw(*parts):return subprocess.check_output([adb,'-s',args.serial,*parts],timeout=90)
    def snapshot(label):
        raw('shell','am','force-stop','ai.adapt.app')
        folder=ROOT/'.tools/device-upgrade-check'/f'{label}-{uuid.uuid4()}'
        folder.mkdir(parents=True,exist_ok=True)
        names=raw('shell','run-as','ai.adapt.app','ls','databases').decode().split()
        for name in ['adapt-v1.db','adapt-v1.db-wal','adapt-v1.db-shm']:
            if name in names:(folder/name).write_bytes(raw('exec-out','run-as','ai.adapt.app','cat','databases/'+name))
        with sqlite3.connect(str(folder/'adapt-v1.db')) as db:
            counts={name:db.execute('SELECT COUNT(*) FROM '+name).fetchone()[0] for name in ['profiles','answers','mastery','sessions','messages','misconceptions','revisions']}
            return {'schemaVersion':db.execute('PRAGMA user_version').fetchone()[0],'counts':counts,'questionCount':db.execute('SELECT COUNT(*) FROM questions').fetchone()[0]}
    before=snapshot('before')
    apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    installed=raw('install','-r',str(apk)).decode().strip()
    if 'Success' not in installed:raise RuntimeError('Debug update failed')
    raw('shell','am','start','-W','-n','ai.adapt.app/.MainActivity')
    time.sleep(4)
    pid=raw('shell','pidof','ai.adapt.app').decode().strip()
    if not pid:raise RuntimeError('Application did not stay alive after update')
    after=snapshot('after')
    preserved=before['counts']==after['counts']
    result={'version':1,'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'before':before,'after':after,'learnerRowsPreserved':preserved,'profileValuesLogged':False,'install':'adb install -r','appStayedAlive':True}
    report=ROOT/'reports/installed-device-upgrade.json';report.write_text(json.dumps(result,indent=2),encoding='utf-8')
    raw('shell','am','start','-W','-n','ai.adapt.app/.MainActivity')
    print('Installed updated debug app; learner rows preserved:',preserved)
    if not preserved or after['schemaVersion']!=2:raise SystemExit(1)
if __name__=='__main__':main()

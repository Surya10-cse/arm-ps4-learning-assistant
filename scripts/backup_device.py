"""Preserve the installed debug app's Room files before a non-destructive update.
Backups stay in ignored .tools, never submission. Does not print learner values.
"""
import argparse, datetime, json, subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def main():
    p=argparse.ArgumentParser()
    p.add_argument('--serial',required=True)
    args=p.parse_args()
    adb=str(ROOT/'.tools/android-sdk/platform-tools/adb.exe')
    def command(*parts): return subprocess.check_output([adb,'-s',args.serial,*parts],timeout=30)
    command('shell','am','force-stop','ai.adapt.app')
    dest=ROOT/'.tools/device-backups'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    dest.mkdir(parents=True,exist_ok=True)
    files=command('shell','run-as','ai.adapt.app','ls','databases').decode().split()
    copied=[]
    for name in ['adapt-v1.db','adapt-v1.db-wal','adapt-v1.db-shm']:
        if name in files:
            (dest/name).write_bytes(command('exec-out','run-as','ai.adapt.app','cat','databases/'+name))
            copied.append(name)
    (dest/'backup.json').write_text(json.dumps({'capturedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'package':'ai.adapt.app','files':copied},indent=2),encoding='utf-8')
    print('Preserved',len(copied),'database files in ignored local backup directory')
if __name__=='__main__': main()

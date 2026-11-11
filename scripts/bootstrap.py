"""Download isolated build tools. Never alters global environment or registry."""
from pathlib import Path
import concurrent.futures, urllib.request, zipfile, json

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)

def fetch(url, name):
    dest = TOOLS / name
    if not dest.exists():
        print('Downloading', name, flush=True)
        urllib.request.urlretrieve(url, dest)
    return dest

def install(url, name, target):
    dest = TOOLS / target
    if not dest.exists():
        archive = fetch(url, name)
        with zipfile.ZipFile(archive) as z:
            z.extractall(dest)
    return dest

if __name__ == '__main__':
    jobs = [
      ('https://services.gradle.org/distributions/gradle-8.11.1-bin.zip', 'gradle.zip', 'gradle'),
      ('https://download.oracle.com/java/21/archive/jdk-21.0.7_windows-x64_bin.zip', 'jdk.zip', 'jdk'),
      ('https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip', 'sdk.zip', 'sdk-tools'),
    ]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for result in pool.map(lambda args: install(*args), jobs):
            print('Installed', result, flush=True)
    fetch('https://repo.maven.apache.org/maven2/org/pytorch/executorch-android/1.0.0/executorch-android-1.0.0.aar', 'executorch-1.0.0.aar')

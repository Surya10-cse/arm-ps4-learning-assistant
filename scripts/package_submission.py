"""Package verified artifacts. Test counts and device conclusions come from actual evidence."""
from pathlib import Path
import datetime, hashlib, io, json, os, shutil, subprocess, unittest, zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
def sha(path):
    digest=hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda:source.read(1024*1024),b''):digest.update(chunk)
    return digest.hexdigest()

def suites(paths):
    result=[]
    for path in paths:
        root=ET.parse(path).getroot()
        item={key:int(root.attrib.get(key,'0')) for key in ['tests','failures','errors','skipped']}
        item.update(name=root.attrib['name'],file=str(path.relative_to(ROOT)),cases=[{'name':case.attrib['name'],'class':case.attrib['classname'],'status':'SKIPPED' if case.find('skipped') is not None else 'FAIL' if case.find('failure') is not None or case.find('error') is not None else 'PASS'} for case in root.findall('testcase')])
        if item['failures'] or item['errors']:raise RuntimeError('Failed suite: '+item['name'])
        result.append(item)
    return result

def inspect_apk(path,tools,java):
    permissions=subprocess.check_output([str(tools/'aapt.exe'),'dump','permissions',str(path)],text=True)
    badging=subprocess.check_output([str(tools/'aapt.exe'),'dump','badging',str(path)],text=True)
    if 'android.permission.INTERNET' in permissions:raise RuntimeError('Unexpected INTERNET permission')
    with zipfile.ZipFile(path) as archive:
        native=[n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
        if not native or any(not n.startswith('lib/arm64-v8a/') for n in native):raise RuntimeError('Expected ARM64-only libraries')
        if 'lib/arm64-v8a/libexecutorch.so' not in native:raise RuntimeError('ExecuTorch missing')
    signing=subprocess.run([str(java),'-jar',str(tools/'lib/apksigner.jar'),'verify','--verbose',str(path)],capture_output=True,text=True)
    return {'bytes':path.stat().st_size,'sha256':sha(path),'signed':signing.returncode==0,'debuggable':'application-debuggable' in badging,'metadata':'\n'.join(line for line in badging.splitlines() if line.startswith(('package:','sdkVersion:','targetSdkVersion:','native-code:','application-label:'))),'signatureVerification':signing.stdout if signing.returncode==0 else 'Unsigned candidate; external team signing required','noInternetPermission':True,'arm64ExecuTorchPackaged':True}

def main():
    now=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=5,minutes=30))).isoformat(timespec='seconds')
    output=ROOT/'submission';output.mkdir(exist_ok=True)
    reports=ROOT/'reports';reports.mkdir(exist_ok=True)
    host=suites([p for pattern in ['core/build/test-results/test/TEST-*.xml','inference/build/test-results/testDebugUnitTest/TEST-*.xml','data/build/test-results/testDebugUnitTest/TEST-*.xml'] for p in ROOT.glob(pattern)])
    required={'ai.adapt.core.LearningTest','ai.adapt.core.TutorTest','ai.adapt.core.AnalyticsTest','ai.adapt.inference.TutorJsonTest','ai.adapt.data.HostRepositoryTest'}
    if not required.issubset({s['name'] for s in host}):raise RuntimeError('Required host suites missing')
    device=suites([p for pattern in ['data/build/outputs/androidTest-results/connected/debug/TEST-*.xml','app/build/outputs/androidTest-results/connected/validation/TEST-*.xml'] for p in ROOT.glob(pattern)])
    if len(device)<2:raise RuntimeError('Actual-device reports missing')
    offline=json.loads((reports/'offline-device-validation.json').read_text(encoding='utf-8'))
    if offline.get('result')!='PASS_CORE' or not offline.get('restored'):raise RuntimeError('Offline test or radio restoration failed')
    upgrade=json.loads((reports/'installed-device-upgrade.json').read_text(encoding='utf-8'))
    if not upgrade.get('learnerRowsPreserved'):raise RuntimeError('Data preservation not verified')
    stream=io.StringIO()
    tests=unittest.TextTestRunner(stream=stream,verbosity=2).run(unittest.defaultTestLoader.discover(str(ROOT/'tests')))
    (reports/'python-tests.txt').write_text(stream.getvalue(),encoding='utf-8')
    if not tests.wasSuccessful():raise RuntimeError('Python tests failed')
    python_result={'tests':tests.testsRun,'failures':len(tests.failures),'errors':len(tests.errors),'skipped':len(tests.skipped)}
    (reports/'python-test-results.json').write_text(json.dumps(python_result,indent=2),encoding='utf-8')
    lint={}
    for variant in ['debug','release']:
        issues=ET.parse(ROOT/f'app/build/reports/lint-results-{variant}.xml').getroot().findall('issue')
        if any(i.attrib.get('severity') in ['Error','Fatal'] for i in issues):raise RuntimeError('Lint errors: '+variant)
        lint[variant]=[{'id':i.attrib['id'],'message':i.attrib['message']} for i in issues if i.attrib.get('severity')=='Warning']
    tools=ROOT/'.tools/android-sdk/build-tools/35.0.0';java=next((ROOT/'.tools/jdk').glob('*/bin/java.exe'))
    debug=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    releases=list((ROOT/'app/build/outputs/apk/release').glob('*.apk'))
    if not releases:raise RuntimeError('Release candidate missing')
    release=next((p for p in releases if p.name=='app-release.apk'),releases[0])
    artifacts={'debug':inspect_apk(debug,tools,java),'release':inspect_apk(release,tools,java)}
    if not artifacts['debug']['signed']:raise RuntimeError('Debug signature failed')
    if artifacts['release']['debuggable']:raise RuntimeError('Release is debuggable')
    shutil.copy2(debug,output/'ADAPT-AI-debug.apk')
    release_name='ADAPT-AI-release.apk' if artifacts['release']['signed'] else 'ADAPT-AI-release-unsigned.apk'
    shutil.copy2(release,output/release_name)
    catalogue=json.loads((output/'catalogue.v2.json').read_text(encoding='utf-8'))
    if len({q['id'] for q in catalogue['questions']})!=len(catalogue['questions']):raise RuntimeError('Duplicate catalogue questions')
    summary={'version':2,'generatedAt':now,'host':host,'device':device,'python':python_result,'lint':lint,'artifacts':artifacts,'catalogue':{'skills':len(catalogue['skills']),'questions':len(catalogue['questions'])}}
    (reports/'test-and-artifact-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
    host_count=sum(s['tests']-s['skipped'] for s in host)
    device_pass=sum(s['tests']-s['skipped'] for s in device);device_skip=sum(s['skipped'] for s in device)
    build=f'''# Build and validation — {now}

Existing modules preserved; product version 0.2.0. Host checks and actual-device core learning passed. Authorized Llama artifacts are absent: PTE generation, five-mode quality, offline inference and INT4/INT8 performance remain BLOCKED.

| Check | Actual result |
|---|---|
| JVM/Robolectric | {host_count} passed |
| Python | {tests.testsRun} passed |
| Actual P4 Pro Android tests | {device_pass} passed, {device_skip} skipped; individual cases below |
| Offline core | PASS_CORE; airplane on / Wi-Fi off / Bluetooth off; original radios restored |
| Existing learner update | Row counts preserved; schema {upgrade['before']['schemaVersion']} → {upgrade['after']['schemaVersion']} |
| Debug lint | 0 errors, {len(lint['debug'])} warnings |
| Release lint | 0 errors, {len(lint['release'])} warnings |
| Catalogue | {len(catalogue['skills'])} competencies, {len(catalogue['questions'])} curated questions |
| Permissions / JNI package | No INTERNET; actual ARM64 ExecuTorch library |
| Release signing | {'Signature verified' if artifacts['release']['signed'] else 'BLOCKED: unsigned non-debuggable candidate; external team keystore not configured'} |

## Artifacts

'''
    for name,info in artifacts.items():build+=f"- {name}: {info['bytes']} bytes; SHA-256 `{info['sha256']}`; signed={info['signed']}; debuggable={info['debuggable']}\n"
    build+='\n## Host suites\n\n'+'\n'.join(f"- {s['name']}: {s['tests']} tests, {s['skipped']} skipped" for s in host)
    build+='\n\n## Actual-device cases\n\n'+'\n'.join(f"- {c['status']}: {c['class']}.{c['name']}" for s in device for c in s['cases'])
    build+='\n\n## Lint warnings\n\n'+'\n'.join(f"- {v}: {w['id']} — {w['message']}" for v,warnings in lint.items() for w in warnings)
    build+='\n\nVersions remain pinned. ChromeOS x86 support is outside the ARM-phone target. No lint baseline suppresses errors. A skipped real-model test is not successful inference.\n'
    (reports/'BUILD_VALIDATION.md').write_text(build,encoding='utf-8')
    hardware=json.loads((reports/'device-probe.json').read_text(encoding='utf-8'))
    sample=json.loads((reports/'core-ui-device-samples.json').read_text(encoding='utf-8'))
    native=next((c for s in device for c in s['cases'] if c['name']=='officialExecuTorchNativeLibraryInitializesOnArm64'),None)
    report=f'''# Actual-device validation — {now}

User's Realme P4 Pro 5G: {hardware['properties']['ro.product.model']}; SoC property {hardware['properties']['ro.soc.model']}; Android API {hardware['properties']['ro.build.version.sdk']}; ABI {hardware['properties']['ro.product.cpu.abilist']}; kernel page size {hardware.get('pageSize','not captured')} bytes. Raw properties are in device-probe.json. Measured properties take precedence over manufacturer specifications.

Core UI/scoring/adaptation, persistence, migration and missing-model behavior passed physically with airplane mode enabled and Wi-Fi/Bluetooth disabled. Original radios were restored. Official native ExecuTorch class/JNI initialization: {native['status'] if native else 'NOT TESTED'}. This verifies library linkage, not model operators, tokenizer pairing, generated answers or per-operator acceleration.

The production debug update used `adb install -r`, preserved learner row counts and retained the database filename. Private snapshots remain in ignored .tools; no learner values were copied into submission reports. UI tests used an isolated validation package. Screenshots show its explicitly named test learner and genuinely scored test responses, not simulated learner statistics or Llama responses.

## Actual non-inference baseline

Label `{sample['label']}`; package `{sample['package']}`. {len(sample['samples'])} samples over {sample['actualDurationSeconds']} seconds. Maximum sampled PSS: {sample['maximumSampledPssKb']} KiB. Maximum observed interval CPU: {sample.get('maximumObservedCpuPercentOneCore')}% of one core. These are core-UI measurements with no model, not Llama performance or true unsampled peak memory. Raw samples include thermal output, battery temperature and charge counters. USB charging prevents a defensible battery-consumption result.

## Blocked acceptance

No authorized original checkpoint/tokenizer or compatible trusted bundle is provisioned. PTE execution, real five-mode output, loaded-model cancellation, offline inference, quality and INT4/INT8 comparisons remain BLOCKED. The real-model test is explicitly skipped. No model token rate, first-token latency, peak memory, CPU or battery result is fabricated.
'''
    (reports/'DEVICE_VALIDATION.md').write_text(report,encoding='utf-8')
    test_folder=reports/'test-results';test_folder.mkdir(exist_ok=True)
    for s in host+device:
        source=ROOT/s['file'];shutil.copy2(source,test_folder/source.name)
    for image in (reports/'screenshots').glob('*.png'):
        if not image.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):raise RuntimeError('Invalid evidence screenshot')
    shutil.copytree(ROOT/'docs',output/'docs',dirs_exist_ok=True)
    shutil.copytree(reports,output/'reports',dirs_exist_ok=True)
    shutil.copytree(ROOT/'schemas',output/'schemas',dirs_exist_ok=True)
    shutil.copytree(ROOT/'models',output/'model-tooling',dirs_exist_ok=True,ignore=shutil.ignore_patterns('__pycache__','*.pte','*.pth','*.model'))
    shutil.copy2(ROOT/'README.md',output/'README.md');shutil.copy2(reports/'BUILD_VALIDATION.md',output/'BUILD_VALIDATION.md')
    excluded={'.tools','.gradle','.kotlin','.git','build','submission','__pycache__','.venv','.aws','.codex','.agents'}
    forbidden={'.pte','.pth','.model','.safetensors','.jks','.keystore','.p12','.db'}
    with zipfile.ZipFile(output/'ADAPT-AI-source.zip','w',zipfile.ZIP_DEFLATED) as archive:
        for directory,subdirs,files in os.walk(ROOT):
            subdirs[:]=[n for n in subdirs if n not in excluded]
            for name in files:
                file=Path(directory)/name
                if name!='local.properties' and not name.startswith('.env') and file.suffix not in forbidden:archive.write(file,Path('ADAPT-AI')/file.relative_to(ROOT))
    (output/'SHA256SUMS.txt').write_text('\n'.join(f'{sha(f)}  {f.relative_to(output).as_posix()}' for f in sorted(output.rglob('*')) if f.is_file() and f.name!='SHA256SUMS.txt')+'\n',encoding='utf-8')
    print(f'Packaged: {host_count} host + {tests.testsRun} Python + {device_pass} device passed; {device_skip} device skipped.')
    print('Release:',release_name,'; real Llama acceptance remains blocked without authorized artifacts.')

if __name__=='__main__':main()

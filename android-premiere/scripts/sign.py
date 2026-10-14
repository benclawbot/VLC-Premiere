#!/usr/bin/env python3
"""Align, sign with a persistent local development certificate, and verify variants."""
from pathlib import Path
import hashlib, json, os, secrets, subprocess, zipfile
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from toolchain import java_tool
from signing_config import select_signing, verify_certificate

root = Path(__file__).resolve().parents[2]/'android-premiere'
java = java_tool('java')
keytool = java_tool('keytool')
bt = root/'tools/build-tools/android-16'
signing = root/'signing'; signing.mkdir(exist_ok=True,mode=0o700)
config = select_signing(root)
if config['mode'] == 'release':
    from release_gate import require_no_open_issues
    # Fail before touching release keys or producing signed APKs.
    require_no_open_issues(os.environ.get('PREMIERE_RELEASE_GITHUB_TOKEN'))
key, password, alias = config['key'], config['password'], config['alias']
def run(args, **kw): return subprocess.run([str(a) for a in args],check=True,**kw)
if config['mode'] == 'development' and not key.exists():
    password.write_text(secrets.token_urlsafe(32)); password.chmod(0o600)
    run([keytool,'-genkeypair','-keystore',key,'-storetype','PKCS12',
        '-storepass:file',password,'-alias','premiere','-keyalg','RSA','-keysize','3072',
        '-validity','10000','-dname','CN=VLC Premiere Local Development, OU=Local Rebuild, O=Local Development', '-noprompt'])
    key.chmod(0o600)
source = root/'dist/VLC-Premiere-unsigned.apk'
manifest=json.loads((root/'build/native-source-manifest.json').read_text())
current_hash=hashlib.sha256()
for path in sorted((root/'native').rglob('*.java')):
    current_hash.update(str(path.relative_to(root/'native')).encode())
    current_hash.update(path.read_bytes())
assert current_hash.hexdigest()==manifest['sources_sha256'],'Native source changed after compilation'
with zipfile.ZipFile(source) as z:
    assert hashlib.sha256(z.read('classes7.dex')).hexdigest()==manifest['dex_sha256'],'APK contains an older native UI DEX'
results=[]
for suffix, abi in [('Universal',None),('arm64','arm64-v8a')]:
    input_apk = source
    if abi:
        input_apk = root/'build'/f'VLC-Premiere-{suffix}-unsigned.apk'
        with zipfile.ZipFile(source) as inp, zipfile.ZipFile(input_apk,'w') as out:
            for item in inp.infolist():
                if item.filename.startswith('lib/') and not item.filename.startswith('lib/'+abi+'/'): continue
                out.writestr(item,inp.read(item))
    aligned = root/'build'/f'VLC-Premiere-{suffix}-aligned.apk'
    final = root/'dist'/f'VLC-Premiere-{suffix}.apk'
    run([bt/'zipalign','-f','-P','16','4',input_apk,aligned])
    run([java,'-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias',alias,
        '--ks-pass','file:'+str(password),'--out',final,aligned])
    log = run([java,'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',final],capture_output=True,text=True)
    (root/'build'/f'signature-{suffix}.txt').write_text(log.stdout)
    certificate_sha256 = verify_certificate(log.stdout, config)
    run([bt/'zipalign','-c','-P','16','4',final])
    with zipfile.ZipFile(final) as z:
        assert z.testzip() is None
        libs=[i.filename for i in z.infolist() if i.filename.startswith('lib/') and i.filename.endswith('.so')]
        assert len(libs) == (4 if abi else 16)
        assert all(z.getinfo(n).compress_type == zipfile.ZIP_STORED for n in libs)
        assert all('classes'+('' if i==1 else str(i))+'.dex' in z.namelist() for i in range(1,8))
    results.append({'file':final.name,'size':final.stat().st_size,'sha256':hashlib.sha256(final.read_bytes()).hexdigest(),
        'abis':[abi] if abi else ['arm64-v8a','armeabi-v7a','x86','x86_64'],'native_libraries':len(libs),'signing_mode':config['mode'],
        'certificate_sha256':certificate_sha256})
    print(final.name,'signature, alignment, ZIP and native payload verified',flush=True)
(root/'dist/SHA256SUMS').write_text(''.join(r['sha256']+'  '+r['file']+'\n' for r in results))
(root/'build/artifacts.json').write_text(json.dumps(results,indent=2)+'\n')

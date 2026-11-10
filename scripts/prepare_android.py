"""Fetch pinned official assets for the self-contained APK. Never commits model binaries."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path, PurePosixPath
import hashlib
import io
import json
import shutil
import tarfile
import time
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parent.parent
ASSETS=ROOT/'android/app/src/full/assets'
SHARED_ASSETS=ROOT/'android/shared-assets'
CACHE=ROOT/'.setup/android-build-cache'
MANIFEST=json.loads((ROOT/'shared/voicevox-assets.json').read_text())
CATALOG=json.loads((ROOT/'shared/voicevox-catalog.json').read_text(encoding='utf-8'))

def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()

def fetch(asset):
    CACHE.mkdir(parents=True,exist_ok=True)
    target=CACHE/(asset['sha256']+'-'+asset['url'].rsplit('/',1)[1])
    if target.exists() and digest(target)==asset['sha256']:return target
    for attempt in range(4):
        try:
            request=urllib.request.Request(asset['url']+f'?kotoba={time.time_ns()}',headers={'User-Agent':'Kotoba-Android-Build'})
            with urllib.request.urlopen(request,timeout=180) as response,target.open('wb') as out:shutil.copyfileobj(response,out,1024*1024)
            if digest(target)!=asset['sha256']:raise RuntimeError('SHA-256 mismatch: '+target.name)
            return target
        except Exception:
            if attempt==3:raise
            time.sleep(2*(attempt+1))

def copy_bytes(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(data)

def native(item):
    abi,assets=item
    destination=ROOT/'android/app/src/full/jniLibs'/abi
    with zipfile.ZipFile(fetch(assets['core'])) as archive:
        for name in archive.namelist():
            if name.endswith('/libvoicevox_core.so'):copy_bytes(destination/'libvoicevox_core.so',archive.read(name))
            elif name.endswith('/include/voicevox_core.h'):copy_bytes(ROOT/'android/app/src/main/cpp/vendor/voicevox_core.h',archive.read(name))
            elif name.endswith('/LICENSE'):copy_bytes(ASSETS/'voicevox/licenses/voicevox-core-LICENSE.txt',archive.read(name))
    with tarfile.open(fetch(assets['onnxruntime']),'r:gz') as archive:
        for member in archive.getmembers():
            if not member.isfile():continue
            basename=PurePosixPath(member.name).name
            if basename=='libvoicevox_onnxruntime.so':copy_bytes(destination/basename,archive.extractfile(member).read())
            elif basename in ('TERMS.txt','third-party-notices.html'):copy_bytes(ASSETS/'voicevox/licenses'/('onnx-'+basename),archive.extractfile(member).read())
    if not (destination/'libvoicevox_core.so').is_file() or not (destination/'libvoicevox_onnxruntime.so').is_file():raise RuntimeError('Native libraries missing: '+abi)

def model(item):
    filename,asset=item
    source=fetch(asset)
    if not filename.endswith('.vvm'):
        copy_bytes(ASSETS/'voicevox/licenses'/filename,source.read_bytes())
        return []
    destination=ASSETS/'voicevox/models'/filename
    destination.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(source,destination)
    with zipfile.ZipFile(source) as archive:return [(filename,character) for character in json.loads(archive.read('metas.json'))]

def main():
    web=SHARED_ASSETS/'web'
    web.mkdir(parents=True,exist_ok=True)
    for name in ('index.html','app.js','platform.js','theme.js'):shutil.copyfile(ROOT/'public'/name,web/name)
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(native,MANIFEST['native'].items()))
        characters=[entry for group in pool.map(model,MANIFEST['models'].items()) for entry in group]
    with tarfile.open(fetch(MANIFEST['dictionary']),'r:gz') as archive:
        dictionary=ASSETS/'voicevox/dict'
        for member in archive.getmembers():
            if not member.isfile():continue
            relative=PurePosixPath(member.name)
            if relative.is_absolute() or '..' in relative.parts:raise RuntimeError('Unsafe dictionary path')
            copy_bytes(dictionary/str(relative),archive.extractfile(member).read())
    voices=[];models={}
    for filename,character in characters:
        if character['name'] not in CATALOG['characters']:continue
        name,gender=CATALOG['characters'][character['name']]
        for style in character['styles']:
            if style['type'] not in ('talk','streaming_talk'):continue
            models[str(style['id'])]=filename
            voices.append({'ShortName':f"voicevox-{style['id']}",'FriendlyName':f"{name} · {CATALOG['styles'].get(style['name'],style['name'])} ({character['name']} / {style['name']})",'Gender':gender,'Locale':'ja-JP','Offline':True,'Credit':CATALOG['credits'].get(character['name'],f"VOICEVOX:{character['name']}")})
    copy_bytes(ASSETS/'voicevox/catalog.json',json.dumps({'voices':voices,'modelsByStyle':models},ensure_ascii=False).encode())
    version=hashlib.sha256(json.dumps(MANIFEST,sort_keys=True).encode()+json.dumps(CATALOG,sort_keys=True).encode()).hexdigest()
    copy_bytes(ASSETS/'voicevox/install-version.txt',version.encode())
    print(json.dumps({'voices':len(voices),'models':len(models),'assetBytes':sum(p.stat().st_size for p in ASSETS.rglob('*') if p.is_file())}))

if __name__=='__main__':main()

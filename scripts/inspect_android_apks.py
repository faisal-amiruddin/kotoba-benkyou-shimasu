"""Fail CI if Lite accidentally packages VOICEVOX data or native code."""
import json
from pathlib import Path
import zipfile

def main():
    lite = Path('dist-apk/kotoba-lite.apk')
    full = Path('dist-apk/kotoba-full.apk')
    with zipfile.ZipFile(lite) as archive:
        names = archive.namelist()
        forbidden = [name for name in names if name.startswith(('lib/', 'assets/voicevox/'))
                     or name.endswith('.vvm') or 'open_jtalk' in name]
        if forbidden:
            raise ValueError('Offline data leaked into Lite: ' + repr(forbidden))
        for name in names:
            if name.endswith('.dex') and b'Lid/kotoba/studio/NativeSpeech;' in archive.read(name):
                raise ValueError('VOICEVOX JNI class leaked into Lite')
        if not all('assets/web/' + name in names for name in ('index.html', 'app.js', 'platform.js', 'theme.js')):
            raise ValueError('Lite is missing its local UI')
    with zipfile.ZipFile(full) as archive:
        names = set(archive.namelist())
        models = json.loads(Path('shared/voicevox-assets.json').read_text())['models']
        required = {'assets/voicevox/models/' + name for name in models if name.endswith('.vvm')}
        required |= {'assets/voicevox/dict/open_jtalk_dic_utf_8-1.11/sys.dic',
                     'lib/arm64-v8a/libvoicevox_core.so', 'lib/arm64-v8a/libvoicevox_onnxruntime.so'}
        if not required.issubset(names):
            raise ValueError('Full is missing offline assets: ' + repr(required - names))
    sizes = {'liteBytes': lite.stat().st_size, 'fullBytes': full.stat().st_size}
    if sizes['liteBytes'] >= 20 * 1024 * 1024 or sizes['liteBytes'] >= sizes['fullBytes'] * 0.1:
        raise ValueError('Lite is unexpectedly large: ' + repr(sizes))
    Path('android/apk-sizes.json').write_text(json.dumps(sizes, indent=2) + '\n')
    print(json.dumps(sizes))

if __name__ == '__main__':
    main()

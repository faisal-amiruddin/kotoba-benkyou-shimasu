"""Local VOICEVOX CORE worker. JSON lines on stdin/stdout; no network access."""
import base64
import json
import math
from pathlib import Path
import sys
from pronunciation import natural_pronunciation

from voicevox_core.blocking import Onnxruntime, OpenJtalk, Synthesizer, VoiceModelFile

sys.stdin.reconfigure(encoding='utf-8')
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')
ROOT = Path(__file__).resolve().parent.parent / '.runtime' / 'voicevox'
CHARACTERS = {
    '四国めたん': ('Shikoku Metan', 'Female'),
    'ずんだもん': ('Zundamon', 'Female'),
    '春日部つむぎ': ('Kasukabe Tsumugi', 'Female'),
    '雨晴はう': ('Amehare Hau', 'Female'),
    '玄野武宏': ('Kurono Takehiro', 'Male'),
    '剣崎雌雄': ('Kenzaki Mesuo', 'Male'),
    '青山龍星': ('Ryusei Aoyama', 'Male'),
    '黒沢冴白': ('Kurosawa Kohaku', 'Male'),
    'もち子さん': ('Mochiko-san', 'Female'),
    '後鬼': ('Goki', 'Female'),
    'No.7': ('No.7', 'Female'),
    '中部つるぎ': ('Chubu Tsurugi', 'Female'),
}
STYLES = {
    'ノーマル': 'Normal', 'あまあま': 'Manis', 'ツンツン': 'Ketus', 'セクシー': 'Sensual',
    '熱血': 'Bersemangat', '不機嫌': 'Kesal', '喜び': 'Gembira', 'しっとり': 'Lembut',
    'かなしみ': 'Sedih', '囁き': 'Berbisik', 'セクシー／あん子': 'Sensual / Anko',
    '泣き': 'Menangis', '怒り': 'Marah', 'のんびり': 'Santai',
    '人間ver.': 'Wujud manusia', 'ぬいぐるみver.': 'Wujud boneka',
    '人間（怒り）ver.': 'Wujud manusia · marah', '鬼ver.': 'Wujud oni',
    'アナウンス': 'Pengumuman', '読み聞かせ': 'Bercerita', 'ヒソヒソ': 'Bisik-bisik',
    'おどおど': 'Gugup', '絶望と敗北': 'Putus asa / kalah',
    'ヘロヘロ': 'Lemas', 'なみだめ': 'Berkaca-kaca',
}
CREDITS = {'もち子さん': 'VOICEVOX:もち子さん(cv明日葉よもぎ)'}
voices = []
models_by_style = {}
synthesizer = None
loaded = {}  # Insertion order is the LRU order; retain enough for two speakers plus a preview.
MAX_LOADED_MODELS = 3
initialization_error = None
try:
    manifest = json.loads((ROOT / 'install.json').read_text(encoding='utf-8-sig'))
    for filename in manifest['models']:
        path = ROOT / 'models' / 'vvms' / filename
        with VoiceModelFile.open(path) as model:
            for character in model.metas:
                if character.name not in CHARACTERS:
                    continue
                name, gender = CHARACTERS[character.name]
                for style in character.styles:
                    if style.type not in ('talk', 'streaming_talk'):
                        continue
                    models_by_style[style.id] = path
                    voices.append({
                        'ShortName': f'voicevox-{style.id}',
                        'FriendlyName': f'{name} · {STYLES.get(style.name, style.name)} ({character.name} / {style.name})',
                        'Gender': gender, 'Locale': 'ja-JP', 'Offline': True,
                        'Credit': CREDITS.get(character.name, f'VOICEVOX:{character.name}'),
                    })
    if not voices:
        raise RuntimeError('No installed VOICEVOX talk styles found.')
except Exception as error:
    initialization_error = str(error)

def synthesize(request):
    global synthesizer
    voice = str(request.get('voice', ''))
    text = request.get('text', '')
    if not voice.startswith('voicevox-') or not voice[9:].isdigit():
        raise ValueError('Invalid VOICEVOX voice.')
    style_id = int(voice[9:])
    if style_id not in models_by_style:
        raise ValueError('VOICEVOX style is not installed.')
    speed = request.get('speed', 1)
    intonation = request.get('intonation', 1)
    pronunciation = request.get('pronunciation', 'natural')
    if pronunciation not in ('natural', 'original'):
        raise ValueError('Invalid pronunciation mode.')
    if not isinstance(text, str) or not text.strip() or len(text) > 4000:
        raise ValueError('Invalid speech text.')
    for value, low, high in [(speed, 0.7, 2), (intonation, 0, 2)]:
        if type(value) not in (float, int) or not math.isfinite(value) or not low <= value <= high:
            raise ValueError('Invalid tempo or intonation.')
    if synthesizer is None:
        runtime = ROOT / 'onnxruntime' / 'lib' / Onnxruntime.LIB_RECOMMENDED_VERSIONED_FILENAME
        synthesizer = Synthesizer(
            Onnxruntime.load_once(filename=str(runtime)),
            OpenJtalk(ROOT / 'dict' / 'open_jtalk_dic_utf_8-1.11'),
            acceleration_mode='CPU', cpu_num_threads=4,
        )
    path = models_by_style[style_id]
    if path not in loaded:
        if len(loaded) >= MAX_LOADED_MODELS:
            oldest = next(iter(loaded))
            synthesizer.unload_voice_model(loaded[oldest])
            del loaded[oldest]
        with VoiceModelFile.open(path) as model:
            synthesizer.load_voice_model(model)
            loaded[path] = model.id
    else:
        loaded[path] = loaded.pop(path)
    query = synthesizer.create_audio_query(text, style_id)
    if pronunciation == 'natural':
        natural_pronunciation(query)
    query.speed_scale = speed
    query.intonation_scale = intonation
    query.output_sampling_rate = 24000
    query.output_stereo = False
    wav = synthesizer.synthesis(query, style_id, enable_interrogative_upspeak=pronunciation == 'original')
    return {'audio': base64.b64encode(wav).decode('ascii')}

for line in sys.stdin:
    request = {}
    try:
        request = json.loads(line)
        if initialization_error:
            raise RuntimeError(initialization_error)
        if request.get('op') == 'voices':
            response = {'voices': voices}
        elif request.get('op') == 'synthesize':
            response = synthesize(request)
        else:
            raise ValueError('Unknown VOICEVOX operation.')
        response['id'] = request.get('id')
    except Exception as error:
        response = {'id': request.get('id'), 'error': str(error)}
    print(json.dumps(response, ensure_ascii=False), flush=True)

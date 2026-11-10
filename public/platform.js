// Android serves the same UI from packaged HTTPS assets and runs synthesis natively.
(() => {
  if (!window.KotobaAndroid) return;
  let sequence = 0;
  const pending = new Map();
  window.__kotobaReply = async (id, status, type, body) => {
    const item = pending.get(id);
    if (!item) return;
    pending.delete(id);
    clearTimeout(item.timer);
    if (type === 'application/x-kotoba-audio') {
      const {path, mime = 'audio/wav'} = JSON.parse(body);
      try {
        const response = await fetch(path);
        if (!response.ok) throw new Error('File audio lokal tidak dapat dibuka.');
        item.resolve(new Response(await response.blob(), {status, headers:{'Content-Type':mime}}));
      } catch(error) { item.reject(error); }
      finally { window.KotobaAndroid.releaseAudio(path); }
      return;
    }
    let data = body;
    if (type === 'audio/wav') {
      const decoded = atob(body);
      data = Uint8Array.from(decoded, char => char.charCodeAt(0));
    }
    item.resolve(new Response(data, {status, headers:{'Content-Type':type}}));
  };
  window.KotobaMobile = {
    request(url, options = {}) {
      return new Promise((resolve, reject) => {
        const id = ++sequence;
        const timer = setTimeout(() => {pending.delete(id); reject(new Error('Proses audio di HP melewati batas waktu. Pendekkan kalimat.'));}, 240000);
        pending.set(id, {resolve, reject, timer});
        try { window.KotobaAndroid.request(id, url, options.body || '{}'); }
        catch(error) { clearTimeout(timer); pending.delete(id); reject(error); }
      });
    }
  };
  async function playerCommand(op, data = {}) {
    const response = await window.KotobaMobile.request('/api/player', {body:JSON.stringify({op,...data})});
    const result = await response.json();
    if (!response.ok || result.error) throw new Error(result.error || 'Pemutar audio tidak tersedia.');
    return result;
  }
  async function upload(blob, name) {
    const handle = window.KotobaAndroid.beginSave(name, blob.type || 'application/octet-stream');
    if (!handle) throw new Error('Tidak dapat menyiapkan file audio.');
    try {
      for (let offset = 0; offset < blob.size; offset += 49152) {
        const bytes = new Uint8Array(await blob.slice(offset, offset + 49152).arrayBuffer());
        if (!window.KotobaAndroid.appendSave(handle, btoa(String.fromCharCode(...bytes)))) throw new Error('Gagal menyimpan audio. Periksa ruang penyimpanan HP.');
      }
      return handle;
    } catch(error) {window.KotobaAndroid.cancelSave(handle);throw error;}
  }
  class NativePlayer extends EventTarget {
    constructor(element) {
      super();
      this.state = {loaded:false,position:0,duration:0,paused:true,ended:false,rate:1};
      this.snapshotTime = performance.now();
      this.defaultPlaybackRate = 1;
      element.hidden = true;
      const controls = document.createElement('div');
      controls.innerHTML = '<div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap"><button type="button" class="btn btn-soft" id="nativeToggle" aria-label="Putar audio" disabled>Putar</button><input id="nativeSeek" type="range" min="0" max="0" value="0" step="0.1" aria-label="Posisi audio" style="flex:1;min-width:120px"><span id="nativeTime" style="font-size:12px;font-variant-numeric:tabular-nums">0:00 / 0:00</span></div><p class="hint">Audio tetap berjalan saat membuka aplikasi lain atau mengunci layar. Kontrol tersedia di notifikasi.</p>';
      element.after(controls);
      this.toggle = controls.querySelector('#nativeToggle');
      this.seek = controls.querySelector('#nativeSeek');
      this.time = controls.querySelector('#nativeTime');
      this.toggle.addEventListener('click', () => (this.paused ? this.play() : this.command('pause')).catch(error => this.error(error)));
      this.seek.addEventListener('change', () => {this.currentTime = Number(this.seek.value);});
      document.addEventListener('visibilitychange', () => {if (!document.hidden) this.refresh();});
      setInterval(() => {if (!document.hidden && this.state.loaded) this.refresh();}, 400);
    }
    error(error) { document.getElementById('statusText').textContent = error.message; }
    apply(state) {
      const before = this.state;
      this.state = {...before,...state};
      this.snapshotTime = performance.now();
      const format = seconds => `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2,'0')}`;
      this.toggle.disabled = !this.state.loaded;
      this.toggle.textContent = this.paused ? 'Putar' : 'Jeda';
      this.toggle.setAttribute('aria-label', this.paused ? 'Putar audio' : 'Jeda audio');
      this.seek.max = this.duration;this.seek.value = this.currentTime;
      this.time.textContent = `${format(this.currentTime)} / ${format(this.duration)}`;
      if (!before.duration && this.duration) this.dispatchEvent(new Event('loadedmetadata'));
      if (before.paused !== this.paused) this.dispatchEvent(new Event(this.paused ? 'pause' : 'play'));
      if (!before.ended && this.ended) this.dispatchEvent(new Event('ended'));
      this.dispatchEvent(new Event('timeupdate'));
    }
    async command(op, data) {const state = await playerCommand(op,data);this.apply(state);return state;}
    async refresh() {try {await this.command('state');} catch(error) {this.error(error);}}
    async setSource(blob, timeline, credits) {
      const handle = await upload(blob,'playback.wav');
      try {await this.command('load',{handle,timeline,credits});}
      catch(error) {window.KotobaAndroid.cancelSave(handle);throw error;}
      for (let i = 0; i < 40 && !this.duration; i++) {await new Promise(resolve => setTimeout(resolve,250));await this.command('state');}
      if (!this.duration) throw new Error('Pemutar belum dapat membaca audio WAV.');
      await this.command('rate',{rate:this.defaultPlaybackRate});
    }
    async restore() {return this.command('session');}
    get currentTime() {
      const elapsed = this.state.playing && !this.state.ended ? (performance.now() - this.snapshotTime) / 1000 * this.state.rate : 0;
      return Math.min(this.duration || Infinity, this.state.position + elapsed);
    }
    set currentTime(seconds) {if (this.state.loaded) this.command('seek',{seconds}).catch(error => this.error(error));}
    get duration() {return this.state.duration;}
    get paused() {return this.state.paused;}
    get ended() {return this.state.ended;}
    get playbackRate() {return this.state.rate;}
    set playbackRate(rate) {this.defaultPlaybackRate = rate;if (this.state.loaded) this.command('rate',{rate}).catch(error => this.error(error));}
    play() {return this.command('play');}
    pause() {if (this.state.loaded) this.command('pause').catch(error => this.error(error));}
  }
  window.KotobaMobile.createPlayer = element => (window.KotobaMobile.player = new NativePlayer(element));
  document.addEventListener('click', async event => {
    const link = event.target.closest('a[download]');
    if (!link || !(link.href.startsWith('blob:') || link.href.startsWith(location.origin + '/playback/'))) return;
    event.preventDefault();
    let handle;
    try {
      const blob = await (await fetch(link.href)).blob();
      handle = await upload(blob, link.download);
      window.KotobaAndroid.finishSave(handle);
    } catch(error) {
      if (handle) window.KotobaAndroid.cancelSave(handle);
      document.getElementById('statusText').textContent = error.message;
    }
  });
})();

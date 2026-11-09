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
      const path = JSON.parse(body).path;
      try {
        const response = await fetch(path);
        if (!response.ok) throw new Error('File audio lokal tidak dapat dibuka.');
        item.resolve(new Response(await response.blob(), {status, headers:{'Content-Type':'audio/wav'}}));
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
  document.addEventListener('click', async event => {
    const link = event.target.closest('a[download]');
    if (!link || !link.href.startsWith('blob:')) return;
    event.preventDefault();
    let handle;
    try {
      const blob = await (await fetch(link.href)).blob();
      handle = window.KotobaAndroid.beginSave(link.download, blob.type || 'application/octet-stream');
      if (!handle) throw new Error('Tidak dapat menyiapkan file unduhan.');
      for (let offset = 0; offset < blob.size; offset += 49152) {
        const bytes = new Uint8Array(await blob.slice(offset, offset + 49152).arrayBuffer());
        const chunk = btoa(String.fromCharCode(...bytes));
        if (!window.KotobaAndroid.appendSave(handle, chunk)) throw new Error('Gagal menyimpan audio. Periksa ruang penyimpanan HP.');
      }
      window.KotobaAndroid.finishSave(handle);
    } catch(error) {
      if (handle) window.KotobaAndroid.cancelSave(handle);
      document.getElementById('statusText').textContent = error.message;
    }
  });
})();

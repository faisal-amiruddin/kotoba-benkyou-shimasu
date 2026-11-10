// Apply before first paint, then keep web and Android system bars in sync.
(() => {
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  const choices = ['auto', 'light', 'dark'];
  let mode = 'auto';
  try { mode = localStorage.getItem('kotoba-theme') || window.KotobaAndroid?.getThemeMode?.() || 'auto'; } catch {}
  if (!choices.includes(mode)) mode = 'auto';
  function apply() {
    const systemDark = window.KotobaAndroid?.isSystemDark ? window.KotobaAndroid.isSystemDark() : system.matches;
    const dark = mode === 'dark' || (mode === 'auto' && systemDark);
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#111814' : '#f6f4ef');
    try { window.KotobaAndroid?.setThemeMode?.(mode); } catch {}
    const control = document.getElementById('themeMode');
    if (control) control.value = mode;
  }
  apply();
  if (system.addEventListener) system.addEventListener('change', apply);
  else system.addListener(apply);
  window.addEventListener('storage', event => {
    if (event.key !== 'kotoba-theme') return;
    mode = choices.includes(event.newValue) ? event.newValue : 'auto';apply();
  });
  document.addEventListener('DOMContentLoaded', () => {
    const control = document.getElementById('themeMode');
    if (!control) return;
    control.value = mode;
    control.addEventListener('change', () => {
      mode = choices.includes(control.value) ? control.value : 'auto';
      try { localStorage.setItem('kotoba-theme', mode); } catch {}
      apply();
    });
  });
})();

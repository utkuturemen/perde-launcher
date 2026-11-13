package com.utku.perde

/** The control panel served at http://<projector-ip>:8080. */
object PanelPage {
    val HTML = """
<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Perde paneli</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; font-family: -apple-system, system-ui, sans-serif; background: #0B0F1A; color: #fff; }
  main { max-width: 640px; margin: 0 auto; padding: 24px 18px 48px; }
  h1 { font-size: 26px; margin: 0 0 4px; }
  .sub { color: #A9B1C6; margin: 0 0 24px; }
  section { background: #151C2E; border-radius: 16px; padding: 18px; margin-bottom: 16px; }
  h2 { font-size: 17px; margin: 0 0 12px; }
  p.hint { color: #A9B1C6; font-size: 14px; margin: 0 0 12px; }
  button, .file { background: #7C9CFF; color: #0B0F1A; border: 0; border-radius: 10px;
    padding: 12px 16px; font-size: 15px; font-weight: 600; cursor: pointer; }
  button.ghost { background: #26304A; color: #fff; }
  input[type=file] { display: none; }
  .row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
  progress { width: 100%; height: 10px; margin-top: 12px; display: none; }
  #status { margin-top: 10px; font-size: 14px; color: #C9D4FF; min-height: 1em; }
  input[type=search] { width: 100%; box-sizing: border-box; padding: 11px 12px; border-radius: 10px;
    border: 1px solid #2A3550; background: #0F1524; color: #fff; font-size: 15px; margin-bottom: 10px; }
  ul { list-style: none; margin: 0; padding: 0; max-height: 420px; overflow-y: auto; }
  li { display: flex; justify-content: space-between; align-items: center; padding: 10px 2px;
    border-bottom: 1px solid #222B40; gap: 10px; }
  li span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  li button { padding: 8px 14px; font-size: 14px; }
  a { color: #7C9CFF; }
</style>
</head>
<body>
<main>
  <h1>Perde</h1>
  <p class="sub">Projektörü bu sayfadan yönet. Projektörle aynı Wi‑Fi'de olman yeterli.</p>

  <section>
    <h2>Uygulama yükle</h2>
    <p class="hint">Bir .apk dosyası seç. Projektöre gönderilir ve kurulum ekranı projektörde açılır.</p>
    <div class="row">
      <label class="file" for="apk">APK seç ve gönder</label>
      <input id="apk" type="file" accept=".apk,application/vnd.android.package-archive">
    </div>
    <progress id="bar" max="100" value="0"></progress>
    <div id="status"></div>
  </section>

  <section>
    <h2>Uygulamalar</h2>
    <input id="q" type="search" placeholder="Ara…">
    <ul id="apps"><li><span>Yükleniyor…</span></li></ul>
  </section>

  <section>
    <h2>Cihaz raporu</h2>
    <p class="hint">Donanım, sistem ayarları ve kumanda tuşları. Kaydedip Claude'a gönderebilirsin.</p>
    <a href="/report" target="_blank">Raporu aç</a>
  </section>
</main>
<script>
const status = document.getElementById('status');
const bar = document.getElementById('bar');

document.getElementById('apk').addEventListener('change', e => {
  const f = e.target.files[0];
  if (!f) return;
  const xhr = new XMLHttpRequest();
  xhr.open('POST', '/upload?name=' + encodeURIComponent(f.name));
  bar.style.display = 'block';
  bar.value = 0;
  status.textContent = f.name + ' gönderiliyor…';
  xhr.upload.onprogress = ev => { if (ev.lengthComputable) bar.value = ev.loaded / ev.total * 100; };
  xhr.onload = () => { status.textContent = xhr.responseText; bar.style.display = 'none'; e.target.value = ''; };
  xhr.onerror = () => { status.textContent = 'Gönderilemedi. Projektör açık ve aynı ağda mı?'; bar.style.display = 'none'; };
  xhr.send(f);
});

let apps = [];
function render() {
  const q = document.getElementById('q').value.toLowerCase();
  const ul = document.getElementById('apps');
  ul.innerHTML = '';
  apps.filter(a => a.label.toLowerCase().includes(q) || a.pkg.includes(q)).forEach(a => {
    const li = document.createElement('li');
    const name = document.createElement('span');
    name.textContent = a.label;
    name.title = a.pkg;
    const btn = document.createElement('button');
    btn.className = 'ghost';
    btn.textContent = 'Aç';
    btn.onclick = async () => {
      const r = await fetch('/launch?pkg=' + encodeURIComponent(a.pkg), { method: 'POST' });
      btn.textContent = await r.text();
      setTimeout(() => btn.textContent = 'Aç', 2000);
    };
    li.append(name, btn);
    ul.append(li);
  });
}
document.getElementById('q').addEventListener('input', render);
fetch('/apps').then(r => r.json()).then(list => { apps = list; render(); })
  .catch(() => { document.getElementById('apps').innerHTML = '<li><span>Liste alınamadı</span></li>'; });
</script>
</body>
</html>
""".trimIndent()
}

@echo off
cd /d "%~dp0"
echo ==============================================
echo  KOTOBA STUDIO - Microsoft Edge Read Aloud
echo ==============================================
where node >nul 2>nul
if errorlevel 1 (
  echo Node.js belum terpasang. Install Node.js versi LTS dari https://nodejs.org lalu jalankan file ini lagi.
  pause
  exit /b 1
)
if not exist node_modules\msedge-tts (
  echo Menginstall dependency msedge-tts@2.1.1...
  call npm install
  if errorlevel 1 (
    echo Instalasi gagal. Periksa koneksi internet dan coba lagi.
    pause
    exit /b 1
  )
)
start "Kotoba Studio" cmd /c "timeout /t 2 /nobreak >nul & start http://127.0.0.1:3210"
node server.js
pause

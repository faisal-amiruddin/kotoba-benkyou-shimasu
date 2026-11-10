package id.kotoba.studio;

import android.app.Activity;
import android.content.Intent;
import android.content.ComponentName;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {
    private static final String ORIGIN="https://appassets.androidplatform.net";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<String,File> audioFiles=new ConcurrentHashMap<>();
    private final Map<String,Save> saves=new ConcurrentHashMap<>();
    private WebView web;
    private SpeechEngine engine;
    private final MicrosoftSpeech microsoft=new MicrosoftSpeech();
    private FrameLayout root;
    private TextView progress;
    private boolean darkAppearance;
    private Save choosingSave;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private static final class Save {
        File file; FileOutputStream stream; String name,mime; long bytes;
        Save(File file,String name,String mime) throws Exception {this.file=file;this.name=name;this.mime=mime;stream=new FileOutputStream(file);}
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        controllerFuture=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        controllerFuture.addListener(()->{try{controller=controllerFuture.get();}catch(Exception ignored){}},this::runOnUiThread);
        root=new FrameLayout(this); root.setBackgroundColor(Color.rgb(247,245,240));
        root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);
        progress=new TextView(this);progress.setGravity(Gravity.CENTER);progress.setTextSize(17);progress.setPadding(28,28,28,28);
        progress.setText("Menyiapkan "+getApplicationInfo().loadLabel(getPackageManager())+"…");
        root.addView(progress,new FrameLayout.LayoutParams(-1,-1));
        applyAppearance(getSharedPreferences("appearance",MODE_PRIVATE).getString("mode","auto"));
        worker.execute(()->{
            try {
                engine=SpeechEngine.prepare(this,message->runOnUiThread(()->progress.setText(message)));
                runOnUiThread(this::openWeb);
            } catch(Throwable error) { runOnUiThread(()->progress.setText("Penyiapan belum berhasil.\n"+error.getMessage()+"\nTutup dan buka kembali aplikasi untuk mencoba lagi.")); }
        });
    }
    private boolean systemDark() {
        return (getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
    private void applyAppearance(String mode) {
        darkAppearance="dark".equals(mode)||("auto".equals(mode)&&systemDark());
        int background=Color.parseColor(darkAppearance?"#111814":"#f6f4ef");
        getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
        View decor=getWindow().getDecorView();
        int lightFlags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(darkAppearance?decor.getSystemUiVisibility()&~lightFlags:decor.getSystemUiVisibility()|lightFlags);
        if(android.os.Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null) {
            int appearance=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(darkAppearance?0:appearance,appearance);
        }
        if(root!=null)root.setBackgroundColor(background);
        if(web!=null)web.setBackgroundColor(background);
        if(progress!=null)progress.setTextColor(Color.parseColor(darkAppearance?"#e8eee9":"#20251f"));
    }
    @SuppressWarnings("SetJavaScriptEnabled") private void openWeb() {
        if(isDestroyed())return;
        web=new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(Color.parseColor(darkAppearance?"#111814":"#f6f4ef"));
        if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.getSettings(),false);
        WebViewAssetLoader loader=new WebViewAssetLoader.Builder()
            .addPathHandler("/playback/",path->{
                if(!path.matches("[a-f0-9-]+\\.wav"))return null;
                try {return new WebResourceResponse("audio/wav",null,new FileInputStream(new File(PlaybackStore.directory(this),path)));}catch(IOException error){return null;}
            })
            .addPathHandler("/generated/",path->{
                File file=audioFiles.get("/generated/"+path);
                try {return file==null?null:new WebResourceResponse(file.getName().endsWith(".mp3")?"audio/mpeg":"audio/wav",null,new FileInputStream(file));}catch(IOException error){return null;}
            })
            .addPathHandler("/",path->{
                String file=path.isEmpty()?"index.html":path;
                if(!Arrays.asList("index.html","app.js","platform.js","theme.js").contains(file)) return null;
                try {return new WebResourceResponse(file.endsWith(".html")?"text/html":"text/javascript","UTF-8",getAssets().open("web/"+file));}catch(IOException error){return null;}
            }).build();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return loader.shouldInterceptRequest(request.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                Uri uri=request.getUrl();
                if("https".equals(uri.getScheme())&&"appassets.androidplatform.net".equals(uri.getHost())) return false;
                if(request.hasGesture()&&("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))) {
                    try {startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(),"KotobaAndroid");
        root.removeAllViews();root.addView(web,new FrameLayout.LayoutParams(-1,-1));
        web.loadUrl(ORIGIN+"/index.html");
    }
    private void reply(int id,int status,String type,String body) {
        runOnUiThread(()->{
            if(web!=null&&!isDestroyed()) web.evaluateJavascript("window.__kotobaReply("+id+","+status+","+JSONObject.quote(type)+","+JSONObject.quote(body)+")",null);
        });
    }
    private JSONObject playerState() throws Exception {
        if(controller==null)throw new IllegalStateException("Pemutar audio sedang disiapkan. Coba sebentar lagi.");
        return new JSONObject().put("loaded",controller.getMediaItemCount()>0)
            .put("position",controller.getCurrentPosition()/1000.0).put("duration",Math.max(0,controller.getDuration())/1000.0)
            .put("paused",!controller.getPlayWhenReady()).put("playing",controller.isPlaying())
            .put("ended",controller.getPlaybackState()==Player.STATE_ENDED).put("rate",controller.getPlaybackParameters().speed)
            .put("error",controller.getPlayerError()==null?JSONObject.NULL:controller.getPlayerError().getMessage());
    }
    private void playerRequest(int id,JSONObject request,JSONObject saved) {
        runOnUiThread(()->{
            try {
                if(controller==null)throw new IllegalStateException("Pemutar audio sedang disiapkan. Coba sebentar lagi.");
                String op=request.optString("op","state");
                switch(op) {
                    case "load":
                        controller.pause();controller.stop();
                        controller.setMediaItem(PlaybackStore.item(PlaybackStore.audio(this,saved)));controller.prepare();
                        for(File file:PlaybackStore.directory(this).listFiles())if(file.getName().endsWith(".wav")&&!file.getName().equals(saved.getString("file")))file.delete();
                        break;
                    case "play": if(controller.getPlaybackState()==Player.STATE_ENDED)controller.seekTo(0);controller.play();break;
                    case "pause": controller.pause();break;
                    case "seek": double seconds=request.getDouble("seconds");if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Posisi audio tidak valid.");controller.seekTo((long)(seconds*1000));break;
                    case "rate": double rate=request.getDouble("rate");if(!Double.isFinite(rate)||rate<0.5||rate>2)throw new IllegalArgumentException("Tempo putar tidak valid.");controller.setPlaybackSpeed((float)rate);break;
                    case "state": case "session": break;
                    default: throw new IllegalArgumentException("Kontrol audio tidak dikenal.");
                }
                JSONObject response=playerState();
                if(op.equals("session")&&saved!=null&&PlaybackStore.audio(this,saved)!=null) {
                    response.put("timeline",saved.optJSONArray("timeline"));response.put("url","/playback/"+saved.getString("file"));
                    response.put("credits",saved.optString("credits",""));
                }
                reply(id,200,"application/json",response.toString());
            }catch(Exception error){reply(id,502,"application/json",newError(error));}
        });
    }
    private String newError(Throwable error) {return "{\"error\":"+JSONObject.quote(error.getMessage()==null?"Gagal memproses audio lokal.":error.getMessage())+"}";}
    private final class Bridge {
        @JavascriptInterface public String getThemeMode() {return getSharedPreferences("appearance",MODE_PRIVATE).getString("mode","auto");}
        @JavascriptInterface public boolean isSystemDark() {return systemDark();}
        @JavascriptInterface public void setThemeMode(String mode) {
            if(!Arrays.asList("auto","light","dark").contains(mode))return;
            getSharedPreferences("appearance",MODE_PRIVATE).edit().putString("mode",mode).apply();
            runOnUiThread(()->applyAppearance(mode));
        }
        @JavascriptInterface public void request(int id,String path,String body) {
            if(path==null||body==null||body.length()>1024*1024) {reply(id,400,"application/json","{\"error\":\"Permintaan tidak valid.\"}");return;}
            worker.execute(()->{
                try {
                    if(path.equals("/api/player")) {
                        JSONObject request=new JSONObject(body),saved=null;
                        String op=request.optString("op","state");
                        if(op.equals("load")) {
                            Save upload=saves.remove(request.getString("handle"));
                            if(upload==null)throw new IOException("File audio belum disiapkan.");
                            try {
                                upload.stream.close();
                                try(RandomAccessFile input=new RandomAccessFile(upload.file,"r")) {
                                    if(input.length()<44||input.readInt()!=0x52494646)throw new IOException("Audio WAV tidak valid.");
                                    input.seek(8);if(input.readInt()!=0x57415645)throw new IOException("Audio WAV tidak valid.");
                                }
                                File directory=PlaybackStore.directory(MainActivity.this);
                                if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Tidak dapat menyimpan audio.");
                                String filename=UUID.randomUUID()+".wav";
                                java.nio.file.Files.move(upload.file.toPath(),new File(directory,filename).toPath());
                                saved=new JSONObject().put("file",filename).put("timeline",request.getJSONArray("timeline")).put("credits",request.optString("credits","")).put("positionMs",0);
                                PlaybackStore.write(MainActivity.this,saved);
                            }finally{discard(upload);}
                        } else if(op.equals("session"))saved=PlaybackStore.read(MainActivity.this);
                        playerRequest(id,request,saved);
                    } else if(path.equals("/api/engines")) {
                        reply(id,200,"application/json",new JSONObject().put("defaultEngine",engine.available()?"voicevox":"online").put("voicevoxAvailable",engine.available()).put("offlineAvailable",false).put("onlineAvailable",true).put("edition",engine.available()?"full":"lite").toString());
                    } else if(path.equals("/api/voices?engine=voicevox")) {
                        reply(id,200,"application/json",new JSONObject().put("voices",engine.voices()).toString());
                    } else if(path.equals("/api/voices?engine=online")) {
                        JSONArray voices=new JSONArray();
                        voices.put(new JSONObject().put("ShortName","ja-JP-KeitaNeural").put("FriendlyName","Keita · Microsoft online").put("Gender","Male").put("Locale","ja-JP"));
                        voices.put(new JSONObject().put("ShortName","ja-JP-NanamiNeural").put("FriendlyName","Nanami · Microsoft online").put("Gender","Female").put("Locale","ja-JP"));
                        reply(id,200,"application/json",new JSONObject().put("voices",voices).toString());
                    } else if(path.equals("/api/synthesize")) {
                        runOnUiThread(()->getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
                        try {
                            JSONObject request=new JSONObject(body);
                            String provider=request.optString("engine","voicevox");
                            byte[] audio;String extension,mime;
                            if(provider.equals("online")) {
                                Object rate=request.opt("speed");
                                if(rate!=null&&!(rate instanceof Number))throw new IllegalArgumentException("Tempo harus berupa angka.");
                                audio=microsoft.synthesize(request.optString("text",""),request.optString("voice",""),rate==null?1:((Number)rate).doubleValue());
                                extension=".mp3";mime="audio/mpeg";
                            } else {
                                audio=engine.synthesize(MainActivity.this,request);extension=".wav";mime="audio/wav";
                            }
                            String key="/generated/"+UUID.randomUUID()+extension;
                            File file=File.createTempFile("kotoba-audio-",extension,getCacheDir());
                            try(FileOutputStream out=new FileOutputStream(file)){out.write(audio);}
                            audioFiles.put(key,file);
                            reply(id,200,"application/x-kotoba-audio",new JSONObject().put("path",key).put("mime",mime).toString());
                        } finally {runOnUiThread(()->getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));}
                    } else reply(id,404,"application/json","{\"error\":\"Fitur ini tidak tersedia di versi Android lokal.\"}");
                } catch(Throwable error) {reply(id,502,"application/json","{\"error\":"+JSONObject.quote(error.getMessage()==null?"Gagal membuat audio lokal.":error.getMessage())+"}");}
            });
        }
        @JavascriptInterface public void releaseAudio(String path) {File file=audioFiles.remove(path);if(file!=null)file.delete();}
        @JavascriptInterface public String beginSave(String filename,String mime) {
            try {
                if(saves.size()>=2)return "";
                String name=filename.replaceAll("[^a-zA-Z0-9._-]","_");
                if(name.isEmpty()||name.length()>160) return "";
                String id=UUID.randomUUID().toString();
                saves.put(id,new Save(File.createTempFile("kotoba-save-",".tmp",getCacheDir()),name,mime.split(";",2)[0]));
                return id;
            }catch(Exception error){return "";}
        }
        @JavascriptInterface public boolean appendSave(String id,String chunk) {
            Save save=saves.get(id);
            if(save==null||chunk.length()>65536)return false;
            try {byte[] bytes=android.util.Base64.decode(chunk,android.util.Base64.DEFAULT);if(save.bytes+bytes.length>400L*1024*1024)return false;save.stream.write(bytes);save.bytes+=bytes.length;return true;}catch(Exception error){return false;}
        }
        @JavascriptInterface public void cancelSave(String id) {Save save=saves.remove(id);if(save!=null)discard(save);}
        @JavascriptInterface public void finishSave(String id) {
            Save save=saves.remove(id);if(save==null)return;
            try {save.stream.close();}catch(IOException error){discard(save);return;}
            runOnUiThread(()->{
                if(choosingSave!=null||isDestroyed()){discard(save);return;}
                choosingSave=save;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(save.mime);intent.putExtra(Intent.EXTRA_TITLE,save.name);
                try {startActivityForResult(intent,110);}catch(Exception error){discard(save);choosingSave=null;Toast.makeText(MainActivity.this,"Pemilih folder tidak tersedia.",Toast.LENGTH_LONG).show();}
            });
        }
    }
    private void discard(Save save){try{save.stream.close();}catch(Exception ignored){}save.file.delete();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=110||choosingSave==null)return;
        Save save=choosingSave;choosingSave=null;
        if(resultCode!=RESULT_OK||data==null||data.getData()==null){discard(save);return;}
        Uri uri=data.getData();
        worker.execute(()->{
            try(InputStream in=new FileInputStream(save.file);OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null)throw new IOException("Tidak dapat membuka file tujuan.");
                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                runOnUiThread(()->Toast.makeText(this,"File tersimpan.",Toast.LENGTH_SHORT).show());
            }catch(Exception error){runOnUiThread(()->Toast.makeText(this,"Gagal menyimpan file: "+error.getMessage(),Toast.LENGTH_LONG).show());}
            finally {discard(save);}
        });
    }
    @Override protected void onDestroy() {
        if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);
        if(web!=null){web.removeJavascriptInterface("KotobaAndroid");web.destroy();web=null;}
        worker.execute(()->microsoft.close());
        worker.shutdown();
        for(File file:audioFiles.values())file.delete();
        for(Save save:saves.values())discard(save);
        super.onDestroy();
    }
}

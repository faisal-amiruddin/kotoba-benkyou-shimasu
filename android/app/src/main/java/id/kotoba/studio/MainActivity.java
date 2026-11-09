package id.kotoba.studio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import androidx.webkit.WebViewAssetLoader;
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
    private FrameLayout root;
    private TextView progress;
    private Save choosingSave;
    private static final class Save {
        File file; FileOutputStream stream; String name,mime; long bytes;
        Save(File file,String name,String mime) throws Exception {this.file=file;this.name=name;this.mime=mime;stream=new FileOutputStream(file);}
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root=new FrameLayout(this); root.setBackgroundColor(Color.rgb(247,245,240));
        root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);
        progress=new TextView(this);progress.setGravity(Gravity.CENTER);progress.setTextSize(17);progress.setPadding(28,28,28,28);
        progress.setText("Menyiapkan Kotoba lokal…\nPertama kali dibuka, penyiapan model memerlukan waktu.");
        root.addView(progress,new FrameLayout.LayoutParams(-1,-1));
        worker.execute(()->{
            try {
                File models=ModelStorage.prepare(this,message->runOnUiThread(()->progress.setText(message)));
                engine=new SpeechEngine(this,models);
                runOnUiThread(this::openWeb);
            } catch(Throwable error) { runOnUiThread(()->progress.setText("Penyiapan belum berhasil.\n"+error.getMessage()+"\nTutup dan buka kembali aplikasi untuk mencoba lagi.")); }
        });
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
        web.setBackgroundColor(Color.rgb(247,245,240));
        WebViewAssetLoader loader=new WebViewAssetLoader.Builder()
            .addPathHandler("/generated/",path->{
                File file=audioFiles.get("/generated/"+path);
                try {return file==null?null:new WebResourceResponse("audio/wav",null,new FileInputStream(file));}catch(IOException error){return null;}
            })
            .addPathHandler("/",path->{
                String file=path.isEmpty()?"index.html":path;
                if(!Arrays.asList("index.html","app.js","platform.js").contains(file)) return null;
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
    private final class Bridge {
        @JavascriptInterface public void request(int id,String path,String body) {
            if(path==null||body==null||body.length()>1024*1024) {reply(id,400,"application/json","{\"error\":\"Permintaan tidak valid.\"}");return;}
            worker.execute(()->{
                try {
                    if(path.equals("/api/engines")) {
                        reply(id,200,"application/json","{\"defaultEngine\":\"voicevox\",\"voicevoxAvailable\":true,\"offlineAvailable\":false,\"onlineAvailable\":false}");
                    } else if(path.equals("/api/voices?engine=voicevox")) {
                        reply(id,200,"application/json",new JSONObject().put("voices",engine.voices()).toString());
                    } else if(path.equals("/api/synthesize")) {
                        runOnUiThread(()->getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
                        try {
                            byte[] wav=engine.synthesize(MainActivity.this,new JSONObject(body));
                            String key="/generated/"+UUID.randomUUID()+".wav";
                            File file=File.createTempFile("kotoba-audio-",".wav",getCacheDir());
                            try(FileOutputStream out=new FileOutputStream(file)){out.write(wav);}
                            audioFiles.put(key,file);
                            reply(id,200,"application/x-kotoba-audio",new JSONObject().put("path",key).toString());
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
                saves.put(id,new Save(File.createTempFile("kotoba-save-",".tmp",getCacheDir()),name,mime));
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
        if(web!=null){web.removeJavascriptInterface("KotobaAndroid");web.destroy();web=null;}
        worker.shutdown();
        for(File file:audioFiles.values())file.delete();
        for(Save save:saves.values())discard(save);
        super.onDestroy();
    }
}

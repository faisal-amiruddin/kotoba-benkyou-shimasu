package id.kotoba.studio;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.*;

final class SpeechEngine {
    private final File root;
    private final JSONArray voices;
    private final Map<Integer,String> models=new HashMap<>();
    private boolean initialized=false;
    SpeechEngine(Context context,File root) throws Exception {
        this.root=root;
        JSONObject catalog=new JSONObject(ModelStorage.readAsset(context,"voicevox/catalog.json"));
        voices=catalog.getJSONArray("voices");
        JSONObject paths=catalog.getJSONObject("modelsByStyle");
        java.util.Iterator<String> keys=paths.keys();
        while(keys.hasNext()) {String key=keys.next();models.put(Integer.parseInt(key),paths.getString(key));}
    }
    JSONArray voices() { return voices; }
    synchronized byte[] synthesize(Context context,JSONObject request) throws Exception {
        String text=request.optString("text","").trim(),voice=request.optString("voice","");
        if(text.isEmpty()||text.length()>4000||!voice.matches("voicevox-\\d{1,5}")) throw new IllegalArgumentException("Teks atau suara tidak valid.");
        int style=Integer.parseInt(voice.substring(9));
        if(!models.containsKey(style)) throw new IllegalArgumentException("Suara ini belum terpasang.");
        if(!request.optString("engine","voicevox").equals("voicevox")) throw new IllegalArgumentException("Versi Android lokal menggunakan VOICEVOX.");
        double speed=number(request,"speed",1,0.7,2),intonation=number(request,"intonation",1,0,2);
        String pronunciation=request.optString("pronunciation","natural");
        if(!pronunciation.equals("natural")&&!pronunciation.equals("original")) throw new IllegalArgumentException("Mode pelafalan tidak valid.");
        if(!initialized) {
            NativeSpeech.initialize(utf8(new File(root,"dict/open_jtalk_dic_utf_8-1.11").getAbsolutePath()),utf8(new File(context.getApplicationInfo().nativeLibraryDir,"libvoicevox_onnxruntime.so").getAbsolutePath()),4);
            initialized=true;
        }
        byte[] bytes=NativeSpeech.query(utf8(new File(root,"models/"+models.get(style)).getAbsolutePath()),utf8(text),style);
        JSONObject query=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(pronunciation.equals("natural")) Pronunciation.natural(query);
        query.put("speedScale",speed); query.put("intonationScale",intonation);
        query.put("outputSamplingRate",24000); query.put("outputStereo",false);
        return NativeSpeech.synthesize(utf8(query.toString()),style,pronunciation.equals("original"));
    }
    private static double number(JSONObject request,String name,double fallback,double min,double max) throws Exception {
        Object raw=request.has(name)?request.get(name):fallback;
        if(!(raw instanceof Number)) throw new IllegalArgumentException("Nilai "+name+" harus berupa angka.");
        double value=((Number)raw).doubleValue();
        if(!Double.isFinite(value)||value<min||value>max) throw new IllegalArgumentException("Nilai "+name+" di luar batas.");
        return value;
    }
    private static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}

package id.kotoba.studio;

import android.content.Context;
import android.test.InstrumentationTestCase;
import org.json.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class OfflineSpeechTest extends InstrumentationTestCase {
    private WebView findWeb(View view) {
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WebView found=findWeb(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private String evaluate(MainActivity activity,String script) throws Exception {
        AtomicReference<String> value=new AtomicReference<>("");CountDownLatch latch=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->{WebView web=findWeb(activity.getWindow().getDecorView());if(web==null){latch.countDown();return;}web.evaluateJavascript(script,result->{value.set(result);latch.countDown();});});
        assertTrue(latch.await(15,TimeUnit.SECONDS));return value.get();
    }
    public void testWebInterfaceSynthesizesWithoutNetwork() throws Exception {
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(intent);
        try {
            String ready="";
            for(int i=0;i<90;i++){ready=evaluate(activity,"!!document.querySelector('#generateAudio') && !document.querySelector('#generateAudio').disabled && document.querySelector('#maleVoice').value==='voicevox-11'");if(ready.equals("true"))break;Thread.sleep(1000);}
            assertEquals("Local UI should initialize native voices", "true",ready);
            String script="{cowo[1]} いいえ。今日は何をしますか？\n{romaji[1]} Iie. Kyou wa nani o shimasu ka?\n{translate[1]} Tidak. Hari ini mau melakukan apa?\n{cewe[2]} 日本語を勉強しましょう。\n{romaji[2]} Nihongo o benkyou shimashou.\n{translate[2]} Ayo belajar bahasa Jepang.";
            evaluate(activity,"document.querySelector('#dialogInput').value="+JSONObject.quote(script)+";document.querySelector('#dialogInput').dispatchEvent(new Event('input'));document.querySelector('#generateAudio').click();true");
            String status="";
            for(int i=0;i<120;i++){status=evaluate(activity,"document.querySelector('#statusText').textContent");if(status.contains("Sukses!"))break;Thread.sleep(1000);}
            assertTrue("WebView must decode the native WAV and finish: "+status,status.contains("Sukses!"));
            assertEquals("true",evaluate(activity,"document.querySelector('#downloadAudio').href.startsWith('blob:')"));
        } finally {getInstrumentation().runOnMainSync(activity::finish);}
    }
    public void testOfflineVoicesAndPronunciation() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        File files=ModelStorage.prepare(context,message->{});
        SpeechEngine engine=new SpeechEngine(context,files);
        assertTrue(engine.voices().length()>=40);
        for(String voice:new String[]{"voicevox-11","voicevox-2","voicevox-13"}) {
            JSONObject request=new JSONObject().put("voice",voice).put("engine","voicevox").put("text","いいえ。大きい青いケーキです。何をしますか？").put("speed",1).put("intonation",1).put("pronunciation","natural");
            byte[] wav=engine.synthesize(context,request);
            assertTrue(wav.length>10000);
            assertEquals("RIFF",new String(wav,0,4,StandardCharsets.US_ASCII));
            assertEquals("WAVE",new String(wav,8,4,StandardCharsets.US_ASCII));
        }
        JSONObject base=new JSONObject().put("voice","voicevox-2").put("text","何をしますか？").put("pronunciation","natural");
        byte[] natural=engine.synthesize(context,base);
        byte[] original=engine.synthesize(context,base.put("pronunciation","original"));
        assertFalse(Arrays.equals(natural,original));
    }
    public void testLongVowelsKeepMorasAndAccent() throws Exception {
        JSONObject first=new JSONObject().put("text","イ").put("vowel","i").put("consonant",JSONObject.NULL).put("vowel_length",0.169).put("pitch",5.692);
        JSONObject second=new JSONObject().put("text","イ").put("vowel","i").put("consonant",JSONObject.NULL).put("vowel_length",0.112).put("pitch",5.838);
        JSONObject phrase=new JSONObject().put("accent",2).put("moras",new JSONArray().put(first).put(second));
        JSONObject query=new JSONObject().put("accent_phrases",new JSONArray().put(phrase));
        Pronunciation.natural(query);
        assertEquals(2,phrase.getJSONArray("moras").length());
        assertEquals(2,phrase.getInt("accent"));
        assertEquals(5.692,first.getDouble("pitch"),0.000001);
        assertEquals(5.838,second.getDouble("pitch"),0.000001);
        assertEquals(0.24,first.getDouble("vowel_length")+second.getDouble("vowel_length"),0.000001);
    }
}

package id.kotoba.studio;

import android.content.Context;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.json.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class OfflineSpeechTest {
    private static Instrumentation getInstrumentation() { return InstrumentationRegistry.getInstrumentation(); }
    private static WebView findWeb(View view) {
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WebView found=findWeb(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    static String evaluate(MainActivity activity,String script) throws Exception {
        AtomicReference<String> value=new AtomicReference<>("");CountDownLatch latch=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->{WebView web=findWeb(activity.getWindow().getDecorView());if(web==null){latch.countDown();return;}web.evaluateJavascript(script,result->{value.set(result);latch.countDown();});});
        assertTrue(latch.await(15,TimeUnit.SECONDS));return value.get();
    }
    @Test public void testWebInterfaceSynthesizesWithoutNetwork() throws Exception {
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(intent);
        ListenableFuture<MediaController> connection=new MediaController.Builder(getInstrumentation().getTargetContext(),new SessionToken(getInstrumentation().getTargetContext(),new ComponentName(getInstrumentation().getTargetContext(),PlaybackService.class))).buildAsync();
        MediaController player=connection.get(15,TimeUnit.SECONDS);
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
            evaluate(activity,"document.querySelector('#resultPanel').scrollIntoView();true");
            Thread.sleep(500);
            ContentValues image=new ContentValues();image.put(MediaStore.Images.Media.DISPLAY_NAME,"android-player.png");image.put(MediaStore.Images.Media.MIME_TYPE,"image/png");image.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/KotobaTests");
            android.net.Uri screenshot=activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,image);
            try(java.io.OutputStream out=activity.getContentResolver().openOutputStream(screenshot)) {
                getInstrumentation().getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out);
            }
            getInstrumentation().runOnMainSync(()->{player.seekTo(0);player.setRepeatMode(Player.REPEAT_MODE_ONE);player.play();});
            for(int i=0;i<30;i++){AtomicReference<Boolean> playing=new AtomicReference<>(false);getInstrumentation().runOnMainSync(()->playing.set(player.isPlaying()));if(playing.get())break;Thread.sleep(200);}
            AtomicReference<Long> before=new AtomicReference<>(0L),after=new AtomicReference<>(0L);
            getInstrumentation().runOnMainSync(()->before.set(player.getCurrentPosition()));
            getInstrumentation().getUiAutomation().executeShellCommand("input keyevent 3").close();
            Thread.sleep(1500);
            AtomicReference<Boolean> background=new AtomicReference<>(false);
            getInstrumentation().runOnMainSync(()->{background.set(player.isPlaying());after.set(player.getCurrentPosition());});
            assertTrue("Audio must keep playing while the launcher is in front",background.get());
            assertTrue("Playback position must advance in background",Math.abs(after.get()-before.get())>500);
            NotificationManager notifications=activity.getSystemService(NotificationManager.class);
            assertTrue("Media playback needs an active foreground notification",Arrays.stream(notifications.getActiveNotifications()).anyMatch(item->(item.getNotification().flags&Notification.FLAG_FOREGROUND_SERVICE)!=0));
            getInstrumentation().getUiAutomation().executeShellCommand("input keyevent 223").close();
            Thread.sleep(1500);
            getInstrumentation().runOnMainSync(()->background.set(player.isPlaying()));
            assertTrue("Audio must keep playing with the screen off",background.get());
            getInstrumentation().getUiAutomation().executeShellCommand("input keyevent 224").close();
            getInstrumentation().getUiAutomation().executeShellCommand("wm dismiss-keyguard").close();
            getInstrumentation().getTargetContext().startActivity(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            Thread.sleep(500);
            getInstrumentation().runOnMainSync(()->{player.pause();player.seekTo(Math.max(0,player.getDuration()-500));});
            Thread.sleep(700);
            assertTrue("Pause from media controller must update the UI",evaluate(activity,"window.KotobaMobile.player.paused").equals("true"));
            assertEquals("Subtitles must resync to the second turn after returning","\"Ayo belajar bahasa Jepang.\"",evaluate(activity,"document.querySelector('#subtitleTranslation').textContent"));
        } finally {
            getInstrumentation().runOnMainSync(()->{player.pause();player.setRepeatMode(Player.REPEAT_MODE_OFF);player.clearMediaItems();MediaController.releaseFuture(connection);activity.finish();});
        }
    }
    @Test public void testOfflineVoicesAndPronunciation() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        File files=ModelStorage.prepare(context,message->{});
        SpeechEngine engine=new SpeechEngine(context,files);
        assertTrue(engine.voices().length()>=40);
        for(String voice:new String[]{"voicevox-11","voicevox-2","voicevox-13","voicevox-21","voicevox-100","voicevox-20","voicevox-27","voicevox-29","voicevox-94","voicevox-11"}) {
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
    @Test public void testLongVowelsKeepMorasAndAccent() throws Exception {
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

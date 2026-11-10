package id.kotoba.studio;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import static org.junit.Assert.*;
import static id.kotoba.studio.AndroidTestSupport.*;

@RunWith(AndroidJUnit4.class)
public class MicrosoftOnlineTest {
    @Test public void realNanamiAndKeitaGenerateAndPlayThroughAndroidUi() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            String ready="";
            for(int i=0;i<90;i++) {
                ready=evaluate(activity,"!!document.querySelector('#speechEngine') && !document.querySelector('#speechEngine').disabled && !document.querySelector('#generateAudio').disabled");
                if(ready.equals("true"))break;Thread.sleep(1000);
            }
            assertEquals("true",ready);
            evaluate(activity,"document.querySelector('#speechEngine').value='online';document.querySelector('#speechEngine').dispatchEvent(new Event('change'));true");
            for(int i=0;i<20;i++) {
                ready=evaluate(activity,"document.querySelector('#maleVoice').value==='ja-JP-KeitaNeural' && document.querySelector('#femaleVoice').value==='ja-JP-NanamiNeural' && !document.querySelector('#generateAudio').disabled");
                if(ready.equals("true"))break;Thread.sleep(500);
            }
            assertEquals("Nanami and Keita should be selectable", "true",ready);
            if(activity.getPackageName().endsWith(".lite")) {
                assertEquals("true",evaluate(activity,"[...document.querySelector('#speechEngine').options].filter(option=>!option.hidden).every(option=>option.value==='online')"));
                assertFalse(new java.io.File(activity.getFilesDir(),"voicevox").exists());
            }
            String script="{cowo[1]} こんにちは。今日は何をしますか？\n{romaji[1]} Konnichiwa. Kyou wa nani o shimasu ka?\n{translate[1]} Halo. Hari ini mau melakukan apa?\n{cewe[2]} 一緒に日本語を勉強しましょう。\n{romaji[2]} Issho ni nihongo o benkyou shimashou.\n{translate[2]} Ayo belajar bahasa Jepang bersama.";
            evaluate(activity,"document.querySelector('#dialogInput').value="+JSONObject.quote(script)+";document.querySelector('#dialogInput').dispatchEvent(new Event('input'));document.querySelector('#maleSpeed').value='0.85';document.querySelector('#femaleSpeed').value='0.85';document.querySelector('#generateAudio').click();true");
            String status="";
            for(int i=0;i<360;i++) {
                status=evaluate(activity,"document.querySelector('#statusText').textContent");
                if(status.contains("Sukses!")||evaluate(activity,"document.querySelector('#statusText').classList.contains('error')").equals("true"))break;
                Thread.sleep(1000);
            }
            assertTrue("Real Microsoft synthesis must finish: "+status,status.contains("Sukses!"));
            assertEquals("true",evaluate(activity,"window.KotobaMobile.player.duration>2 && document.querySelector('#downloadAudio').href.startsWith('blob:')"));
            screenshot(activity,activity.getPackageName().endsWith(".lite")?"android-lite-player.png":"android-microsoft-player.png");
            assertBackgroundPlayback(activity);
            evaluate(activity,"window.KotobaMobile.player.pause();window.KotobaMobile.player.currentTime=window.KotobaMobile.player.duration-0.5;true");Thread.sleep(700);
            assertEquals("\"Ayo belajar bahasa Jepang bersama.\"",evaluate(activity,"document.querySelector('#subtitleTranslation').textContent"));
        } finally {
            evaluate(activity,"window.KotobaMobile.player.pause();localStorage.removeItem('kotoba-speech-engine');localStorage.removeItem('kotoba-tempo');true");
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}

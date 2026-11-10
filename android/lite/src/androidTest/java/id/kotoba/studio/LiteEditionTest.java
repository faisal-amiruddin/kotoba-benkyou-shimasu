package id.kotoba.studio;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.File;
import java.util.zip.ZipFile;

@RunWith(AndroidJUnit4.class)
public class LiteEditionTest {
    @Test public void liteShipsNoOfflineEngineAndNeverPreparesModels() throws Exception {
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("id.kotoba.studio.lite",context.getPackageName());
        SpeechEngine engine=SpeechEngine.prepare(context,message->fail("Lite must not prepare models"));
        assertFalse(engine.available());assertEquals(0,engine.voices().length());
        assertFalse(new File(context.getFilesDir(),"voicevox").exists());
        File apk=new File(context.getApplicationInfo().sourceDir);
        assertTrue("Lite APK must stay below 20 MB",apk.length()<20L*1024*1024);
        try(ZipFile zip=new ZipFile(apk)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                String name=entries.nextElement().getName();
                assertFalse("Unexpected native/model asset: "+name,name.startsWith("lib/")||name.endsWith(".vvm")||name.startsWith("assets/voicevox/")||name.contains("open_jtalk"));
            }
        }
    }
}

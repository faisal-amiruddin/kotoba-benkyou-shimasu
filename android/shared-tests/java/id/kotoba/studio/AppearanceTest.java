package id.kotoba.studio;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static id.kotoba.studio.AndroidTestSupport.*;

@RunWith(AndroidJUnit4.class)
public class AppearanceTest {
    private void waitForTheme(MainActivity activity,String mode) throws Exception {
        String ready="";
        for(int i=0;i<90;i++) {
            ready=evaluate(activity,"document.readyState==='complete' && !window.__themeReloadPending && !!document.querySelector('#themeMode') && document.documentElement.dataset.theme==='"+mode+"' && !document.querySelector('#generateAudio').disabled");
            if(ready.equals("true"))break;Thread.sleep(1000);
        }
        assertEquals("Theme should initialize without breaking voice loading","true",ready);
    }
    @Test public void compactLayoutDarkModePersistenceAndLauncherIcon() throws Exception {
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            waitForTheme(activity,"light");
            assertEquals("true",evaluate(activity,"!document.querySelector('header,.hero,.steps') && document.querySelector('main').firstElementChild.classList.contains('grid')"));
            assertEquals("true",evaluate(activity,"document.documentElement.scrollWidth<=innerWidth"));
            evaluate(activity,"document.querySelector('#themeMode').value='dark';document.querySelector('#themeMode').dispatchEvent(new Event('change'));true");
            waitForTheme(activity,"dark");
            assertEquals("\"rgb(17, 24, 20)\"",evaluate(activity,"getComputedStyle(document.body).backgroundColor"));
            assertEquals("\"dark\"",evaluate(activity,"window.KotobaAndroid.getThemeMode()"));
            getInstrumentation().runOnMainSync(()->assertEquals(0,activity.getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));
            String edition=activity.getPackageName().endsWith(".lite")?"lite":"full";
            screenshot(activity,"android-"+edition+"-dark.png");
            evaluate(activity,"window.__themeReloadPending=true;true");
            getInstrumentation().runOnMainSync(()->findWeb(activity.getWindow().getDecorView()).reload());
            waitForTheme(activity,"dark");
            assertEquals("\"dark\"",evaluate(activity,"document.querySelector('#themeMode').value"));
            evaluate(activity,"document.querySelector('#themeMode').value='light';document.querySelector('#themeMode').dispatchEvent(new Event('change'));true");
            waitForTheme(activity,"light");
            assertEquals("\"rgb(246, 244, 239)\"",evaluate(activity,"getComputedStyle(document.body).backgroundColor"));
            screenshot(activity,"android-"+edition+"-light.png");
            Drawable icon=activity.getApplicationInfo().loadIcon(activity.getPackageManager());
            assertTrue("Launcher must use the new adaptive icon",icon instanceof AdaptiveIconDrawable);
            Bitmap bitmap=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888);
            icon.setBounds(0,0,432,432);icon.draw(new Canvas(bitmap));
            saveBitmap(activity,"android-"+edition+"-icon.png",bitmap);
        } finally {
            evaluate(activity,"document.querySelector('#themeMode').value='auto';document.querySelector('#themeMode').dispatchEvent(new Event('change'));true");
            getInstrumentation().runOnMainSync(activity::finish);
        }
    }
}

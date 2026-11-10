package id.kotoba.studio;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

final class AndroidTestSupport {
    static Instrumentation getInstrumentation() {return InstrumentationRegistry.getInstrumentation();}
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
    static void screenshot(MainActivity activity,String name) throws Exception {
        evaluate(activity,"document.querySelector('#resultPanel').scrollIntoView();true");Thread.sleep(300);
        ContentValues image=new ContentValues();image.put(MediaStore.Images.Media.DISPLAY_NAME,name);image.put(MediaStore.Images.Media.MIME_TYPE,"image/png");image.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/KotobaTests");
        android.net.Uri uri=activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,image);
        try(java.io.OutputStream out=activity.getContentResolver().openOutputStream(uri)){getInstrumentation().getUiAutomation().takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    static void assertBackgroundPlayback(MainActivity activity) throws Exception {
        Instrumentation instrumentation=getInstrumentation();
        ListenableFuture<MediaController> connection=new MediaController.Builder(activity,new SessionToken(activity,new ComponentName(activity,PlaybackService.class))).buildAsync();
        MediaController player=connection.get(15,TimeUnit.SECONDS);
        try {
            instrumentation.runOnMainSync(()->{player.seekTo(0);player.setRepeatMode(Player.REPEAT_MODE_ONE);player.play();});
            AtomicReference<Boolean> playing=new AtomicReference<>(false);
            for(int i=0;i<30;i++){instrumentation.runOnMainSync(()->playing.set(player.isPlaying()));if(playing.get())break;Thread.sleep(200);}
            assertTrue("Generated Microsoft audio must play",playing.get());
            AtomicReference<Long> before=new AtomicReference<>(0L),after=new AtomicReference<>(0L);
            instrumentation.runOnMainSync(()->before.set(player.getCurrentPosition()));
            instrumentation.getUiAutomation().executeShellCommand("input keyevent 3").close();Thread.sleep(1500);
            instrumentation.runOnMainSync(()->{playing.set(player.isPlaying());after.set(player.getCurrentPosition());});
            assertTrue("Audio must keep playing with another app in front",playing.get());
            assertTrue("Playback advances in background",Math.abs(after.get()-before.get())>500);
            NotificationManager notifications=activity.getSystemService(NotificationManager.class);
            assertTrue("Background audio needs a media notification",Arrays.stream(notifications.getActiveNotifications()).anyMatch(item->(item.getNotification().flags&Notification.FLAG_FOREGROUND_SERVICE)!=0));
            instrumentation.getUiAutomation().executeShellCommand("input keyevent 223").close();Thread.sleep(1500);
            instrumentation.runOnMainSync(()->playing.set(player.isPlaying()));
            assertTrue("Audio must keep playing with the screen off",playing.get());
        } finally {
            instrumentation.getUiAutomation().executeShellCommand("input keyevent 224").close();
            instrumentation.getUiAutomation().executeShellCommand("wm dismiss-keyguard").close();
            activity.startActivity(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            instrumentation.runOnMainSync(()->{player.pause();player.setRepeatMode(Player.REPEAT_MODE_OFF);MediaController.releaseFuture(connection);});
            Thread.sleep(400);
        }
    }
}

package id.kotoba.studio;

import android.app.PendingIntent;
import android.content.Intent;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import java.io.File;
import org.json.JSONObject;

@UnstableApi
public final class PlaybackService extends MediaSessionService {
    private MediaSession session;
    @Override public void onCreate() {
        super.onCreate();
        ExoPlayer player=new ExoPlayer.Builder(this).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        PendingIntent activity=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        session=new MediaSession.Builder(this,player).setSessionActivity(activity).build();
        try {
            JSONObject saved=PlaybackStore.read(this);File audio=PlaybackStore.audio(this,saved);
            if(audio!=null&&audio.isFile()){player.setMediaItem(PlaybackStore.item(audio),saved.optLong("positionMs",0));player.prepare();}
        } catch(Exception ignored) {}
    }
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controller) {
        return controller.isTrusted()||getPackageName().equals(controller.getPackageName())?session:null;
    }
    @Override public void onDestroy() {
        if(session!=null){
            try {JSONObject saved=PlaybackStore.read(this);saved.put("positionMs",session.getPlayer().getCurrentPosition());PlaybackStore.write(this,saved);}catch(Exception ignored){}
            session.getPlayer().release();session.release();session=null;
        }
        super.onDestroy();
    }
}

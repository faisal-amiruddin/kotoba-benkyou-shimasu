package id.kotoba.studio;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.json.*;

final class PlaybackStore {
    static File directory(Context context) { return new File(context.getFilesDir(),"playback"); }
    static JSONObject read(Context context) throws Exception {
        File manifest=new File(directory(context),"session.json");
        if(!manifest.isFile())return new JSONObject();
        return new JSONObject(new String(Files.readAllBytes(manifest.toPath()),StandardCharsets.UTF_8));
    }
    static File audio(Context context,JSONObject session) {
        String name=session.optString("file","");
        return name.matches("[a-f0-9-]+\\.wav")?new File(directory(context),name):null;
    }
    static void write(Context context,JSONObject session) throws Exception {
        File root=directory(context);
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Tidak dapat menyimpan sesi audio.");
        File temporary=new File(root,"session.tmp");
        Files.write(temporary.toPath(),session.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temporary.toPath(),new File(root,"session.json").toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    static MediaItem item(File audio) {
        return new MediaItem.Builder().setMediaId(audio.getName()).setUri(Uri.fromFile(audio))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle("Kotoba · Latihan listening Jepang").setArtist("VOICEVOX offline").build()).build();
    }
}

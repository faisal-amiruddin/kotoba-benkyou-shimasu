package id.kotoba.studio;

import android.content.Context;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Lite ships no VOICEVOX model, dictionary, native library or initialization. */
final class SpeechEngine {
    static SpeechEngine prepare(Context context,Consumer<String> progress) {return new SpeechEngine();}
    boolean available() {return false;}
    JSONArray voices() {return new JSONArray();}
    byte[] synthesize(Context context,JSONObject request) {
        throw new IllegalArgumentException("Kotoba Lite menggunakan Microsoft online. VOICEVOX tersedia di Kotoba Full.");
    }
}

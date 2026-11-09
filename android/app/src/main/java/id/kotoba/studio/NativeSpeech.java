package id.kotoba.studio;

final class NativeSpeech {
    static { System.loadLibrary("kotoba_voicevox"); }
    static native void initialize(byte[] dictionary, byte[] runtime, int threads);
    static native byte[] query(byte[] modelPath, byte[] text, int style);
    static native byte[] synthesize(byte[] query, int style, boolean upspeak);
}

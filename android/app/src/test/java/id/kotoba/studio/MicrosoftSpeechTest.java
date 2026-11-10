package id.kotoba.studio;

import org.junit.Test;
import static org.junit.Assert.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okio.ByteString;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class MicrosoftSpeechTest {
    @Test public void secMsGecMatchesMsedgeTtsReferenceVectors() throws Exception {
        assertEquals("52BC7A17B50DFEC9-28C4394775C8751CD2F6859C030C06DB1489ACAB3B3A7651831FC26895177ED6",EdgeProtocol.secMsGec(0));
        assertEquals("6D50DDDB91812C33-DD68EFB01898B0750C9797D51A5FF93471DFDAE6888A1039D532CBDCCE54545D",EdgeProtocol.secMsGec(1760000000000L));
        assertEquals("AA142202A704DC18-06BDE051606776D2F498D7488D9176A471EAD3E8D9D874507BCC70B063746D4C",EdgeProtocol.secMsGec(1794268800000L));
    }
    @Test public void japaneseIsEscapedAndRateAndVoiceAreValidated() {
        String ssml=EdgeProtocol.ssml("いいえ。 <voice> & 日本語\u0000", "ja-JP-NanamiNeural",0.85);
        assertTrue(ssml.contains("いいえ。 &lt;voice&gt; &amp; 日本語"));assertTrue(ssml.contains("rate=\"0.85\""));assertFalse(ssml.contains("\u0000"));
        for(double speed:new double[]{Double.NaN,0.1,3})assertThrows(IllegalArgumentException.class,()->EdgeProtocol.ssml("はい。","ja-JP-KeitaNeural",speed));
        assertThrows(IllegalArgumentException.class,()->EdgeProtocol.ssml("はい。","en-US-AriaNeural",1));
    }
    private static byte[] frame(String id,byte[] audio) {
        byte[] header=("X-RequestId:"+id+"\r\nContent-Type:audio/mpeg\r\nPath:audio\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] result=new byte[2+header.length+audio.length];result[0]=(byte)(header.length>>8);result[1]=(byte)header.length;
        System.arraycopy(header,0,result,2,header.length);System.arraycopy(audio,0,result,2+header.length,audio.length);return result;
    }
    @Test public void audioHeaderLengthAndRequestIdAreChecked() throws Exception {
        byte[] bytes={1,2,3};assertArrayEquals(bytes,EdgeProtocol.audio(frame("abc",bytes),"abc"));
        assertEquals(0,EdgeProtocol.audio(frame("other",bytes),"abc").length);
        assertThrows(IOException.class,()->EdgeProtocol.audio(new byte[]{0,50,1},"abc"));
    }
    private static MockResponse synthesis(boolean complete) {
        return new MockResponse().withWebSocketUpgrade(new WebSocketListener(){
            @Override public void onMessage(WebSocket ws,String message) {
                if(!message.contains("Path:ssml"))return;
                String id=message.substring("X-RequestId:".length(),message.indexOf("\r\n"));
                byte[] audio=new byte[512];Arrays.fill(audio,(byte)17);
                ws.send(ByteString.of(frame(id,audio)));
                if(complete)ws.send("X-RequestId:"+id+"\r\nPath:turn.end\r\n\r\n");
                else ws.close(1000,"Incomplete");
            }
            @Override public void onClosing(WebSocket ws,int code,String reason) {ws.close(code,reason);}
        });
    }
    @Test public void receivesCompleteAudioAndCorrectsServerClockOn403() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(403).setHeader("Date","Tue, 10 Nov 2026 00:00:00 GMT"));server.enqueue(synthesis(true));
            try(MicrosoftSpeech client=new MicrosoftSpeech(new OkHttpClient(),server.url("/speech").toString())) {
                assertEquals(512,client.synthesize("何をしますか？","ja-JP-KeitaNeural",1).length);
            }
            RecordedRequest first=server.takeRequest(5,TimeUnit.SECONDS),second=server.takeRequest(5,TimeUnit.SECONDS);
            assertTrue(first.getRequestUrl().queryParameter("Sec-MS-GEC").matches("[A-F0-9]{16}-[A-F0-9]{64}"));
            long serverTime=java.time.ZonedDateTime.parse("Tue, 10 Nov 2026 00:00:00 GMT",java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            assertEquals(EdgeProtocol.secMsGec(serverTime),second.getRequestUrl().queryParameter("Sec-MS-GEC"));
        }
    }
    @Test public void neverReturnsTruncatedAudioWithoutTurnEnd() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(synthesis(false));server.enqueue(synthesis(false));
            try(MicrosoftSpeech client=new MicrosoftSpeech(new OkHttpClient(),server.url("/speech").toString())) {
                assertThrows(IOException.class,()->client.synthesize("こんにちは。","ja-JP-NanamiNeural",1));
            }
        }
    }
}

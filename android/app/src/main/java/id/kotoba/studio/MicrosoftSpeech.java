package id.kotoba.studio;

import okhttp3.*;
import okio.ByteString;
import java.io.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.*;

/** Direct HTTPS/WSS Read Aloud client; system TLS and hostname checks stay enabled. */
final class MicrosoftSpeech implements AutoCloseable {
    private static final String ENDPOINT="https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    private final OkHttpClient client;
    private final String endpoint;
    private volatile long clockOffsetMs;
    MicrosoftSpeech() {this(new OkHttpClient.Builder().connectTimeout(25,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build(),ENDPOINT);}
    MicrosoftSpeech(OkHttpClient client,String endpoint) {this.client=client;this.endpoint=endpoint;}
    private static final class Failure extends IOException {
        final int status;
        Failure(String message,int status,Throwable cause) {super(message,cause);this.status=status;}
    }
    byte[] synthesize(String text,String voice,double speed) throws Exception {
        String ssml=EdgeProtocol.ssml(text,voice,speed);
        for(int attempt=0;;attempt++) {
            try {return once(ssml);}
            catch(IOException error) {
                boolean retry=!(error instanceof Failure)||((Failure)error).status==0||((Failure)error).status==403||((Failure)error).status==429||((Failure)error).status>=500;
                if(!retry||attempt>=1)throw new IOException("Microsoft online belum berhasil: "+error.getMessage()+" Periksa koneksi internet dan waktu/tanggal HP, lalu coba lagi.",error);
                Thread.sleep(750);
            }
        }
    }
    private byte[] once(String ssml) throws Exception {
        String id=UUID.randomUUID().toString().replace("-","");
        HttpUrl url=HttpUrl.get(endpoint).newBuilder().addQueryParameter("TrustedClientToken",EdgeProtocol.TOKEN)
            .addQueryParameter("Sec-MS-GEC",EdgeProtocol.secMsGec(System.currentTimeMillis()+clockOffsetMs))
            .addQueryParameter("Sec-MS-GEC-Version",EdgeProtocol.VERSION).addQueryParameter("ConnectionId",id).build();
        Request request=new Request.Builder().url(url).header("User-Agent",EdgeProtocol.USER_AGENT)
            .header("Origin","chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold").build();
        CompletableFuture<byte[]> result=new CompletableFuture<>();
        ByteArrayOutputStream audio=new ByteArrayOutputStream();
        WebSocket socket=client.newWebSocket(request,new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response) {
                String config="Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}";
                if(!ws.send(config)||!ws.send("X-RequestId:"+id+"\r\nContent-Type:application/ssml+xml\r\nPath:ssml\r\n\r\n"+ssml))result.completeExceptionally(new IOException("WebSocket tidak dapat mengirim kalimat."));
            }
            @Override public void onMessage(WebSocket ws,ByteString frame) {
                if(result.isDone())return;
                try {
                    byte[] chunk=EdgeProtocol.audio(frame.toByteArray(),id);
                    if(audio.size()+chunk.length>32*1024*1024)throw new IOException("Audio Microsoft terlalu besar. Pendekkan kalimat.");
                    audio.write(chunk);
                }catch(Exception error){result.completeExceptionally(error);ws.cancel();}
            }
            @Override public void onMessage(WebSocket ws,String message) {
                if(message.contains("Path:turn.end")&&message.contains("X-RequestId:"+id+"\r\n")) {
                    if(audio.size()<100)result.completeExceptionally(new IOException("Microsoft mengembalikan audio kosong."));
                    else result.complete(audio.toByteArray());
                    ws.close(1000,"Selesai");
                }
            }
            @Override public void onClosing(WebSocket ws,int code,String reason) {
                if(!result.isDone())result.completeExceptionally(new IOException("Sambungan terputus sebelum audio selesai."));
                ws.close(code,reason);
            }
            @Override public void onFailure(WebSocket ws,Throwable error,Response response) {
                int status=response==null?0:response.code();
                if(status==403&&response.header("Date")!=null) {
                    try {clockOffsetMs=ZonedDateTime.parse(response.header("Date"),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis();}catch(Exception ignored){}
                }
                result.completeExceptionally(new Failure(status==0?"Koneksi HTTPS/WebSocket gagal ("+error.getClass().getSimpleName()+").":"Server Microsoft merespons HTTP "+status+".",status,error));
                if(response!=null)response.close();
            }
        });
        try {return result.get(75,TimeUnit.SECONDS);}
        catch(TimeoutException error){throw new IOException("Koneksi atau sintesis melewati batas waktu 75 detik.",error);}
        catch(ExecutionException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new IOException("Sintesis gagal.",cause);}
        finally {socket.cancel();}
    }
    @Override public void close() {client.dispatcher().cancelAll();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
}

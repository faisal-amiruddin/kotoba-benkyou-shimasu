package id.kotoba.studio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Read Aloud framing and Sec-MS-GEC v2, adapted from msedge-tts 2.1.1 (MIT).
 * See assets/licenses/msedge-tts-LICENSE.txt for attribution and permission. */
final class EdgeProtocol {
    static final String TOKEN="6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    static final String VERSION="2-143.0.3650.96";
    static final String USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static int swap(int value) {return Integer.rotateLeft(value,16);}
    private static int term(int word,int key,int a,int b,int c) {
        int product=swap(word*key+swap(word)*a)*b;
        return swap(product)*c+product;
    }
    private static int mix(int value,int[] multipliers) {for(int multiplier:multipliers)value=swap(value)*multiplier;return value;}
    static String secMsGec(long epochMillis) throws Exception {
        long seconds=Math.floorDiv(epochMillis,1000)+11644473600L;
        long ticks=(seconds-seconds%300)*10000000L;
        return hash(ticks+TOKEN);
    }
    static String hash(String ticksAndToken) throws Exception {
        byte[] message=("Copyright (C) Microsoft Corporation. All rights reserved."+ticksAndToken).getBytes(StandardCharsets.UTF_8);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(message);
        ByteBuffer keys=ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN);
        int firstKey=keys.getInt()|1,secondKey=keys.getInt()|1;
        ByteBuffer words=ByteBuffer.allocate((message.length+7)/8*8).order(ByteOrder.LITTLE_ENDIAN);
        words.put(message).rewind();
        int sum=0,last=0,mixed=0,mixSum=0;
        int[] first={0xCF98B111,0x87085B9F,0x12CEB96D,0x257E1D83},second={0xA27416F5,0xD38396FF,0x7C932B89,0xBFA49F69};
        while(words.hasRemaining()) {
            int a=words.getInt(),b=words.getInt();
            int initial=term(a,firstKey,0xEF0569FB,0x76F50F34,0xC3EFEA97);
            last=term(b,secondKey,0xC31713DB,0x1881CE3C,0x35BD1EC9);
            sum+=initial+last;
            mixed=mix((mixed+a)*firstKey,first);mixSum+=mixed;
            mixed=mix((mixed+b)*secondKey,second);mixSum+=mixed;
        }
        byte[] checksum=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(last^mixed).putInt(sum^mixSum).array();
        return hex(checksum)+"-"+hex(digest);
    }
    private static String hex(byte[] bytes) {StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(Locale.ROOT,"%02X",value&255));return result.toString();}
    static boolean allowedVoice(String voice) {return "ja-JP-NanamiNeural".equals(voice)||"ja-JP-KeitaNeural".equals(voice);}
    static String escape(String text) {
        StringBuilder xml=new StringBuilder();
        text.codePoints().forEach(c->{
            if(c==9||c==10||c==13||c>=32&&c<=0xD7FF||c>=0xE000&&c<=0xFFFD||c>=0x10000&&c<=0x10FFFF) {
                switch(c){case '&':xml.append("&amp;");break;case '<':xml.append("&lt;");break;case '>':xml.append("&gt;");break;case '"':xml.append("&quot;");break;case '\'':xml.append("&apos;");break;default:xml.appendCodePoint(c);}
            }
        });
        return xml.toString();
    }
    static String ssml(String text,String voice,double speed) {
        if(text==null||text.trim().isEmpty()||text.length()>4000)throw new IllegalArgumentException("Kalimat harus berisi 1–4000 karakter.");
        if(!allowedVoice(voice))throw new IllegalArgumentException("Pilih suara Microsoft Nanami atau Keita.");
        if(!Double.isFinite(speed)||speed<0.7||speed>2)throw new IllegalArgumentException("Tempo harus antara 0,70 dan 2,00.");
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"ja-JP\"><voice name=\""+voice+"\"><prosody pitch=\"+0Hz\" rate=\""+speed+"\" volume=\"+0%\">"+escape(text)+"</prosody></voice></speak>";
    }
    static byte[] audio(byte[] frame,String requestId) throws Exception {
        if(frame.length<2)throw new java.io.IOException("Frame audio Microsoft tidak lengkap.");
        int length=((frame[0]&255)<<8)|(frame[1]&255);
        int start=2+length;
        if(start>frame.length)throw new java.io.IOException("Header audio Microsoft tidak lengkap.");
        String headers=new String(frame,2,length,StandardCharsets.UTF_8);
        if(!headers.contains("Path:audio\r\n")||!headers.contains("X-RequestId:"+requestId+"\r\n"))return new byte[0];
        return java.util.Arrays.copyOfRange(frame,start,frame.length);
    }
}

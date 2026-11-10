package id.kotoba.studio;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class ModelStorage {
    interface Progress { void update(String message); }
    static File prepare(Context context,Progress progress) throws Exception {
        File root=new File(context.getFilesDir(),"voicevox");
        String version=readAsset(context,"voicevox/install-version.txt").trim();
        File marker=new File(root,"installed-version.txt");
        if(marker.isFile()&&new String(java.nio.file.Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8).trim().equals(version)) return root;
        long needed=650L*1024*1024;
        if(context.getFilesDir().getUsableSpace()<needed) throw new IOException("Ruang penyimpanan kurang. Sediakan setidaknya 700 MB untuk menyiapkan model offline.");
        copy(context.getAssets(),"voicevox",root,progress);
        try(FileOutputStream out=new FileOutputStream(marker)) { out.write(version.getBytes(StandardCharsets.UTF_8)); }
        return root;
    }
    private static void copy(AssetManager assets,String path,File target,Progress progress) throws Exception {
        String[] children=assets.list(path);
        if(children!=null&&children.length>0) {
            if(!target.isDirectory()&&!target.mkdirs()) throw new IOException("Tidak dapat membuat folder model.");
            for(String child:children) copy(assets,path+"/"+child,new File(target,child),progress);
        } else {
            progress.update(path.endsWith(".vvm")?"Menyiapkan paket suara offline…":path.contains("/dict/")?"Menyiapkan kamus bahasa Jepang…":"Menyiapkan Kotoba lokal…");
            try(InputStream in=assets.open(path);FileOutputStream out=new FileOutputStream(target)) { byte[] buffer=new byte[256*1024];int n;while((n=in.read(buffer))!=-1) out.write(buffer,0,n); }
        }
    }
    static String readAsset(Context context,String path) throws Exception {
        try(InputStream in=context.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8"); }
    }
}

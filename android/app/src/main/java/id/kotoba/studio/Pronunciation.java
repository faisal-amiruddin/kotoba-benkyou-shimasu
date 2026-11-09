package id.kotoba.studio;

import org.json.JSONArray;
import org.json.JSONObject;

/** Mirrors lib/pronunciation.py. Morae and lexical pitch accents remain intact. */
final class Pronunciation {
    static void natural(JSONObject query) throws Exception {
        JSONArray phrases=query.getJSONArray("accent_phrases");
        for(int p=0;p<phrases.length();p++) {
            JSONArray moras=phrases.getJSONObject(p).getJSONArray("moras");
            int start=0;
            while(start<moras.length()) {
                JSONObject first=moras.getJSONObject(start);
                String vowel=first.getString("vowel");
                int end=start+1;
                if(vowel.length()==1&&"aiueo".contains(vowel)) {
                    while(end<moras.length()&&moras.getJSONObject(end).getString("vowel").equals(vowel)&&moras.getJSONObject(end).isNull("consonant")) end++;
                    if(end-start>=2) {
                        double total=0,limit=0.12*(end-start);
                        for(int i=start;i<end;i++) total+=moras.getJSONObject(i).getDouble("vowel_length");
                        if(total>limit) for(int i=start;i<end;i++) {
                            JSONObject mora=moras.getJSONObject(i);
                            mora.put("vowel_length",mora.getDouble("vowel_length")*limit/total);
                        }
                    }
                }
                if(vowel.equals("N")) {
                    first.put("vowel_length",Math.min(first.getDouble("vowel_length"),0.18));
                    if(start+1<moras.length()&&moras.getJSONObject(start+1).optString("consonant").equals("n")) {
                        JSONObject next=moras.getJSONObject(start+1);
                        double length=next.optDouble("consonant_length",0),total=first.getDouble("vowel_length")+length;
                        if(total>0.20) { first.put("vowel_length",first.getDouble("vowel_length")*0.20/total); next.put("consonant_length",length*0.20/total); }
                    }
                }
                start=end;
            }
        }
    }
}

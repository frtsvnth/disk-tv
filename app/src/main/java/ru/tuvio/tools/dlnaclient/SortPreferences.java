package ru.tuvio.tools.dlnaclient;
import android.content.Context;
import android.content.SharedPreferences;
import java.text.Collator;
import java.util.*;
final class SortPreferences {
 private final SharedPreferences prefs;
 SortPreferences(Context c){this(c.getSharedPreferences("browse_settings",Context.MODE_PRIVATE));}
 SortPreferences(SharedPreferences p){prefs=p;}
 int field(){try{return Math.max(0,Math.min(4,prefs.getInt("field",0)));}catch(ClassCastException e){return 0;}}
 boolean descending(){return prefs.getBoolean("descending",false);}
 boolean foldersFirst(){return prefs.getBoolean("folders_first",true);}
 void field(int v){prefs.edit().putInt("field",v).apply();}
 void descending(boolean v){prefs.edit().putBoolean("descending",v).apply();}
 void foldersFirst(boolean v){prefs.edit().putBoolean("folders_first",v).apply();}
 void sort(List<DmsItem> items){
  final int field=field(); final boolean reverse=descending(), folders=foldersFirst();
  Map<DmsItem,Integer> original=new IdentityHashMap<>();for(int i=0;i<items.size();i++)original.put(items.get(i),i);
  Collator names=Collator.getInstance();names.setStrength(Collator.SECONDARY);
  items.sort((a,b)->{
   if(folders&&a.isFolder!=b.isFolder)return a.isFolder?-1:1;
   int result;
   if(field==2)result=Integer.compare(original.get(a),original.get(b));
   else if(field==0)result=names.compare(a.title,b.title);
   else {
    long av=field==1?a.size:field==3?MediaDetails.date(a.date):a.durationMs;
    long bv=field==1?b.size:field==3?MediaDetails.date(b.date):b.durationMs;
    if(av<=0&&bv>0)return 1;if(bv<=0&&av>0)return -1;
    result=Long.compare(av,bv);
   }
   if(result==0)result=names.compare(a.title,b.title);
   return reverse?-result:result;
  });
 }
}

package ru.tuvio.tools.dlnaclient;
import android.content.Context;
import android.content.SharedPreferences;
final class PlaybackPreferences {
 private final SharedPreferences prefs;
 PlaybackPreferences(Context c){this(c.getSharedPreferences("playback_settings",Context.MODE_PRIVATE));}
 PlaybackPreferences(SharedPreferences p){prefs=p;}
 String string(String key,String fallback){try{String s=prefs.getString(key,fallback);return s==null?fallback:s;}catch(ClassCastException e){return fallback;}}
 int integer(String key,int fallback){try{return prefs.getInt(key,fallback);}catch(ClassCastException e){return fallback;}}
 boolean bool(String key,boolean fallback){try{return prefs.getBoolean(key,fallback);}catch(ClassCastException e){return fallback;}}
 void set(String key,String value){prefs.edit().putString(key,value).apply();}
 void set(String key,int value){prefs.edit().putInt(key,value).apply();}
 void set(String key,boolean value){prefs.edit().putBoolean(key,value).apply();}
 void remove(String key){prefs.edit().remove(key).apply();}
 long seekMs(){int n=integer("seek",1);return (n==1||n==2||n==5||n==10?n:1)*1000L;}
 int bufferMb(){int n=integer("buffer",32);return n==16||n==32||n==64?n:32;}
 int subtitleSize(){return Math.max(14,Math.min(48,integer("subtitle_size",22)));}
 String language(String key,String fallback){String s=string(key,fallback);return s.equals("system")?java.util.Locale.getDefault().getLanguage():s;}
}

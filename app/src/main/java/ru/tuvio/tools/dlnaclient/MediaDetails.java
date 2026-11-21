package ru.tuvio.tools.dlnaclient;
import java.util.*;
final class MediaDetails {
 static long duration(String value) {
  try { String[] p=value.split(":"); if(p.length!=3)return 0;
   return Math.max(0,Math.round((Long.parseLong(p[0])*3600+Long.parseLong(p[1])*60+Double.parseDouble(p[2]))*1000));
  }catch(Exception e){return 0;}
 }
 static long date(String value) {
  if(value==null || value.isEmpty())return 0;
  try{return java.time.Instant.parse(value).toEpochMilli();}catch(Exception ignored){}
  try{return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli();}catch(Exception ignored){}
  try{return java.time.LocalDate.parse(value.substring(0,10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();}catch(Exception ignored){return 0;}
 }
 static String subtitleMime(String type,String url) {
  String mime=(type==null?"":type).toLowerCase(Locale.ROOT).split(";",2)[0].trim();
  String path=(url==null?"":url).split("[?#]",2)[0].toLowerCase(Locale.ROOT);
  if(mime.equals("srt")||mime.contains("subrip")||mime.equals("text/srt")||path.endsWith(".srt"))return "application/x-subrip";
  if(mime.equals("vtt")||mime.equals("text/vtt")||path.endsWith(".vtt"))return "text/vtt";
  if(mime.equals("ssa")||mime.equals("ass")||mime.equals("text/x-ssa")||mime.equals("text/x-ass")||path.endsWith(".ass")||path.endsWith(".ssa"))return "text/x-ssa";
  if(mime.equals("ttml")||mime.equals("application/ttml+xml")||path.endsWith(".ttml")||path.endsWith(".dfxp"))return "application/ttml+xml";
  return null;
 }
 static String stem(String title) {
  return title.toLowerCase(Locale.ROOT).replaceFirst("\\.(mkv|mp4|avi|mov|m2ts|ts|srt|vtt|ass|ssa|ttml)$", "");
 }
 static void addSubtitle(DmsItem video,DmsItem.Subtitle sub) {
  for(DmsItem.Subtitle old:video.subtitles)if(old.url.equals(sub.url))return;
  video.subtitles.add(sub);
 }
 static void attachSidecars(List<DmsItem> items) {
  for(DmsItem video:items) {
   if(video.isFolder || video.url==null || subtitleMime(video.mimeType,video.url)!=null)continue;
   String base=stem(video.title);
   for(DmsItem sub:items) {
    if(sub.isFolder||sub.url==null)continue;
    String mime=subtitleMime(sub.mimeType,sub.url);if(mime==null)continue;
    String name=stem(sub.title);
    if(!name.equals(base)&&!name.startsWith(base+"."))continue;
    String language=name.equals(base)?null:name.substring(base.length()+1);
    if(language!=null&&!language.matches("[a-z]{2,3}(-[a-z]{2})?"))continue;
    addSubtitle(video,new DmsItem.Subtitle(sub.url,mime,language,sub.title));
   }
  }
 }
}

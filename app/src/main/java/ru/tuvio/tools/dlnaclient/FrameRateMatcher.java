package ru.tuvio.tools.dlnaclient;
final class FrameRateMatcher {
 static int choose(float fps,float[] rates,int current){
  if(!Float.isFinite(fps)||fps<10||fps>240)return -1;
  if(current>=0&&current<rates.length&&matches(fps,rates[current]))return current;
  int best=-1;double score=Double.MAX_VALUE;
  for(int i=0;i<rates.length;i++)if(matches(fps,rates[i])){
   double multiple=Math.rint(rates[i]/fps),error=Math.abs(rates[i]-fps*multiple);
   double candidate=multiple+error;
   if(candidate<score){score=candidate;best=i;}
  }
  return best;
 }
 private static boolean matches(float fps,float rate){double multiple=Math.rint(rate/fps);return multiple>=1&&multiple<=8&&Math.abs(rate-fps*multiple)<0.12;}
}

package ru.tuvio.tools.dlnaclient;
final class SeekPolicy {
 static long increment(long base,long heldMs){
  base=Math.max(1000,Math.min(10000,base));
  return heldMs>=4000?Math.max(base,10000):heldMs>=1500?Math.max(base,5000):base;
 }
}

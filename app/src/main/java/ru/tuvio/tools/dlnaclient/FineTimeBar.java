package ru.tuvio.tools.dlnaclient;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class FineTimeBar extends androidx.media3.ui.DefaultTimeBar {
 private long base=1000;
 public FineTimeBar(Context c,AttributeSet a){super(c,a);setKeyTimeIncrement(base);}
 public void setBaseIncrement(long value){base=value;setKeyTimeIncrement(base);}
 @Override public boolean onKeyDown(int code,KeyEvent event){
  if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT)
   setKeyTimeIncrement(SeekPolicy.increment(base,event.getEventTime()-event.getDownTime()));
  return super.onKeyDown(code,event);
 }
}

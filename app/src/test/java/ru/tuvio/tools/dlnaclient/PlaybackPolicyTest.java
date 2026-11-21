package ru.tuvio.tools.dlnaclient;
import org.junit.Test;import static org.junit.Assert.*;
public class PlaybackPolicyTest {
 @Test public void shortSeekIsOneSecondRegardlessOfMovieLength(){assertEquals(1000,SeekPolicy.increment(1000,0));assertEquals(1000,SeekPolicy.increment(1000,1000));}
 @Test public void holdingAcceleratesGradually(){assertEquals(5000,SeekPolicy.increment(1000,2000));assertEquals(10000,SeekPolicy.increment(1000,6000));assertEquals(10000,SeekPolicy.increment(1000000,6000));}
 @Test public void currentCompatibleFrameRateIsRetained(){assertEquals(1,FrameRateMatcher.choose(29.97f,new float[]{30,59.94f,24},1));}
 @Test public void incompatibleRateSelectsCinemaMode(){assertEquals(0,FrameRateMatcher.choose(23.976f,new float[]{23.976f,50,60},2));}
 @Test public void unsupportedFrameRateDoesNotForceMode(){assertEquals(-1,FrameRateMatcher.choose(24,new float[]{50,60},1));assertEquals(-1,FrameRateMatcher.choose(-1,new float[]{24},0));}
 @Test public void playbackDefaultsAreConservative(){PlaybackPreferences p=new PlaybackPreferences(new MemoryPreferences());assertEquals(1000,p.seekMs());assertEquals(32,p.bufferMb());assertFalse(p.bool("frame_match",false));assertEquals("off",p.string("subtitle_language","off"));}
 @Test public void corruptAndOutOfRangePreferencesFallBack(){MemoryPreferences m=new MemoryPreferences();m.edit().putString("seek","bad").putInt("buffer",9999).putInt("subtitle_size",9999).apply();PlaybackPreferences p=new PlaybackPreferences(m);assertEquals(1000,p.seekMs());assertEquals(32,p.bufferMb());assertEquals(48,p.subtitleSize());}
}

package ru.tuvio.tools.dlnaclient;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class SortPreferencesTest {
 private DmsItem item(String title,boolean folder,long size){return new DmsItem(title,"0",title,"video/mp4","http://example.invalid/video.mp4",size,folder,null,null);}
 private List<String> titles(List<DmsItem> items){List<String> names=new ArrayList<>();for(DmsItem i:items)names.add(i.title);return names;}
 @Test public void alphabeticFoldersBeforeFilesByDefault(){List<DmsItem> list=new ArrayList<>(Arrays.asList(item("Zebra",false,1),item("Beta",true,0),item("Alpha",false,1),item("alpha",true,0)));new SortPreferences(new MemoryPreferences()).sort(list);assertEquals(Arrays.asList("alpha","Beta","Alpha","Zebra"),titles(list));}
 @Test public void descendingStillKeepsFoldersFirst(){MemoryPreferences p=new MemoryPreferences();SortPreferences s=new SortPreferences(p);s.descending(true);List<DmsItem> list=new ArrayList<>(Arrays.asList(item("A",false,1),item("B",true,0),item("C",true,0),item("Z",false,1)));s.sort(list);assertEquals(Arrays.asList("C","B","Z","A"),titles(list));}
 @Test public void canMixFoldersWithFiles(){SortPreferences s=new SortPreferences(new MemoryPreferences());s.foldersFirst(false);List<DmsItem> list=new ArrayList<>(Arrays.asList(item("Z",true,0),item("A",false,1)));s.sort(list);assertEquals("A",list.get(0).title);}
 @Test public void unknownSizeIsLastInEitherDirection(){SortPreferences s=new SortPreferences(new MemoryPreferences());s.field(1);s.descending(true);List<DmsItem> list=new ArrayList<>(Arrays.asList(item("Unknown",false,0),item("Small",false,5),item("Big",false,100)));s.sort(list);assertEquals(Arrays.asList("Big","Small","Unknown"),titles(list));}
 @Test public void serverOrderCanBeReversed(){SortPreferences s=new SortPreferences(new MemoryPreferences());s.field(2);s.foldersFirst(false);s.descending(true);List<DmsItem> list=new ArrayList<>(Arrays.asList(item("B",false,1),item("A",false,1),item("C",false,1)));s.sort(list);assertEquals(Arrays.asList("C","A","B"),titles(list));}
 @Test public void invalidSavedFieldFallsBack(){MemoryPreferences p=new MemoryPreferences();p.edit().putInt("field",500).apply();assertEquals(4,new SortPreferences(p).field());}
}

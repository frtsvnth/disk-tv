package ru.tuvio.tools.dlnaclient;

import java.io.Serializable;

/** Single browse item: a folder or a file exposed by a DLNA MediaServer. */
public class DmsItem implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String objectId;
    public final String parentId;
    public final String title;
    public final String mimeType;
    public final String url;          // http://... for playable items, null for folders
    public final long size;
    public final boolean isFolder;
    public final String album;
    public final String artist;
    public final long durationMs;
    public final String date, genre, description;
    public final java.util.List<Subtitle> subtitles = new java.util.ArrayList<>();
    public static final class Subtitle implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String url, mime, language, label;
        public Subtitle(String url, String mime, String language, String label) {
            this.url=url; this.mime=mime; this.language=language; this.label=label;
        }
    }

    public DmsItem(String objectId, String parentId, String title, String mimeType,
                   String url, long size, boolean isFolder, String album, String artist) {
        this(objectId,parentId,title,mimeType,url,size,isFolder,album,artist,0,"","","");
    }
    public DmsItem(String objectId, String parentId, String title, String mimeType,
                   String url, long size, boolean isFolder, String album, String artist,
                   long durationMs, String date, String genre, String description) {
        this.durationMs=durationMs; this.date=date; this.genre=genre; this.description=description;
        this.objectId = objectId;
        this.parentId = parentId;
        this.title = title;
        this.mimeType = mimeType;
        this.url = url;
        this.size = size;
        this.isFolder = isFolder;
        this.album = album;
        this.artist = artist;
    }

    @Override
    public String toString() {
        return title;
    }
}

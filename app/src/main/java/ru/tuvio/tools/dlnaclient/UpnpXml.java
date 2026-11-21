package ru.tuvio.tools.dlnaclient;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Namespace-aware parsing shared by discovery and ContentDirectory. */
final class UpnpXml {
    static Document document(String xml) throws Exception {
        if (xml.contains("<!DOCTYPE")) throw new IllegalArgumentException("DOCTYPE is not allowed");
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setExpandEntityReferences(false);
        javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
        b.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return b.parse(new InputSource(new StringReader(xml)));
    }

    static String text(Element e, String name) {
        NodeList nodes = e.getElementsByTagNameNS("*", name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    static Server description(String location, String xml) throws Exception {
        Element root = document(xml).getDocumentElement();
        String base = text(root, "URLBase");
        if (base.isEmpty()) base = location;
        URL baseUrl = new URL(new URL(location), base);
        NodeList services = root.getElementsByTagNameNS("*", "service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            String type = text(service, "serviceType");
            if (!type.startsWith("urn:schemas-upnp-org:service:ContentDirectory:")) continue;
            Element device = (Element) service.getParentNode().getParentNode();
            String udn = text(device, "UDN");
            String control = text(service, "controlURL");
            if (udn.isEmpty() || control.isEmpty()) continue;
            String name = text(device, "friendlyName");
            URL endpoint = new URL(baseUrl, control);
            return new Server(udn, name.isEmpty() ? endpoint.getHost() : name,
                    baseUrl.toString(), endpoint.toString(), endpoint.getHost(),
                    endpoint.getPort() > 0 ? endpoint.getPort() : endpoint.getDefaultPort(), type);
        }
        return null;
    }

    static final class Page {
        final List<DmsItem> items;
        final int returned, total;
        Page(List<DmsItem> items, int returned, int total) {
            this.items = items; this.returned = returned; this.total = total;
        }
    }

    static Page browse(String soap, String base) throws Exception {
        Element root = document(soap).getDocumentElement();
        if (root.getElementsByTagNameNS("*", "Fault").getLength() > 0)
            throw new java.io.IOException("DLNA: " + text(root, "errorCode") + " " + text(root, "errorDescription"));
        NodeList results = root.getElementsByTagNameNS("*", "Result");
        if (results.getLength() == 0) throw new java.io.IOException("DLNA: missing Browse Result");
        String didl = results.item(0).getTextContent().trim();
        List<DmsItem> items = new ArrayList<>();
        if (!didl.isEmpty()) {
            Element listing = document(didl).getDocumentElement();
            for (Node n = listing.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) continue;
                Element e = (Element) n;
                boolean folder = "container".equals(e.getLocalName());
                if (!folder && !"item".equals(e.getLocalName())) continue;
                String url = null, mime = null;
                long size = 0, duration = 0;
                java.util.List<DmsItem.Subtitle> subtitles = new ArrayList<>();
                NodeList resources = e.getElementsByTagNameNS("*", "res");
                for (int k = 0; k < resources.getLength(); k++) {
                    Element res = (Element) resources.item(k);
                    String[] protocol = res.getAttribute("protocolInfo").split(":", 4);
                    String candidate = res.getTextContent().trim();
                    if (candidate.isEmpty()) continue;
                    URL resolved = new URL(new URL(base), candidate);
                    if (!resolved.getProtocol().equals("http") && !resolved.getProtocol().equals("https")) continue;
                    String candidateMime = protocol.length > 2 ? protocol[2] : "";
                    String subtitleMime=MediaDetails.subtitleMime(candidateMime,candidate);
                    if(subtitleMime!=null) {
                        subtitles.add(new DmsItem.Subtitle(resolved.toString(),subtitleMime,res.getAttribute("lang"),"Субтитры"));
                        continue;
                    }
                    boolean video = candidateMime.startsWith("video/");
                    if (url == null || (video && (mime == null || !mime.startsWith("video/")))) {
                        url = resolved.toString(); mime = candidateMime;
                        try { size = Long.parseLong(res.getAttribute("size")); } catch (NumberFormatException ignored) {}
                        duration=MediaDetails.duration(res.getAttribute("duration"));
                    }
                }
                String title = text(e, "title");
                if (title.isEmpty()) title = e.getAttribute("id");
                if (mime == null || mime.isEmpty()) mime = text(e, "class");
                DmsItem item=new DmsItem(e.getAttribute("id"), e.getAttribute("parentID"), title,
                        mime, url, size, folder, text(e,"album"),text(e,"artist"),duration,
                        text(e,"date"),text(e,"genre"),text(e,"description"));
                for(DmsItem.Subtitle sub:subtitles)MediaDetails.addSubtitle(item,sub);
                for(String tag:new String[]{"CaptionInfoEx","CaptionInfo"}) {
                    NodeList captions=e.getElementsByTagNameNS("*",tag);
                    for(int k=0;k<captions.getLength();k++) {
                        Element caption=(Element)captions.item(k);
                        String address=caption.getTextContent().trim();
                        if(address.isEmpty())continue;
                        URL resolved=new URL(new URL(base),address);
                        if(!resolved.getProtocol().equals("http")&&!resolved.getProtocol().equals("https"))continue;
                        String type="",language="";
                        NamedNodeMap attributes=caption.getAttributes();
                        for(int a=0;a<attributes.getLength();a++) {
                            Node attr=attributes.item(a);
                            if("type".equals(attr.getLocalName()))type=attr.getNodeValue();
                            if("lang".equals(attr.getLocalName()))language=attr.getNodeValue();
                        }
                        String subMime=MediaDetails.subtitleMime(type,address);
                        if(subMime!=null)MediaDetails.addSubtitle(item,new DmsItem.Subtitle(resolved.toString(),subMime,language,"Субтитры"));
                    }
                }
                // Subtitle-only items remain available for sidecar matching.
                if(item.url==null && !subtitles.isEmpty()) {
                    DmsItem.Subtitle sub=subtitles.get(0);
                    item=new DmsItem(item.objectId,item.parentId,item.title,sub.mime,sub.url,item.size,false,item.album,item.artist);
                }
                items.add(item);
            }
        }
        return new Page(items, number(text(root, "NumberReturned"), items.size()),
                number(text(root, "TotalMatches"), -1));
    }

    private static int number(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; }
    }
}

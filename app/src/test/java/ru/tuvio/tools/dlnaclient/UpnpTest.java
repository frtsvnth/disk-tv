package ru.tuvio.tools.dlnaclient;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class UpnpTest {
    static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    static String soap(String didl, int returned, int total) {
        return "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body>"
                + "<u:BrowseResponse xmlns:u='urn:schemas-upnp-org:service:ContentDirectory:1'>"
                + "<u:Result>" + escape(didl) + "</u:Result><NumberReturned>" + returned
                + "</NumberReturned><TotalMatches>" + total + "</TotalMatches></u:BrowseResponse></s:Body></s:Envelope>";
    }
    static String didl(String inner) {
        return "<DIDL-Lite xmlns='urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/' xmlns:t='http://purl.org/dc/elements/1.1/' xmlns:c='urn:schemas-upnp-org:metadata-1-0/upnp/'>" + inner + "</DIDL-Lite>";
    }
    @Test public void parsesNamespacesAttributesEntitiesAndVideoResource() throws Exception {
        String xml = didl("<container parentID='0' restricted='1' id='folder'><t:title>A &amp; B</t:title></container>"
                + "<item parentID='folder' id='movie'><t:title>Фильм &amp; &lt;2026&gt;</t:title>"
                + "<c:class>object.item.videoItem.movie</c:class><res protocolInfo='http-get:*:image/jpeg:*'>/cover.jpg</res>"
                + "<res size='123456' protocolInfo='http-get:*:video/x-matroska:*'>/video?id=1&amp;name=A%26B</res></item>");
        UpnpXml.Page page = UpnpXml.browse(soap(xml, 2, 2), "http://localhost:8200/desc.xml");
        assertEquals(2, page.items.size());
        assertEquals("A & B", page.items.get(0).title);
        DmsItem movie = page.items.get(1);
        assertEquals("Фильм & <2026>", movie.title);
        assertEquals("video/x-matroska", movie.mimeType);
        assertEquals("http://localhost:8200/video?id=1&name=A%26B", movie.url);
        assertEquals(123456, movie.size);
    }
    @Test public void resolvesDescriptionRelativeToLocationAndCorrectService() throws Exception {
        String xml = "<root xmlns='urn:schemas-upnp-org:device-1-0'><device><friendlyName>NAS &amp; TV</friendlyName>"
                + "<UDN>uuid:test</UDN><serviceList><service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><controlURL>/wrong</controlURL></service>"
                + "<service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:2</serviceType><controlURL>control</controlURL></service></serviceList></device></root>";
        Server server = UpnpXml.description("http://localhost:8200/upnp/description.xml", xml);
        assertEquals("http://localhost:8200/upnp/control", server.controlUrl);
        assertEquals("NAS & TV", server.friendlyName);
        assertTrue(server.serviceType.endsWith(":2"));
        server = UpnpXml.description("http://localhost/device.xml", xml.replace("<device>", "<URLBase>http://localhost:9000/base/</URLBase><device>"));
        assertEquals("http://localhost:9000/base/control", server.controlUrl);
    }
    @Test public void faultsAndMissingResultsAreErrors() throws Exception {
        try {
            UpnpXml.browse("<Envelope><Fault><errorCode>701</errorCode><errorDescription>No such object</errorDescription></Fault></Envelope>", "http://localhost/"); fail();
        } catch (java.io.IOException e) { assertTrue(e.getMessage().contains("701")); }
        try { UpnpXml.browse("<Envelope/>", "http://localhost/"); fail(); }
        catch (java.io.IOException expected) {}
    }
    @Test public void rejectsExternalEntities() throws Exception {
        try { UpnpXml.document("<!DOCTYPE x SYSTEM 'file:///private/etc/passwd'><x/>"); fail(); }
        catch (IllegalArgumentException expected) {}
    }
    @Test public void loadsAllPagesEvenWhenServerCapsPageSize() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (TestHttpServer http = new TestHttpServer(request -> {
            int start = Integer.parseInt(request.split("<StartingIndex>")[1].split("<")[0]);
            requests.incrementAndGet();
            StringBuilder items = new StringBuilder();
            for (int i = start; i < Math.min(450, start + 75); i++)
                items.append("<item id='").append(i).append("' parentID='0'><t:title>Movie</t:title></item>");
            return soap(didl(items.toString()), Math.min(75, 450 - start), 450);
        })) {
            String base = "http://127.0.0.1:" + http.port();
            List<DmsItem> items = UpnpClient.browseAll(new Server("uuid:test", "NAS", base, "/control", "127.0.0.1", http.port()), "0", 0, 200);
            assertEquals(450, items.size()); assertEquals("449", items.get(449).objectId); assertEquals(6, requests.get());
        }
    }
    @Test public void refusesServerThatIgnoresStartingIndex() throws Exception {
        try (TestHttpServer http = new TestHttpServer(request -> soap(didl("<item id='one' parentID='0'><t:title>One</t:title></item>"), 1, 10))) {
            String base = "http://127.0.0.1:" + http.port();
            try { UpnpClient.browseAll(new Server("uuid:test", "NAS", base, "/control", "127.0.0.1", http.port()), "0", 0, 200); fail(); }
            catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("repeated")); }
        }
    }
}

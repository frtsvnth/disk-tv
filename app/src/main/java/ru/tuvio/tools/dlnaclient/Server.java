package ru.tuvio.tools.dlnaclient;

import java.io.Serializable;

/** Discovered DLNA MediaServer on the LAN. */
public class Server implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String udn;
    public final String friendlyName;
    public final String baseUrl;          // control URL base
    public final String controlUrl;       // ContentDirectory control URL (resolved vs baseUrl)
    public final String address;          // host IP
    public final int port;
    public final String serviceType;

    public Server(String udn, String friendlyName, String baseUrl, String controlUrl,
                  String address, int port) {
        this(udn, friendlyName, baseUrl, controlUrl, address, port,
                "urn:schemas-upnp-org:service:ContentDirectory:1");
    }

    public Server(String udn, String friendlyName, String baseUrl, String controlUrl,
                  String address, int port, String serviceType) {
        this.serviceType = serviceType;
        this.udn = udn;
        this.friendlyName = friendlyName;
        this.baseUrl = baseUrl;
        this.controlUrl = controlUrl;
        this.address = address;
        this.port = port;
    }

    @Override
    public String toString() {
        return friendlyName;
    }
}

package io.github.massimilianopili.mcp.docker;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.docker")
public class DockerProperties {

    private String host;
    private boolean tlsVerify = true;
    private String certPath;
    private String apiVersion = "v1.45";

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public boolean isTlsVerify() { return tlsVerify; }
    public void setTlsVerify(boolean tlsVerify) { this.tlsVerify = tlsVerify; }

    public String getCertPath() { return certPath; }
    public void setCertPath(String certPath) { this.certPath = certPath; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    /** true se l'host e' un Unix socket (unix:///var/run/docker.sock) */
    public boolean isUnixSocket() {
        return host != null && host.startsWith("unix://");
    }

    /** Percorso del socket Unix, es: /var/run/docker.sock */
    public String getUnixSocketPath() {
        if (!isUnixSocket()) return null;
        return host.substring("unix://".length());
    }

    /**
     * Base URL per le chiamate API Docker, include la versione.
     * Per Unix socket: http://localhost/{apiVersion}
     * Per TCP: http(s)://host:port/{apiVersion}
     */
    public String getApiBase() {
        if (isUnixSocket()) {
            return "http://localhost/" + apiVersion;
        }
        String base = host;
        if (base.startsWith("tcp://")) {
            base = (tlsVerify ? "https://" : "http://") + base.substring("tcp://".length());
        }
        return stripTrailingSlash(base) + "/" + apiVersion;
    }

    private String stripTrailingSlash(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }
}

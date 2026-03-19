package io.github.massimilianopili.mcp.docker;

import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

import java.io.File;

@Configuration
@ConditionalOnProperty(name = "mcp.docker.host")
public class DockerConfig {

    private static final Logger log = LoggerFactory.getLogger(DockerConfig.class);

    @Bean(name = "dockerWebClient")
    public WebClient dockerWebClient(DockerProperties props) throws Exception {
        HttpClient httpClient;

        if (props.isUnixSocket()) {
            log.info("Docker WebClient: connessione via Unix socket {}", props.getUnixSocketPath());
            TcpClient tcpClient = TcpClient.create()
                    .remoteAddress(() -> new DomainSocketAddress(props.getUnixSocketPath()));
            httpClient = HttpClient.from(tcpClient);
        } else {
            String host = props.getHost();
            log.info("Docker WebClient: connessione via TCP {}", host);

            if (props.getCertPath() != null && !props.getCertPath().isBlank()) {
                File certDir = new File(props.getCertPath());
                SslContext sslContext = SslContextBuilder.forClient()
                        .keyManager(
                                new File(certDir, "cert.pem"),
                                new File(certDir, "key.pem"))
                        .trustManager(new File(certDir, "ca.pem"))
                        .build();
                httpClient = HttpClient.create()
                        .secure(spec -> spec.sslContext(sslContext));
            } else if (!props.isTlsVerify()) {
                SslContext sslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                httpClient = HttpClient.create()
                        .secure(spec -> spec.sslContext(sslContext));
            } else {
                httpClient = HttpClient.create();
            }
        }

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/json")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                        .build())
                .build();
    }
}

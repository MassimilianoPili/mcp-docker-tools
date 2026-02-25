package io.github.massimilianopili.mcp.docker;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "mcp.docker.host")
@EnableConfigurationProperties(DockerProperties.class)
@Import({DockerConfig.class,
         DockerContainerTools.class, DockerImageTools.class,
         DockerNetworkTools.class, DockerVolumeTools.class,
         DockerSystemTools.class, DockerComposeTools.class})
public class DockerToolsAutoConfiguration {
    // Tool registrati automaticamente da ReactiveToolAutoConfiguration di spring-ai-reactive-tools
}

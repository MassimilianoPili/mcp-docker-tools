# MCP Docker Tools

Spring Boot starter providing 41 MCP tools for Docker Engine management. Covers containers, images, networks, volumes, system info, and Compose projects via the Docker REST API.

## Installation

```xml
<dependency>
    <groupId>io.github.massimilianopili</groupId>
    <artifactId>mcp-docker-tools</artifactId>
    <version>0.0.1</version>
</dependency>
```

Requires Java 17+, Spring AI 1.0.0+, and [spring-ai-reactive-tools](https://github.com/MassimilianoPili/spring-ai-reactive-tools) 0.2.0+.

## Tools (41)

| Class | Count | Description |
|-------|-------|-------------|
| `DockerContainerTools` | 10 | List, inspect, start, stop, restart, remove, logs, stats, top, rename |
| `DockerImageTools` | 8 | List, inspect, pull, remove, tag, history, search (Hub) |
| `DockerNetworkTools` | 7 | CRUD networks, connect/disconnect containers |
| `DockerVolumeTools` | 4 | CRUD volumes |
| `DockerSystemTools` | 5 | Info, version, ping, disk usage, prune |
| `DockerComposeTools` | 2 | Detect and inspect Compose projects from container labels |

Remaining 5 tools distributed across helper methods.

## Configuration

```properties
# Required — enables all Docker tools
MCP_DOCKER_HOST=unix:///var/run/docker.sock    # or tcp://remote:2376

# Optional
MCP_DOCKER_TLS_VERIFY=true                     # default: true
MCP_DOCKER_CERT_PATH=/path/to/certs            # for TCP+TLS (ca.pem, cert.pem, key.pem)
MCP_DOCKER_API_VERSION=v1.45                   # default: v1.45
```

## How It Works

- Uses `@ReactiveTool` ([spring-ai-reactive-tools](https://github.com/MassimilianoPili/spring-ai-reactive-tools)) for async `Mono<T>` methods
- Auto-configured via `DockerToolsAutoConfiguration` with `@ConditionalOnProperty(name = "mcp.docker.host")`
- Supports Unix socket (`DomainSocketAddress`), TCP, and TCP+TLS connections via Netty
- 10MB response buffer for large outputs (inspect, logs)

## Requirements

- Java 17+
- Spring Boot 3.4+ with WebFlux
- Spring AI 1.0.0+
- spring-ai-reactive-tools 0.2.0+

## License

[MIT License](LICENSE)

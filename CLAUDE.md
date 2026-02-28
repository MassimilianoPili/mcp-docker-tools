# MCP Docker Tools

Spring Boot starter che fornisce tool MCP per la gestione di Docker Engine (container, immagini, network, volumi, sistema, compose) via Docker REST API. Pubblicato su Maven Central come `io.github.massimilianopili:mcp-docker-tools`.

## Build

```bash
# Build
/opt/maven/bin/mvn clean compile

# Install locale (senza GPG)
/opt/maven/bin/mvn clean install -Dgpg.skip=true

# Deploy su Maven Central
/opt/maven/bin/mvn clean deploy
```

Java 17+ richiesto. Maven: `/opt/maven/bin/mvn`.

## Struttura Progetto

```
src/main/java/io/github/massimilianopili/mcp/docker/
├── DockerProperties.java              # @ConfigurationProperties(prefix = "mcp.docker")
├── DockerConfig.java                  # WebClient bean (Unix socket + TCP/TLS)
├── DockerToolsAutoConfiguration.java  # Spring Boot auto-config
├── DockerContainerTools.java          # @ReactiveTool: lifecycle, logs, stats, processes
├── DockerImageTools.java              # @ReactiveTool: pull, tag, search, history
├── DockerNetworkTools.java            # @ReactiveTool: CRUD network, connect/disconnect
├── DockerVolumeTools.java             # @ReactiveTool: CRUD volumi
├── DockerSystemTools.java             # @ReactiveTool: info, version, ping, disk usage, prune
└── DockerComposeTools.java            # @ReactiveTool: detect compose projects da label container

src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## Tool (41 totali)

### DockerContainerTools (10)
- `docker_list_containers` — Lista container (running o tutti)
- `docker_inspect_container` — Dettaglio completo container
- `docker_start_container` — Avvia container fermo
- `docker_stop_container` — Ferma container (timeout configurabile)
- `docker_restart_container` — Riavvia container
- `docker_remove_container` — Elimina container (force, removeVolumes opzionali)
- `docker_get_container_logs` — Log (default 100 righe, timeout 60s)
- `docker_get_container_stats` — Snapshot risorse (CPU, memoria, rete)
- `docker_top_container` — Processi nel container
- `docker_rename_container` — Rinomina container

### DockerImageTools (8)
- `docker_list_images` — Lista immagini locali
- `docker_inspect_image` — Dettaglio immagine
- `docker_pull_image` — Download da registry (timeout 120s, default tag: latest)
- `docker_remove_image` — Elimina immagine (force opzionale)
- `docker_tag_image` — Aggiunge tag a immagine
- `docker_image_history` — Storico layer
- `docker_search_images` — Cerca su Docker Hub

### DockerNetworkTools (7)
- `docker_list_networks` — Lista network con conteggio container
- `docker_inspect_network` — Dettaglio con container connessi
- `docker_create_network` — Crea network (bridge, overlay, macvlan)
- `docker_remove_network` — Elimina network
- `docker_connect_container` — Connetti container a network
- `docker_disconnect_container` — Disconnetti container da network

### DockerVolumeTools (4)
- `docker_list_volumes` — Lista volumi
- `docker_inspect_volume` — Dettaglio volume
- `docker_create_volume` — Crea volume (driver: local, nfs, ecc.)
- `docker_remove_volume` — Elimina volume

### DockerSystemTools (5)
- `docker_system_info` — Info daemon (container count, versione, OS, CPU, memoria)
- `docker_version` — Versione Docker (API, engine, OS, arch)
- `docker_ping` — Health check
- `docker_disk_usage` — Utilizzo disco (container, immagini, volumi, build cache)
- `docker_prune_system` — Pulizia risorse inutilizzate (all=true rimuove tutte le immagini non usate)

### DockerComposeTools (2)
- `docker_list_compose_projects` — Rileva progetti compose da label container (`com.docker.compose.project`)
- `docker_get_compose_project` — Stato container di un progetto compose

## Pattern Chiave

- **@ReactiveTool** (spring-ai-reactive-tools): tutti i tool restituiscono `Mono<T>`.
- **Attivazione**: `@ConditionalOnProperty(name = "mcp.docker.host")`.
- **Connessione flessibile**: supporta Unix socket (`unix:///var/run/docker.sock`), TCP, TCP+TLS con certificati.
- **WebClient Netty**: `ReactorClientHttpConnector` con `DomainSocketAddress` per Unix socket.
- **TLS**: carica ca.pem, cert.pem, key.pem da `certPath`; opzione `skipTlsVerify` con `InsecureTrustManagerFactory`.
- **Buffer**: 10MB max in-memory per risposte grandi (inspect, logs).

## Configurazione

```properties
# Obbligatoria — abilita tutti i tool Docker
MCP_DOCKER_HOST=unix:///var/run/docker.sock    # oppure tcp://remote:2376

# Opzionali
MCP_DOCKER_TLS_VERIFY=true                     # default: true
MCP_DOCKER_CERT_PATH=/path/to/certs            # per TCP+TLS
MCP_DOCKER_API_VERSION=v1.45                    # default: v1.45
```

## Dipendenze

- Spring Boot 3.4.1 (spring-boot-autoconfigure, spring-boot-starter-webflux)
- Spring AI 1.0.0 (spring-ai-model)
- spring-ai-reactive-tools 0.2.0

## Maven Central

- GroupId: `io.github.massimilianopili`
- Plugin: `central-publishing-maven-plugin` v0.7.0
- Credenziali: Central Portal token in `~/.m2/settings.xml` (server id: `central`)

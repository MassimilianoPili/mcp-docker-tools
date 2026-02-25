package io.github.massimilianopili.mcp.docker;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@ConditionalOnProperty(name = "mcp.docker.host")
public class DockerSystemTools {

    private final WebClient webClient;
    private final DockerProperties props;

    public DockerSystemTools(
            @Qualifier("dockerWebClient") WebClient webClient,
            DockerProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "docker_system_info",
          description = "Recupera le informazioni di sistema del Docker Engine (versione, OS, container, immagini, storage)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> systemInfo() {
        return webClient.get()
                .uri(props.getApiBase() + "/info")
                .retrieve()
                .bodyToMono(Map.class)
                .map(info -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("containers", info.getOrDefault("Containers", 0));
                    result.put("containersRunning", info.getOrDefault("ContainersRunning", 0));
                    result.put("containersPaused", info.getOrDefault("ContainersPaused", 0));
                    result.put("containersStopped", info.getOrDefault("ContainersStopped", 0));
                    result.put("images", info.getOrDefault("Images", 0));
                    result.put("serverVersion", info.getOrDefault("ServerVersion", ""));
                    result.put("operatingSystem", info.getOrDefault("OperatingSystem", ""));
                    result.put("osType", info.getOrDefault("OSType", ""));
                    result.put("architecture", info.getOrDefault("Architecture", ""));
                    result.put("ncpu", info.getOrDefault("NCPU", 0));
                    result.put("memTotal", info.getOrDefault("MemTotal", 0));
                    result.put("driver", info.getOrDefault("Driver", ""));
                    result.put("dockerRootDir", info.getOrDefault("DockerRootDir", ""));
                    result.put("name", info.getOrDefault("Name", ""));
                    return result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero info sistema: " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_version",
          description = "Recupera la versione del Docker Engine e dei componenti")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> version() {
        return webClient.get()
                .uri(props.getApiBase() + "/version")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero versione: " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_ping",
          description = "Verifica se il Docker Engine e' raggiungibile")
    public Mono<Map<String, Object>> ping() {
        return webClient.get()
                .uri(props.getApiBase() + "/_ping")
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> Map.<String, Object>of("status", "OK".equals(body.trim()) ? "healthy" : body.trim(), "host", props.getHost()))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore ping Docker: " + e.getMessage(), "host", String.valueOf(props.getHost()))));
    }

    @ReactiveTool(name = "docker_disk_usage",
          description = "Mostra l'utilizzo disco di container, immagini, volumi e build cache (docker system df)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> diskUsage() {
        return webClient.get()
                .uri(props.getApiBase() + "/system/df")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore disk usage: " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_prune_system",
          description = "Pulisce le risorse Docker inutilizzate (container fermi, reti non usate, immagini dangling, build cache). ATTENZIONE: operazione distruttiva!")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> pruneSystem(
            @ToolParam(description = "true per rimuovere anche tutte le immagini inutilizzate (non solo dangling, default: false)", required = false) Boolean all) {
        boolean pruneAll = all != null && all;

        Mono<Map<String, Object>> pruneContainers = webClient.post()
                .uri(props.getApiBase() + "/containers/prune")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", e.getMessage())));

        Mono<Map<String, Object>> pruneNetworks = webClient.post()
                .uri(props.getApiBase() + "/networks/prune")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", e.getMessage())));

        String imageFilter = pruneAll ? "" : "?filters=%7B%22dangling%22%3A%5B%22true%22%5D%7D";
        Mono<Map<String, Object>> pruneImages = webClient.post()
                .uri(props.getApiBase() + "/images/prune" + imageFilter)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", e.getMessage())));

        Mono<Map<String, Object>> pruneBuild = webClient.post()
                .uri(props.getApiBase() + "/build/prune")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", e.getMessage())));

        return Mono.zip(pruneContainers, pruneNetworks, pruneImages, pruneBuild)
                .map(tuple -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("containers", tuple.getT1());
                    result.put("networks", tuple.getT2());
                    result.put("images", tuple.getT3());
                    result.put("buildCache", tuple.getT4());
                    return result;
                });
    }
}

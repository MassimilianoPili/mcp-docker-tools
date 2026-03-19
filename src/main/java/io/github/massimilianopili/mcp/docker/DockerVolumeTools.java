package io.github.massimilianopili.mcp.docker;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@ConditionalOnProperty(name = "mcp.docker.host")
public class DockerVolumeTools {

    private final WebClient webClient;
    private final DockerProperties props;

    public DockerVolumeTools(
            @Qualifier("dockerWebClient") WebClient webClient,
            DockerProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "docker_list_volumes",
          description = "Lists Docker volumes")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> listVolumes() {
        return webClient.get()
                .uri(props.getApiBase() + "/volumes")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    List<Map<String, Object>> volumes = (List<Map<String, Object>>) response.getOrDefault("Volumes", List.of());
                    result.put("volumes", volumes.stream().map(v -> {
                        Map<String, Object> vol = new LinkedHashMap<>();
                        vol.put("name", v.getOrDefault("Name", ""));
                        vol.put("driver", v.getOrDefault("Driver", ""));
                        vol.put("mountpoint", v.getOrDefault("Mountpoint", ""));
                        vol.put("scope", v.getOrDefault("Scope", ""));
                        vol.put("createdAt", v.getOrDefault("CreatedAt", ""));
                        return vol;
                    }).toList());
                    result.put("warnings", response.getOrDefault("Warnings", List.of()));
                    return result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore recupero volumi: " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_inspect_volume",
          description = "Retrieves details of a Docker volume")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> inspectVolume(
            @ToolParam(description = "Volume name") String name) {
        return webClient.get()
                .uri(props.getApiBase() + "/volumes/" + name)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore inspect volume " + name + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_create_volume",
          description = "Creates a new Docker volume")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createVolume(
            @ToolParam(description = "Volume name") String name,
            @ToolParam(description = "Driver: local (default), nfs, etc.", required = false) String driver) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Name", name);
        if (driver != null && !driver.isBlank()) {
            body.put("Driver", driver);
        }

        return webClient.post()
                .uri(props.getApiBase() + "/volumes/create")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione volume " + name + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_remove_volume",
          description = "Removes a Docker volume")
    public Mono<Map<String, Object>> removeVolume(
            @ToolParam(description = "Volume name") String name,
            @ToolParam(description = "Force removal (default: false)", required = false) Boolean force) {
        boolean f = force != null && force;
        return webClient.delete()
                .uri(props.getApiBase() + "/volumes/" + name + "?force=" + f)
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "volume", name, "deleted", true))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore rimozione volume " + name + ": " + e.getMessage())));
    }
}

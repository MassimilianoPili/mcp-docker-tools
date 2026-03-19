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
public class DockerContainerTools {

    private final WebClient webClient;
    private final DockerProperties props;

    public DockerContainerTools(
            @Qualifier("dockerWebClient") WebClient webClient,
            DockerProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "docker_list_containers",
          description = "Lists Docker containers (default: running only, all=true to include stopped)")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listContainers(
            @ToolParam(description = "true to include stopped containers (default: false)", required = false) Boolean all) {
        boolean showAll = all != null && all;
        return webClient.get()
                .uri(props.getApiBase() + "/containers/json?all=" + showAll)
                .retrieve()
                .bodyToMono(List.class)
                .map(containers -> ((List<Map<String, Object>>) containers).stream().map(c -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", ((String) c.getOrDefault("Id", "")).substring(0, Math.min(12, ((String) c.getOrDefault("Id", "")).length())));
                    List<String> names = (List<String>) c.getOrDefault("Names", List.of());
                    result.put("name", names.isEmpty() ? "" : names.get(0).replaceFirst("^/", ""));
                    result.put("image", c.getOrDefault("Image", ""));
                    result.put("state", c.getOrDefault("State", ""));
                    result.put("status", c.getOrDefault("Status", ""));
                    result.put("created", c.getOrDefault("Created", 0));
                    return result;
                }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero container: " + e.getMessage()))));
    }

    @ReactiveTool(name = "docker_inspect_container",
          description = "Retrieves full details of a Docker container")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> inspectContainer(
            @ToolParam(description = "Container ID or name") String id) {
        return webClient.get()
                .uri(props.getApiBase() + "/containers/" + id + "/json")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore inspect container " + id + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_start_container",
          description = "Starts a stopped Docker container")
    public Mono<Map<String, Object>> startContainer(
            @ToolParam(description = "Container ID or name") String id) {
        return webClient.post()
                .uri(props.getApiBase() + "/containers/" + id + "/start")
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "container", id, "action", "started"))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore avvio container " + id + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_stop_container",
          description = "Stops a running Docker container")
    public Mono<Map<String, Object>> stopContainer(
            @ToolParam(description = "Container ID or name") String id,
            @ToolParam(description = "Timeout in seconds before SIGKILL (default: 10)", required = false) Integer timeout) {
        int t = (timeout != null && timeout > 0) ? timeout : 10;
        return webClient.post()
                .uri(props.getApiBase() + "/containers/" + id + "/stop?t=" + t)
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "container", id, "action", "stopped"))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore stop container " + id + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_restart_container",
          description = "Restarts a Docker container")
    public Mono<Map<String, Object>> restartContainer(
            @ToolParam(description = "Container ID or name") String id,
            @ToolParam(description = "Timeout in seconds before SIGKILL (default: 10)", required = false) Integer timeout) {
        int t = (timeout != null && timeout > 0) ? timeout : 10;
        return webClient.post()
                .uri(props.getApiBase() + "/containers/" + id + "/restart?t=" + t)
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "container", id, "action", "restarted"))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore restart container " + id + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_remove_container",
          description = "Removes a Docker container")
    public Mono<Map<String, Object>> removeContainer(
            @ToolParam(description = "Container ID or name") String id,
            @ToolParam(description = "Force removal even if running (default: false)", required = false) Boolean force,
            @ToolParam(description = "Remove associated volumes (default: false)", required = false) Boolean removeVolumes) {
        boolean f = force != null && force;
        boolean v = removeVolumes != null && removeVolumes;
        return webClient.delete()
                .uri(props.getApiBase() + "/containers/" + id + "?force=" + f + "&v=" + v)
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "container", id, "deleted", true))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore rimozione container " + id + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_get_container_logs",
          description = "Retrieves logs from a Docker container. Non-TTY containers may include control characters.",
          timeoutMs = 60000)
    public Mono<String> getContainerLogs(
            @ToolParam(description = "Container ID or name") String id,
            @ToolParam(description = "Number of lines from the end (default: 100)", required = false) Integer tailLines,
            @ToolParam(description = "Show timestamps (default: false)", required = false) Boolean timestamps) {
        int tail = (tailLines != null && tailLines > 0) ? tailLines : 100;
        boolean ts = timestamps != null && timestamps;
        return webClient.get()
                .uri(props.getApiBase() + "/containers/" + id + "/logs?stdout=true&stderr=true&tail=" + tail + "&timestamps=" + ts)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("Errore recupero log container " + id + ": " + e.getMessage()));
    }

    @ReactiveTool(name = "docker_get_container_stats",
          description = "Retrieves resource usage statistics (CPU, memory, network) for a container (single snapshot)")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getContainerStats(
            @ToolParam(description = "Container ID or name") String id) {
        return webClient.get()
                .uri(props.getApiBase() + "/containers/" + id + "/stats?stream=false")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore stats container " + id + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_top_container",
          description = "Lists processes running inside a Docker container")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> topContainer(
            @ToolParam(description = "Container ID or name") String id) {
        return webClient.get()
                .uri(props.getApiBase() + "/containers/" + id + "/top")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore top container " + id + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_rename_container",
          description = "Renames a Docker container")
    public Mono<Map<String, Object>> renameContainer(
            @ToolParam(description = "Container ID or name") String id,
            @ToolParam(description = "New container name") String name) {
        return webClient.post()
                .uri(props.getApiBase() + "/containers/" + id + "/rename?name=" + name)
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "container", id, "newName", name))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore rename container " + id + ": " + e.getMessage())));
    }
}

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
public class DockerNetworkTools {

    private final WebClient webClient;
    private final DockerProperties props;

    public DockerNetworkTools(
            @Qualifier("dockerWebClient") WebClient webClient,
            DockerProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "docker_list_networks",
          description = "Elenca le reti Docker")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listNetworks() {
        return webClient.get()
                .uri(props.getApiBase() + "/networks")
                .retrieve()
                .bodyToMono(List.class)
                .map(networks -> ((List<Map<String, Object>>) networks).stream().map(n -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", ((String) n.getOrDefault("Id", "")).substring(0, Math.min(12, ((String) n.getOrDefault("Id", "")).length())));
                    result.put("name", n.getOrDefault("Name", ""));
                    result.put("driver", n.getOrDefault("Driver", ""));
                    result.put("scope", n.getOrDefault("Scope", ""));
                    Map<String, Object> containers = (Map<String, Object>) n.getOrDefault("Containers", Map.of());
                    result.put("containerCount", containers.size());
                    return result;
                }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero reti: " + e.getMessage()))));
    }

    @ReactiveTool(name = "docker_inspect_network",
          description = "Recupera i dettagli di una rete Docker con i container connessi")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> inspectNetwork(
            @ToolParam(description = "ID o nome della rete") String id) {
        return webClient.get()
                .uri(props.getApiBase() + "/networks/" + id)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore inspect rete " + id + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_create_network",
          description = "Crea una nuova rete Docker")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> createNetwork(
            @ToolParam(description = "Nome della rete") String name,
            @ToolParam(description = "Driver: bridge, overlay, macvlan (default: bridge)", required = false) String driver) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Name", name);
        body.put("Driver", (driver != null && !driver.isBlank()) ? driver : "bridge");

        return webClient.post()
                .uri(props.getApiBase() + "/networks/create")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", r.getOrDefault("Id", ""));
                    result.put("name", name);
                    result.put("created", true);
                    return (Map<String, Object>) result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore creazione rete " + name + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_remove_network",
          description = "Rimuove una rete Docker")
    public Mono<Map<String, Object>> removeNetwork(
            @ToolParam(description = "ID o nome della rete") String id) {
        return webClient.delete()
                .uri(props.getApiBase() + "/networks/" + id)
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "network", id, "deleted", true))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore rimozione rete " + id + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_connect_container",
          description = "Connette un container a una rete Docker")
    public Mono<Map<String, Object>> connectContainer(
            @ToolParam(description = "ID o nome della rete") String networkId,
            @ToolParam(description = "ID o nome del container") String containerId) {
        return webClient.post()
                .uri(props.getApiBase() + "/networks/" + networkId + "/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("Container", containerId))
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("network", networkId, "container", containerId, "connected", true))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore connessione container " + containerId + " a rete " + networkId + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_disconnect_container",
          description = "Disconnette un container da una rete Docker")
    public Mono<Map<String, Object>> disconnectContainer(
            @ToolParam(description = "ID o nome della rete") String networkId,
            @ToolParam(description = "ID o nome del container") String containerId,
            @ToolParam(description = "Forza disconnessione (default: false)", required = false) Boolean force) {
        boolean f = force != null && force;
        return webClient.post()
                .uri(props.getApiBase() + "/networks/" + networkId + "/disconnect")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("Container", containerId, "Force", f))
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("network", networkId, "container", containerId, "disconnected", true))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore disconnessione container " + containerId + " da rete " + networkId + ": " + e.getMessage())));
    }
}

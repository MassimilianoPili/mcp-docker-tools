package io.github.massimilianopili.mcp.docker;

import io.github.massimilianopili.ai.reactive.annotation.ReactiveTool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "mcp.docker.host")
public class DockerComposeTools {

    private final WebClient webClient;
    private final DockerProperties props;

    public DockerComposeTools(
            @Qualifier("dockerWebClient") WebClient webClient,
            DockerProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "docker_list_compose_projects",
          description = "Elenca i progetti Docker Compose rilevati dai container (basato sulla label com.docker.compose.project)")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listComposeProjects() {
        return webClient.get()
                .uri(props.getApiBase() + "/containers/json?all=true&filters=%7B%22label%22%3A%5B%22com.docker.compose.project%22%5D%7D")
                .retrieve()
                .bodyToMono(List.class)
                .map(containers -> {
                    List<Map<String, Object>> list = (List<Map<String, Object>>) containers;
                    Map<String, List<Map<String, Object>>> grouped = list.stream()
                            .collect(Collectors.groupingBy(c -> {
                                Map<String, String> labels = (Map<String, String>) c.getOrDefault("Labels", Map.of());
                                return labels.getOrDefault("com.docker.compose.project", "unknown");
                            }));
                    return grouped.entrySet().stream().map(entry -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("project", entry.getKey());
                        result.put("containers", entry.getValue().size());
                        long running = entry.getValue().stream()
                                .filter(c -> "running".equals(c.get("State")))
                                .count();
                        result.put("running", running);
                        result.put("stopped", entry.getValue().size() - running);
                        // Prendi la working dir dal primo container se disponibile
                        Map<String, String> firstLabels = (Map<String, String>) entry.getValue().get(0).getOrDefault("Labels", Map.of());
                        result.put("workingDir", firstLabels.getOrDefault("com.docker.compose.project.working_dir", ""));
                        return result;
                    }).toList();
                })
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero progetti Compose: " + e.getMessage()))));
    }

    @ReactiveTool(name = "docker_get_compose_project",
          description = "Mostra lo stato dei container di un progetto Docker Compose")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> getComposeProject(
            @ToolParam(description = "Nome del progetto Docker Compose") String projectName) {
        String filter = "%7B%22label%22%3A%5B%22com.docker.compose.project%3D" + projectName + "%22%5D%7D";
        return webClient.get()
                .uri(props.getApiBase() + "/containers/json?all=true&filters=" + filter)
                .retrieve()
                .bodyToMono(List.class)
                .map(containers -> ((List<Map<String, Object>>) containers).stream().map(c -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    String id = (String) c.getOrDefault("Id", "");
                    result.put("id", id.length() > 12 ? id.substring(0, 12) : id);
                    List<String> names = (List<String>) c.getOrDefault("Names", List.of());
                    result.put("name", names.isEmpty() ? "" : names.get(0).replaceFirst("^/", ""));
                    result.put("image", c.getOrDefault("Image", ""));
                    result.put("state", c.getOrDefault("State", ""));
                    result.put("status", c.getOrDefault("Status", ""));
                    Map<String, String> labels = (Map<String, String>) c.getOrDefault("Labels", Map.of());
                    result.put("service", labels.getOrDefault("com.docker.compose.service", ""));
                    result.put("containerNumber", labels.getOrDefault("com.docker.compose.container-number", ""));
                    return result;
                }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero progetto " + projectName + ": " + e.getMessage()))));
    }
}

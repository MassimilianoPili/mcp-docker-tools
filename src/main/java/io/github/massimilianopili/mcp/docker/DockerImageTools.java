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
public class DockerImageTools {

    private final WebClient webClient;
    private final DockerProperties props;

    public DockerImageTools(
            @Qualifier("dockerWebClient") WebClient webClient,
            DockerProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @ReactiveTool(name = "docker_list_images",
          description = "Lists local Docker images")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> listImages(
            @ToolParam(description = "true to include intermediate images (default: false)", required = false) Boolean all) {
        boolean showAll = all != null && all;
        return webClient.get()
                .uri(props.getApiBase() + "/images/json?all=" + showAll)
                .retrieve()
                .bodyToMono(List.class)
                .map(images -> ((List<Map<String, Object>>) images).stream().map(img -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    String id = (String) img.getOrDefault("Id", "");
                    result.put("id", id.length() > 19 ? id.substring(7, 19) : id);
                    result.put("repoTags", img.getOrDefault("RepoTags", List.of()));
                    result.put("size", img.getOrDefault("Size", 0));
                    result.put("created", img.getOrDefault("Created", 0));
                    return result;
                }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore recupero immagini: " + e.getMessage()))));
    }

    @ReactiveTool(name = "docker_inspect_image",
          description = "Retrieves details of a Docker image")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> inspectImage(
            @ToolParam(description = "Image name or ID") String name) {
        return webClient.get()
                .uri(props.getApiBase() + "/images/" + name + "/json")
                .retrieve()
                .bodyToMono(Map.class)
                .map(r -> (Map<String, Object>) r)
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore inspect immagine " + name + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_pull_image",
          description = "Pulls an image from a Docker registry",
          timeoutMs = 120000)
    public Mono<Map<String, Object>> pullImage(
            @ToolParam(description = "Image name, e.g.: nginx, redis, ubuntu") String image,
            @ToolParam(description = "Tag (default: latest)", required = false) String tag) {
        String t = (tag != null && !tag.isBlank()) ? tag : "latest";
        return webClient.post()
                .uri(props.getApiBase() + "/images/create?fromImage=" + image + "&tag=" + t)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("image", image + ":" + t);
                    result.put("status", "pulled");
                    result.put("details", body.lines().reduce((a, b) -> b).orElse(""));
                    return (Map<String, Object>) result;
                })
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore pull immagine " + image + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_remove_image",
          description = "Removes a local Docker image")
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> removeImage(
            @ToolParam(description = "Image name or ID") String name,
            @ToolParam(description = "Force removal (default: false)", required = false) Boolean force) {
        boolean f = force != null && force;
        return webClient.delete()
                .uri(props.getApiBase() + "/images/" + name + "?force=" + f)
                .retrieve()
                .bodyToMono(List.class)
                .map(r -> Map.<String, Object>of("image", name, "deleted", true, "details", r))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore rimozione immagine " + name + ": " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_tag_image",
          description = "Tags a Docker image")
    public Mono<Map<String, Object>> tagImage(
            @ToolParam(description = "Source image (name or ID)") String source,
            @ToolParam(description = "Target repository, e.g.: myrepo/myimage") String repo,
            @ToolParam(description = "Tag, e.g.: v1.0") String tag) {
        return webClient.post()
                .uri(props.getApiBase() + "/images/" + source + "/tag?repo=" + repo + "&tag=" + tag)
                .retrieve()
                .toBodilessEntity()
                .map(r -> Map.<String, Object>of("status", r.getStatusCode().value(), "source", source, "tagged", repo + ":" + tag))
                .onErrorResume(e -> Mono.just(Map.of("error", "Errore tag immagine: " + e.getMessage())));
    }

    @ReactiveTool(name = "docker_image_history",
          description = "Shows the layer history of a Docker image")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> imageHistory(
            @ToolParam(description = "Image name or ID") String name) {
        return webClient.get()
                .uri(props.getApiBase() + "/images/" + name + "/history")
                .retrieve()
                .bodyToMono(List.class)
                .map(history -> ((List<Map<String, Object>>) history).stream().map(h -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("id", h.getOrDefault("Id", ""));
                    result.put("created", h.getOrDefault("Created", 0));
                    result.put("createdBy", h.getOrDefault("CreatedBy", ""));
                    result.put("size", h.getOrDefault("Size", 0));
                    result.put("tags", h.getOrDefault("Tags", List.of()));
                    return result;
                }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore history immagine " + name + ": " + e.getMessage()))));
    }

    @ReactiveTool(name = "docker_search_images",
          description = "Searches for images on Docker Hub")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> searchImages(
            @ToolParam(description = "Search term") String term) {
        return webClient.get()
                .uri(props.getApiBase() + "/images/search?term=" + term)
                .retrieve()
                .bodyToMono(List.class)
                .map(results -> ((List<Map<String, Object>>) results).stream().map(r -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("name", r.getOrDefault("name", ""));
                    result.put("description", r.getOrDefault("description", ""));
                    result.put("starCount", r.getOrDefault("star_count", 0));
                    result.put("official", r.getOrDefault("is_official", false));
                    result.put("automated", r.getOrDefault("is_automated", false));
                    return result;
                }).toList())
                .onErrorResume(e -> Mono.just(List.of(Map.of("error", "Errore ricerca immagini: " + e.getMessage()))));
    }
}

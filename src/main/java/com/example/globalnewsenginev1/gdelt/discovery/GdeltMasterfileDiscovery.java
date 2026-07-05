package com.example.globalnewsenginev1.gdelt.discovery;

import com.example.globalnewsenginev1.gdelt.GdeltDataset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GdeltMasterfileDiscovery {

    private static final Pattern GDELT_FILE_PATTERN =
            Pattern.compile(".*/?(\\d{14})\\.(export\\.CSV\\.zip|mentions\\.CSV\\.zip|gkg\\.csv\\.zip)$");
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final HttpClient httpClient;
    private final URI manifestUri;

    @Autowired
    GdeltMasterfileDiscovery(@Value("${gdelt.manifest-url}") URI manifestUri) {
        this(HttpClient.newHttpClient(), manifestUri);
    }

    public GdeltMasterfileDiscovery(HttpClient httpClient, URI manifestUri) {
        this.httpClient = httpClient;
        this.manifestUri = manifestUri;
    }

    List<GdeltCompleteWindow> discoverCompleteWindows() {
        HttpRequest request = HttpRequest.newBuilder(manifestUri).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "GDELT masterfile download failed with HTTP " + response.statusCode() + ": " + manifestUri);
            }
            return parseCompleteWindows(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read GDELT masterfile: " + manifestUri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading GDELT masterfile: " + manifestUri, exception);
        }
    }

    public List<GdeltCompleteWindow> discoverLatestCompleteWindows(int limit) {
        List<GdeltCompleteWindow> windows = discoverCompleteWindows();
        if (limit <= 0 || windows.size() <= limit) {
            return windows;
        }
        return windows.subList(windows.size() - limit, windows.size());
    }

    public List<GdeltCompleteWindow> parseCompleteWindows(String masterfileContent) {
        Map<Instant, EnumMap<GdeltDataset, URI>> filesByTimestamp = new java.util.HashMap<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(masterfileContent))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line).ifPresent(file ->
                        filesByTimestamp
                                .computeIfAbsent(file.sourceTimestamp(), ignored -> new EnumMap<>(GdeltDataset.class))
                                .put(file.dataset(), file.uri()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse GDELT masterfile", exception);
        }

        return filesByTimestamp.entrySet().stream()
                .filter(entry -> entry.getValue().keySet().containsAll(List.of(GdeltDataset.values())))
                .map(entry -> new GdeltCompleteWindow(entry.getKey(), Map.copyOf(entry.getValue())))
                .sorted(java.util.Comparator.comparing(GdeltCompleteWindow::sourceTimestamp))
                .toList();
    }

    private java.util.Optional<DiscoveredFile> parseLine(String line) {
        String trimmedLine = line.trim();
        if (trimmedLine.isEmpty()) {
            return java.util.Optional.empty();
        }

        String[] columns = trimmedLine.split("\\s+");
        String url = columns[columns.length - 1];
        Matcher matcher = GDELT_FILE_PATTERN.matcher(url);
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }

        try {
            Instant sourceTimestamp = LocalDateTime.parse(matcher.group(1), FILE_TIMESTAMP).toInstant(ZoneOffset.UTC);
            return GdeltDataset.fromFileSuffix(matcher.group(2))
                    .map(dataset -> new DiscoveredFile(sourceTimestamp, dataset, URI.create(url)));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private record DiscoveredFile(Instant sourceTimestamp, GdeltDataset dataset, URI uri) {
    }
}

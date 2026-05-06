package com.example.globalnewsenginev1.gdelt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class GdeltManifestClient {

    private final HttpClient httpClient;
    private final URI manifestUri;
    private final long manifestTailBytes;

    public GdeltManifestClient(
            @Value("${gdelt.manifest-url}") URI manifestUri,
            @Value("${gdelt.manifest-tail-bytes:5242880}") long manifestTailBytes
    ) {
        this.manifestUri = manifestUri;
        this.manifestTailBytes = manifestTailBytes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String fetchMasterFileList() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(manifestUri)
                .timeout(Duration.ofSeconds(60))
                .header("Range", "bytes=-" + manifestTailBytes)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 206) {
            throw new IOException("GDELT manifest request failed with HTTP " + response.statusCode());
        }

        return response.body();
    }
}

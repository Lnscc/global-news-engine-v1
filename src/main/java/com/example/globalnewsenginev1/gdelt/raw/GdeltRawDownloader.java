package com.example.globalnewsenginev1.gdelt.raw;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
class GdeltRawDownloader {

    private final HttpClient httpClient;

    GdeltRawDownloader() {
        this(HttpClient.newHttpClient());
    }

    GdeltRawDownloader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    GdeltRawImporter.ImportPayload download(String sourceUrl, GdeltZipContentHandler contentHandler) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl)).GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IllegalStateException("GDELT download failed with HTTP " + response.statusCode() + ": " + sourceUrl);
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream body = response.body();
                 DigestInputStream digestStream = new DigestInputStream(body, digest);
                 ZipInputStream zipStream = new ZipInputStream(digestStream, StandardCharsets.UTF_8)) {
                ZipEntry entry = zipStream.getNextEntry();
                if (entry == null) {
                    throw new IllegalStateException("GDELT ZIP file is empty: " + sourceUrl);
                }
                long rowCount = contentHandler.handle(zipStream);
                drain(digestStream);
                return new GdeltRawImporter.ImportPayload(rowCount, HexFormat.of().formatHex(digest.digest()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read GDELT file: " + sourceUrl, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading GDELT file: " + sourceUrl, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void drain(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        while (inputStream.read(buffer) != -1) {
            // Drain the compressed response so the checksum covers the complete ZIP file.
        }
    }

    interface GdeltZipContentHandler {

        long handle(InputStream inputStream) throws IOException;
    }
}

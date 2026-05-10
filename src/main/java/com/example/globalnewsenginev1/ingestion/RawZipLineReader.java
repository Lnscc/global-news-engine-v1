package com.example.globalnewsenginev1.ingestion;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class RawZipLineReader {

    public int readLines(Path zipPath, BiConsumer<Long, String> rowConsumer) throws IOException {
        int rowsRead = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(java.nio.file.Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipInputStream.getNextEntry();
            if (entry == null) {
                throw new IOException("Zip file is empty: " + zipPath);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(zipInputStream, StandardCharsets.UTF_8));
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                rowConsumer.accept(lineNumber, line);
                rowsRead++;
            }
        }

        return rowsRead;
    }
}

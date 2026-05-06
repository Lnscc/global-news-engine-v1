package com.example.globalnewsenginev1.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class RawFileStorage {

    private final Path rootDirectory;

    public RawFileStorage(@Value("${ingestion.raw-storage-dir:data/raw}") Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public Path pathFor(SourceBatch batch, RawSourceFile file) {
        return rootDirectory
                .resolve(batch.getSource().toLowerCase())
                .resolve(batch.getExternalBatchId())
                .resolve(file.getFileType() + ".zip")
                .toAbsolutePath()
                .normalize();
    }
}

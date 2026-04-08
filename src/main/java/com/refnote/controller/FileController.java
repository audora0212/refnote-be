package com.refnote.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/files")
@Profile("dev")
public class FileController {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping("/{userId}/{filename}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String userId,
            @PathVariable String filename) {

        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path path = basePath.resolve(userId).resolve(filename).normalize();

        if (!path.startsWith(basePath)) {
            return ResponseEntity.status(403).build();
        }

        Resource resource = new FileSystemResource(path);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }
}

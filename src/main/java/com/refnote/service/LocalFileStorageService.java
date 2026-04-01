package com.refnote.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
@Profile("dev")
public class LocalFileStorageService implements FileStorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public String upload(MultipartFile file, Long userId, Long documentId) {
        try {
            Path dir = Paths.get(uploadDir, userId.toString()).toAbsolutePath();
            Files.createDirectories(dir);

            String filename = documentId + "_" + file.getOriginalFilename();
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target);

            String key = userId + "/" + filename;
            log.info("로컬 파일 저장 완료: {}", target);
            return key;
        } catch (IOException e) {
            log.error("로컬 파일 저장 실패", e);
            throw new RuntimeException("파일 저장에 실패했습니다.", e);
        }
    }

    @Override
    public String getFileUrl(String key) {
        return "/api/files/" + key;
    }

    @Override
    public InputStream download(String key) {
        try {
            Path path = Paths.get(uploadDir).toAbsolutePath().resolve(key);
            return new FileInputStream(path.toFile());
        } catch (IOException e) {
            log.error("로컬 파일 다운로드 실패: {}", key, e);
            throw new RuntimeException("파일을 찾을 수 없습니다.", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path path = Paths.get(uploadDir).toAbsolutePath().resolve(key);
            Files.deleteIfExists(path);
            log.info("로컬 파일 삭제 완료: {}", key);
        } catch (IOException e) {
            log.error("로컬 파일 삭제 실패: {}", key, e);
        }
    }
}

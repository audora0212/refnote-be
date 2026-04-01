package com.refnote.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorageService {

    String upload(MultipartFile file, Long userId, Long documentId);

    String getFileUrl(String key);

    InputStream download(String key);

    void delete(String key);
}

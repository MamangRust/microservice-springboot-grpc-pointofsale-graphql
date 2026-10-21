package com.sanedge.pointofsale.gateway.controller;

import java.io.File;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sanedge.pointofsale.gateway.service.FileService;
import com.sanedge.pointofsale.gateway.service.FolderService;

import lombok.RequiredArgsConstructor;

/**
 * The only remaining REST endpoint: GraphQL has no native multipart upload, so
 * files are stored here and the resulting path is fed to mutations as a String
 * (e.g. {@code createProduct(input:{ imageProduct: "<path>" })}).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upload")
public class UploadController {

    private final FileService fileService;
    private final FolderService folderService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "product") String folder,
            @RequestParam(value = "key", required = false) String key) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "file is required"));
        }
        String basePath = "static" + File.separator + folder;
        String folderName = (key == null || key.isBlank()) ? "misc" : key.replace(" ", "_");
        String folderPath = folderService.createFolder(basePath, folderName);
        if (folderPath == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "failed to create upload folder"));
        }
        String filePath = folderPath + File.separator + file.getOriginalFilename();
        String saved = fileService.createFileImage(file, filePath);
        if (saved == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "failed to store file"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "success",
                "message", "file uploaded",
                "data", Map.of("path", saved)));
    }
}

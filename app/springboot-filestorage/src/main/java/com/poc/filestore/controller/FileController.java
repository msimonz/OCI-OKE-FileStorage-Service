package com.poc.filestore.controller;

import com.poc.filestore.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@jakarta.servlet.annotation.MultipartConfig
public class FileController {

    private final FileService fileService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listFiles() {
        List<Map<String, Object>> files = fileService.listAll();
        return ResponseEntity.ok(Map.of("files", files, "total", files.size()));
    }

    @PostMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @PathVariable String filename,
            HttpServletRequest request) throws Exception {
        
        jakarta.servlet.http.Part part = request.getPart("file");
        if (part == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se encontró el campo 'file'"));
        }
        
        Path dest = java.nio.file.Paths.get(fileService.getStoragePath()).resolve(filename);
        try (java.io.InputStream is = part.getInputStream()) {
            java.nio.file.Files.copy(is, dest);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Archivo subido", "filename", filename));
    }

    @PutMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> updateFile(
            @PathVariable String filename,
            HttpServletRequest request) throws Exception {
        
        jakarta.servlet.http.Part part = request.getPart("file");
        if (part == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se encontró el campo 'file'"));
        }
        
        Path dest = java.nio.file.Paths.get(fileService.getStoragePath()).resolve(filename);
        try (java.io.InputStream is = part.getInputStream()) {
            java.nio.file.Files.copy(is, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return ResponseEntity.ok(Map.of("message", "Archivo actualizado", "filename", filename));
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        Resource resource = fileService.load(filename);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource);
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String filename) {
        return ResponseEntity.ok(fileService.delete(filename));
    }

    @GetMapping("/{filename}/info")
    public ResponseEntity<Map<String, Object>> fileInfo(@PathVariable String filename) {
        return ResponseEntity.ok(fileService.info(filename));
    }
}
package com.poc.filestore.controller;

import com.poc.filestore.service.SftpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final SftpService sftpService;

    // ── Listar archivos ───────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<Map<String, Object>> listFiles() throws Exception {
        List<String> files = sftpService.listFiles();
        return ResponseEntity.ok(Map.of(
            "files", files,
            "total", files.size()
        ));
    }

    // ── Subir archivo (CREATE) ────────────────────────────────────────
    @PostMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @PathVariable String filename,
            HttpServletRequest request) throws Exception {
        try (InputStream is = request.getInputStream()) {
            sftpService.uploadFile(filename, is);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of(
                "message", "Archivo subido exitosamente al SFTP → FileStorage",
                "filename", filename
            ));
    }

    // ── Descargar archivo (READ) ──────────────────────────────────────
    @GetMapping("/{filename}")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable String filename) throws Exception {
        InputStream is = sftpService.downloadFile(filename);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new InputStreamResource(is));
    }

    // ── Eliminar archivo (DELETE) ─────────────────────────────────────
    @DeleteMapping("/{filename}")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable String filename) throws Exception {
        sftpService.deleteFile(filename);
        return ResponseEntity.ok(Map.of(
            "message", "Archivo '" + filename + "' eliminado exitosamente"
        ));
    }

    // ── Health check ──────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "sftp", "sftp-server-svc:22"
        ));
    }
}
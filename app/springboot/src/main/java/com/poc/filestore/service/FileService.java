package com.poc.filestore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileService {

    @Value("${filestore.path}")
    private String storagePath;

    private Path rootPath;

    // ── Inicialización ────────────────────────────────────────────────
    @PostConstruct
    public void init() {
        rootPath = Paths.get(storagePath);
        try {
            Files.createDirectories(rootPath);
            log.info("Storage path inicializado: {}", rootPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo inicializar el directorio de almacenamiento", e);
        }
    }

    // ── CREATE ────────────────────────────────────────────────────────
    public Map<String, Object> save(String filename, MultipartFile file) {
        Path dest = rootPath.resolve(filename);

        if (Files.exists(dest)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El archivo '" + filename + "' ya existe. Usa PUT para actualizarlo."
            );
        }

        try {
            Files.copy(file.getInputStream(), dest);
            log.info("Archivo creado: {}", filename);
        } catch (IOException e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error guardando archivo: " + e.getMessage()
            );
        }

        return buildFileMeta(dest, filename, "Archivo subido exitosamente");
    }

    // ── READ — descargar ──────────────────────────────────────────────
    public Resource load(String filename) {
        Path file = rootPath.resolve(filename);

        if (!Files.exists(file)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Archivo '" + filename + "' no encontrado"
            );
        }

        try {
            Resource resource = new UrlResource(file.toUri());
            if (resource.isReadable()) return resource;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Archivo no legible");
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // ── READ ALL — listar ─────────────────────────────────────────────
    public List<Map<String, Object>> listAll() {
        try {
            return Files.list(rootPath)
                .filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        return Map.<String, Object>of(
                            "filename", path.getFileName().toString(),
                            "size_bytes", Files.size(path),
                            "modified_at", LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(path).toInstant(),
                                ZoneOffset.UTC
                            ).toString()
                        );
                    } catch (IOException e) {
                        return Map.<String, Object>of("filename", path.getFileName().toString());
                    }
                })
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error listando archivos: " + e.getMessage()
            );
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────
    public Map<String, Object> update(String filename, MultipartFile file) {
        Path dest = rootPath.resolve(filename);

        if (!Files.exists(dest)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Archivo '" + filename + "' no existe. Usa POST para crearlo."
            );
        }

        try {
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Archivo actualizado: {}", filename);
        } catch (IOException e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error actualizando archivo: " + e.getMessage()
            );
        }

        return buildFileMeta(dest, filename, "Archivo actualizado exitosamente");
    }

    // ── DELETE ────────────────────────────────────────────────────────
    public Map<String, Object> delete(String filename) {
        Path file = rootPath.resolve(filename);

        if (!Files.exists(file)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Archivo '" + filename + "' no encontrado"
            );
        }

        try {
            Files.delete(file);
            log.info("Archivo eliminado: {}", filename);
        } catch (IOException e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error eliminando archivo: " + e.getMessage()
            );
        }

        return Map.of("message", "Archivo '" + filename + "' eliminado exitosamente");
    }

    // ── INFO — metadata ───────────────────────────────────────────────
    public Map<String, Object> info(String filename) {
        Path file = rootPath.resolve(filename);

        if (!Files.exists(file)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Archivo '" + filename + "' no encontrado"
            );
        }

        return buildFileMeta(file, filename, null);
    }

    public String getStoragePath() {
        return storagePath;
    }

    // ── Helper ────────────────────────────────────────────────────────
    private Map<String, Object> buildFileMeta(Path path, String filename, String message) {
        try {
            var attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
            var builder = new java.util.LinkedHashMap<String, Object>();

            if (message != null) builder.put("message", message);
            builder.put("filename", filename);
            builder.put("size_bytes", attrs.size());
            builder.put("created_at", LocalDateTime.ofInstant(
                attrs.creationTime().toInstant(), ZoneOffset.UTC).toString());
            builder.put("modified_at", LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(), ZoneOffset.UTC).toString());

            return builder;
        } catch (IOException e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()
            );
        }
    }
}
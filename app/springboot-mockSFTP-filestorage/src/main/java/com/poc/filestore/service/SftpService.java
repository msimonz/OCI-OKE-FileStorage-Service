package com.poc.filestore.service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.poc.filestore.config.SftpConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class SftpService {

    private final SftpConfig sftpConfig;

    /**
     * Sube un archivo al SFTP.
     * El SFTP tiene montado el PVC, así que el archivo
     * termina en el OCI File Storage automáticamente.
     */
    public void uploadFile(String filename, InputStream inputStream) throws Exception {
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            log.info("Conectando al SFTP {}:{}", sftpConfig.getHost(), sftpConfig.getPort());

            // Crear sesión JSch
            JSch jsch = new JSch();
            session = jsch.getSession(
                sftpConfig.getUsername(),
                sftpConfig.getHost(),
                sftpConfig.getPort()
            );
            session.setPassword(sftpConfig.getPassword());

            // Deshabilitar verificación de host key (POC)
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(30000);

            log.info("Sesión SSH establecida");

            // Abrir canal SFTP
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            log.info("Canal SFTP abierto");

            // Navegar al directorio remoto
            channelSftp.cd(sftpConfig.getRemoteDir());

            // Subir el archivo
            String remotePath = sftpConfig.getRemoteDir() + "/" + filename;
            channelSftp.put(inputStream, filename);

            log.info("Archivo '{}' subido exitosamente al SFTP en {}", filename, remotePath);

        } catch (Exception e) {
            log.error("Error subiendo archivo al SFTP: {}", e.getMessage());
            throw new RuntimeException("Error al subir archivo al SFTP: " + e.getMessage(), e);
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            log.info("Conexión SFTP cerrada");
        }
    }

    /**
     * Descarga un archivo del SFTP.
     */
    public InputStream downloadFile(String filename) throws Exception {
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(
                sftpConfig.getUsername(),
                sftpConfig.getHost(),
                sftpConfig.getPort()
            );
            session.setPassword(sftpConfig.getPassword());

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(30000);

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            String remotePath = sftpConfig.getRemoteDir() + "/" + filename;
            log.info("Descargando archivo '{}' del SFTP", remotePath);

            return channelSftp.get(remotePath);

        } catch (Exception e) {
            log.error("Error descargando archivo del SFTP: {}", e.getMessage());
            throw new RuntimeException("Error al descargar archivo del SFTP: " + e.getMessage(), e);
        }
        // Nota: no cerramos session/channel aquí porque el InputStream
        // aún necesita la conexión abierta para leer
    }

    /**
     * Elimina un archivo del SFTP.
     */
    public void deleteFile(String filename) throws Exception {
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(
                sftpConfig.getUsername(),
                sftpConfig.getHost(),
                sftpConfig.getPort()
            );
            session.setPassword(sftpConfig.getPassword());

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(30000);

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            String remotePath = sftpConfig.getRemoteDir() + "/" + filename;
            channelSftp.rm(remotePath);

            log.info("Archivo '{}' eliminado del SFTP", remotePath);

        } finally {
            if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    /**
     * Lista archivos en el directorio remoto del SFTP.
     */
    public java.util.List<String> listFiles() throws Exception {
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(
                sftpConfig.getUsername(),
                sftpConfig.getHost(),
                sftpConfig.getPort()
            );
            session.setPassword(sftpConfig.getPassword());

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(30000);

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            java.util.Vector<ChannelSftp.LsEntry> entries =
                channelSftp.ls(sftpConfig.getRemoteDir());

            return entries.stream()
                .filter(e -> !e.getAttrs().isDir())
                .map(ChannelSftp.LsEntry::getFilename)
                .collect(java.util.stream.Collectors.toList());

        } finally {
            if (channelSftp != null && channelSftp.isConnected()) channelSftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }
}
package es.grupo18.urbanpulse.attachment.service;

import java.io.IOException;
import java.io.UncheckedIOException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import es.grupo18.urbanpulse.shared.storage.StorageProperties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Acceso al bucket privado; nunca genera URLs públicas ni revela claves en
 * errores HTTP.
 */
@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    /**
     * Almacena una imagen saneada.
     *
     * @param storageKey  clave opaca
     * @param data        contenido acotado
     * @param contentType MIME validado
     */
    public void upload(
            String storageKey,
            byte[] data,
            String contentType) {

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .contentType(contentType)
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromBytes(data));
    }

    /**
     * Recupera como máximo 10 MiB; aborta incluso si el servidor declara una
     * longitud falsa.
     *
     * @param storageKey clave previamente autorizada
     * @return contenido acotado
     * @throws IllegalStateException si excede el límite
     * @throws UncheckedIOException  si falla la lectura
     */
    public byte[] download(String storageKey) {

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build();

        try (var response = s3Client.getObject(request)) {
            byte[] data = response.readNBytes(ImageSanitizer.MAX_BYTES + 1);
            if (data.length > ImageSanitizer.MAX_BYTES) {
                response.abort();
                throw new IllegalStateException("Object exceeds allowed size");
            }
            return data;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Elimina únicamente objetos de una compensación; DELETE HTTP no invoca este
     * método.
     *
     * @param storageKey clave opaca del objeto sobrante
     */
    public void delete(String storageKey) {

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build();

        s3Client.deleteObject(request);
    }
}

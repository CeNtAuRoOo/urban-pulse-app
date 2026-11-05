
package es.grupo18.urbanpulse.service;

import org.springframework.stereotype.Service;

import es.grupo18.urbanpulse.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    /**
     * Uploads an image to Neon Object Storage.
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
                RequestBody.fromBytes(data)
        );
    }

    /**
     * Downloads an image from Neon Object Storage.
     */
    public byte[] download(String storageKey) {

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build();

        ResponseBytes<GetObjectResponse> response =
                s3Client.getObjectAsBytes(request);

        return response.asByteArray();
    }

    /**
     * Deletes an image from Neon Object Storage.
     */
    public void delete(String storageKey) {

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build();

        s3Client.deleteObject(request);
    }
}

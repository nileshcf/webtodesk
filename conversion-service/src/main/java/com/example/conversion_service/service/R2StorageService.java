package com.example.conversion_service.service;

import com.example.conversion_service.config.R2Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class R2StorageService {

    private final S3Client s3Client;
    private final R2Properties r2Props;

    /**
     * Upload a local file to R2. Returns the public download URL.
     */
    public String uploadFile(Path localFile, String r2Key, String contentType) {
        log.info("Uploading {} -> r2://{}/{}", localFile.getFileName(), r2Props.getBucket(), r2Key);

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(r2Props.getBucket())
                .key(r2Key)
                .contentType(contentType)
                .contentDisposition("attachment; filename=\"" + localFile.getFileName() + "\"")
                .build();

        s3Client.putObject(req, RequestBody.fromFile(localFile));

        String publicUrl = r2Props.getPublicUrl() + "/" + r2Key;
        log.info("Upload complete: {}", publicUrl);
        return publicUrl;
    }

    /**
     * Upload from an InputStream (e.g. GitHub artifact download).
     */
    public String uploadStream(InputStream inputStream, long contentLength, String r2Key, String contentType, String fileName) {
        log.info("Uploading stream -> r2://{}/{}", r2Props.getBucket(), r2Key);

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(r2Props.getBucket())
                .key(r2Key)
                .contentType(contentType)
                .contentDisposition("attachment; filename=\"" + fileName + "\"")
                .build();

        s3Client.putObject(req, RequestBody.fromInputStream(inputStream, contentLength));

        String publicUrl = r2Props.getPublicUrl() + "/" + r2Key;
        log.info("Upload complete: {}", publicUrl);
        return publicUrl;
    }

    /**
     * Delete a file from R2.
     */
    public void deleteFile(String r2Key) {
        log.info("Deleting r2://{}/{}", r2Props.getBucket(), r2Key);
        DeleteObjectRequest req = DeleteObjectRequest.builder()
                .bucket(r2Props.getBucket())
                .key(r2Key)
                .build();
        s3Client.deleteObject(req);
    }

    /**
     * Check if a key exists in R2.
     */
    public boolean exists(String r2Key) {
        try {
            HeadObjectRequest req = HeadObjectRequest.builder()
                    .bucket(r2Props.getBucket())
                    .key(r2Key)
                    .build();
            s3Client.headObject(req);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * Get public URL for an R2 key.
     */
    public String getPublicUrl(String r2Key) {
        return r2Props.getPublicUrl() + "/" + r2Key;
    }
}

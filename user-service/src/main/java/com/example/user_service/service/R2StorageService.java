package com.example.user_service.service;

import com.example.user_service.config.R2Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class R2StorageService {

    private final S3Client s3Client;
    private final R2Properties r2Props;

    /**
     * Upload an avatar stream to R2. Returns the secure public CDN URL.
     */
    public String uploadAvatar(MultipartFile file, String userId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        // avatars/user-id_timestamp.ext to break cache instantly on UI reload
        String r2Key = "avatars/" + userId + "_" + System.currentTimeMillis() + extension;

        log.info("Uploading avatar stream -> r2://{}/{}", r2Props.getBucket(), r2Key);

        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(r2Props.getBucket())
                .key(r2Key)
                .contentType(file.getContentType())
                .contentDisposition("inline; filename=\"" + r2Key + "\"")
                .build();

        s3Client.putObject(req, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        String publicUrl = r2Props.getPublicUrl() + "/" + r2Key;
        log.info("Upload complete: {}", publicUrl);
        return publicUrl;
    }
}

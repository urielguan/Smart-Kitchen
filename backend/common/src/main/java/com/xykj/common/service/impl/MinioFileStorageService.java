package com.xykj.common.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.xykj.common.config.MinioConfig;
import com.xykj.common.exception.BizException;
import com.xykj.common.service.FileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * MinIO 文件存储服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService, CommandLineRunner {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    public void run(String... args) {
        initBucket();
    }

    /**
     * 初始化 bucket：不存在则创建，并设置公开读策略
     */
    private void initBucket() {
        try {
            String bucket = minioConfig.getBucketName();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                // 设置公开读策略
                String policy = String.format(
                    "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}",
                    bucket);
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
                log.info("MinIO bucket '{}' 创建成功并设置公开读策略", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败: {}", e.getMessage());
        }
    }

    @Override
    public String upload(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (StrUtil.isNotBlank(originalFilename) && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 生成对象路径：directory/yyyy/MM/dd/uuid.ext
        String datePath = LocalDate.now().format(DATE_FORMAT);
        String objectName = directory + "/" + datePath + "/" + IdUtil.fastSimpleUUID() + suffix;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("文件上传到MinIO失败: {}", e.getMessage());
            throw new BizException("文件上传失败，请稍后重试");
        }

        // 返回完整访问 URL
        String url = minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/" + objectName;
        log.info("文件上传成功: {}", url);
        return url;
    }

    @Override
    public void delete(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return;
        }

        try {
            String objectName = extractObjectName(fileUrl);
            if (StrUtil.isBlank(objectName)) {
                return;
            }

            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
            log.info("文件删除成功: {}", objectName);
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage());
        }
    }

    @Override
    public StoredFile download(String fileUrl) {
        String objectName = extractObjectName(fileUrl);
        if (StrUtil.isBlank(objectName)) {
            throw BizException.badRequest("附件地址无效");
        }

        try {
            var stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
            return new StoredFile(inputStream, stat.contentType(), stat.size());
        } catch (Exception e) {
            log.error("文件下载失败: {}", e.getMessage(), e);
            throw new BizException("文件下载失败，请稍后重试");
        }
    }

    private String extractObjectName(String fileUrl) {
        if (StrUtil.isBlank(fileUrl)) {
            return null;
        }

        String endpoint = StrUtil.removeSuffix(minioConfig.getEndpoint(), "/");
        String bucketPrefix = endpoint + "/" + minioConfig.getBucketName() + "/";
        if (fileUrl.startsWith(bucketPrefix)) {
            return fileUrl.substring(bucketPrefix.length());
        }

        String bucketPath = "/" + minioConfig.getBucketName() + "/";
        int index = fileUrl.indexOf(bucketPath);
        if (index >= 0) {
            return fileUrl.substring(index + bucketPath.length());
        }
        return null;
    }
}

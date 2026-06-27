package com.xykj.common.service;

import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param file      文件
     * @param directory 存储目录（如 "materials"、"avatars"）
     * @return 文件访问 URL
     */
    String upload(MultipartFile file, String directory);

    /**
     * 删除文件
     *
     * @param fileUrl 文件访问 URL
     */
    void delete(String fileUrl);

    /**
     * 下载文件
     *
     * @param fileUrl 文件访问 URL
     * @return 文件流与元信息
     */
    StoredFile download(String fileUrl);

    /**
     * 存储文件下载结果
     */
    record StoredFile(InputStream inputStream, String contentType, Long size) {
    }
}

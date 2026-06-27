package com.xykj.common.util;

import com.xykj.common.exception.BizException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * 文件校验工具
 */
public class FileValidationUtil {

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /**
     * 校验图片文件
     *
     * @param file    上传的文件
     * @param maxSize 最大文件大小（字节）
     */
    public static void validateImageFile(MultipartFile file, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("上传文件不能为空");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw BizException.badRequest("仅支持 JPG、PNG、GIF、WebP 格式的图片");
        }
        if (file.getSize() > maxSize) {
            throw BizException.badRequest("图片大小不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }
    }
}

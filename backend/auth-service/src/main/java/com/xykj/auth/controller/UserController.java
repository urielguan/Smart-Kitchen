package com.xykj.auth.controller;

import com.xykj.auth.constant.AuthConstants;
import com.xykj.auth.dto.ChangePasswordRequest;
import com.xykj.auth.dto.ForceChangePasswordRequest;
import com.xykj.auth.dto.UpdateProfileRequest;
import com.xykj.auth.service.AuthService;
import com.xykj.auth.vo.UserInfoVO;
import com.xykj.common.enums.ResultCode;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.R;
import com.xykj.common.service.FileStorageService;
import com.xykj.common.util.FileValidationUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final FileStorageService fileStorageService;

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * 查询当前用户信息
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public R<UserInfoVO> getCurrentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        UserInfoVO vo = authService.getCurrentUser(userId);
        return R.ok(vo);
    }

    /**
     * 上传头像
     * POST /api/v1/auth/upload-avatar
     */
    @PostMapping("/upload-avatar")
    public R<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        FileValidationUtil.validateImageFile(file, MAX_AVATAR_SIZE);
        String avatarUrl = fileStorageService.upload(file, "avatars");
        Map<String, String> result = new HashMap<>();
        result.put("avatarUrl", avatarUrl);
        return R.ok(result);
    }

    /**
     * 修改个人信息
     * PUT /api/v1/auth/profile
     */
    @PutMapping("/profile")
    public R<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                       HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute(AuthConstants.CURRENT_USER_ID);
        authService.updateProfile(userId, request);
        return R.ok();
    }

    /**
     * 修改密码
     * PUT /api/v1/auth/password
     */
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                        HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute(AuthConstants.CURRENT_USER_ID);
        authService.changePassword(userId, request);
        return R.ok();
    }

    /**
     * 首次登录强制修改密码（不需要原密码）
     * PUT /api/v1/auth/password/force
     */
    @PutMapping("/password/force")
    public R<Void> forceChangePassword(@Valid @RequestBody ForceChangePasswordRequest request,
                                             HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute(AuthConstants.CURRENT_USER_ID);
        authService.forceChangePassword(userId, request);
        return R.ok();
    }
}

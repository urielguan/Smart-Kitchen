package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.EvidencePackageCreateDTO;
import com.xykj.device.vo.EvidencePackageVO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 证据包导出服务
 */
public interface EvidencePackageService {

    /** 创建证据包（异步打包） */
    EvidencePackageVO createPackage(EvidencePackageCreateDTO dto);

    /** 查询打包状态 */
    EvidencePackageVO getPackageStatus(Long id);

    /** 流式下载 ZIP 文件 */
    void downloadPackage(Long id, HttpServletResponse response);

    /** 失败重试 */
    EvidencePackageVO retryPackage(Long id);

    /** 证据包历史列表 */
    Page<EvidencePackageVO> getPackageList(int pageNum, int pageSize, Long orgId);
}

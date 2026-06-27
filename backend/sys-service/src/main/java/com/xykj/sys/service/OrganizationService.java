package com.xykj.sys.service;

import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.*;
import com.xykj.sys.vo.OrganizationStatisticsVO;
import com.xykj.sys.vo.OrganizationTreeVO;
import com.xykj.sys.vo.OrganizationVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 组织服务接口
 */
public interface OrganizationService {

    /**
     * 分页查询组织列表
     */
    PageResult<OrganizationVO> list(OrganizationQueryDTO query);

    /**
     * 获取组织详情
     */
    OrganizationVO getDetail(Long id);

    /**
     * 新增组织
     */
    Map<String, Object> create(OrganizationCreateDTO dto);

    /**
     * 更新组织
     */
    Map<String, Object> update(Long id, OrganizationUpdateDTO dto);

    /**
     * 删除组织
     */
    void delete(Long id);

    /**
     * 更新组织状态（启用/禁用）
     */
    Map<String, Object> updateStatus(Long id, OrganizationStatusDTO dto);

    /**
     * 获取组织树
     */
    List<OrganizationTreeVO> getTree(String orgType, String status, String keyword, Boolean includeChildren);

    /**
     * 获取统计数据
     */
    OrganizationStatisticsVO getStatistics();

    /**
     * 下载导入模板
     */
    void downloadImportTemplate(HttpServletResponse response);

    /**
     * 导入组织
     */
    OrganizationImportResultDTO importOrganizations(MultipartFile file);

    /**
     * 导出组织
     */
    void exportOrganizations(String orgType, String status, String keyword, Boolean includeChildren, HttpServletResponse response);

    /**
     * 下载错误文件
     */
    void downloadErrorFile(String fileName, HttpServletResponse response);
}

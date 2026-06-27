package com.xykj.wms.service;

import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.MaterialCreateDTO;
import com.xykj.wms.dto.MaterialImportResultDTO;
import com.xykj.wms.dto.MaterialQueryDTO;
import com.xykj.wms.dto.MaterialUpdateDTO;
import com.xykj.wms.vo.MaterialStatisticsVO;
import com.xykj.wms.vo.MaterialVO;
import com.xykj.wms.vo.NutritionSyncResultVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 物料服务接口
 */
public interface MaterialService {

    /**
     * 分页查询物料列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<MaterialVO> list(MaterialQueryDTO query);

    /**
     * 获取物料详情
     *
     * @param id 物料ID
     * @return 物料详情
     */
    MaterialVO getDetail(Long id);

    /**
     * 新增物料
     *
     * @param dto 创建参数
     * @return 新增物料ID
     */
    Long create(MaterialCreateDTO dto);

    /**
     * 更新物料
     *
     * @param id  物料ID
     * @param dto 更新参数
     */
    void update(Long id, MaterialUpdateDTO dto);

    /**
     * 删除物料
     *
     * @param id 物料ID
     */
    void delete(Long id);

    /**
     * 更新物料状态（启用/停用）
     *
     * @param id     物料ID
     * @param status 状态：active/inactive
     */
    void updateStatus(Long id, String status);

    /**
     * 获取物料统计数据
     *
     * @return 统计数据
     */
    MaterialStatisticsVO getStatistics(MaterialQueryDTO query);

    /**
     * 上传物料图片
     *
     * @param file 图片文件
     * @return 图片URL
     */
    String uploadImage(MultipartFile file);

    /**
     * 下载物料导入模板
     */
    void downloadImportTemplate(HttpServletResponse response);

    /**
     * 导入物料
     *
     * @param file Excel文件
     * @return 导入结果
     */
    MaterialImportResultDTO importMaterials(MultipartFile file);

    /**
     * 导出物料
     *
     * @param query   查询参数
     * @param response HTTP响应
     */
    void exportMaterials(MaterialQueryDTO query, HttpServletResponse response);

    /**
     * 下载导入错误文件
     *
     * @param fileName 文件名
     * @param response HTTP响应
     */
    void downloadErrorFile(String fileName, HttpServletResponse response);

    /**
     * 同步物料营养数据
     */
    NutritionSyncResultVO syncNutrition(Long id);
}

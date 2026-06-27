package com.xykj.wms.service;

import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.WarehouseCreateDTO;
import com.xykj.wms.dto.WarehouseImportResultDTO;
import com.xykj.wms.dto.WarehouseQueryDTO;
import com.xykj.wms.dto.WarehouseUpdateDTO;
import com.xykj.wms.vo.WarehouseStatisticsVO;
import com.xykj.wms.vo.WarehouseVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

public interface WarehouseService {

    /** 分页查询仓库列表（含仓位聚合） */
    PageResult<WarehouseVO> list(WarehouseQueryDTO query);

    /** 统计卡片 */
    WarehouseStatisticsVO getStatistics(WarehouseQueryDTO query);

    /** 下载导入模板 */
    void downloadImportTemplate(HttpServletResponse response);

    /** 导入仓库 */
    WarehouseImportResultDTO importWarehouses(MultipartFile file);

    /** 导出仓库 */
    void exportWarehouses(WarehouseQueryDTO query, HttpServletResponse response);

    /** 下载导入错误文件 */
    void downloadImportErrorFile(String fileName, HttpServletResponse response);

    /** 仓库详情 */
    WarehouseVO getDetail(Long id);

    /** 新增仓库 */
    Long create(WarehouseCreateDTO dto);

    /** 编辑仓库 */
    void update(Long id, WarehouseUpdateDTO dto);

    /** 删除仓库（有子仓位则拒绝） */
    void delete(Long id);
}

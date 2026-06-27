package com.xykj.wms.service;

import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.LocationCreateDTO;
import com.xykj.wms.dto.LocationImportResultDTO;
import com.xykj.wms.dto.LocationQueryDTO;
import com.xykj.wms.dto.LocationUpdateDTO;
import com.xykj.wms.vo.LocationVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

public interface LocationService {

    /** 分页查询仓位列表 */
    PageResult<LocationVO> list(LocationQueryDTO query);

    /** 下载导入模板 */
    void downloadImportTemplate(HttpServletResponse response);

    /** 导入仓位 */
    LocationImportResultDTO importLocations(MultipartFile file);

    /** 导出仓位 */
    void exportLocations(LocationQueryDTO query, HttpServletResponse response);

    /** 下载导入错误文件 */
    void downloadImportErrorFile(String fileName, HttpServletResponse response);

    /** 新增仓位 */
    Long create(LocationCreateDTO dto);

    /** 编辑仓位 */
    void update(Long id, LocationUpdateDTO dto);

    /** 删除仓位（used_capacity > 0 则拒绝） */
    void delete(Long id);
}

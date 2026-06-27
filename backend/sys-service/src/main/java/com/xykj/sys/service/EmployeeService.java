package com.xykj.sys.service;

import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.EmployeeCreateDTO;
import com.xykj.sys.dto.EmployeeImportResultDTO;
import com.xykj.sys.dto.EmployeeQueryDTO;
import com.xykj.sys.dto.EmployeeUpdateDTO;
import com.xykj.sys.vo.EmployeeVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 员工服务接口
 */
public interface EmployeeService {

    /**
     * 分页查询员工列表
     */
    PageResult<EmployeeVO> list(EmployeeQueryDTO query);

    /**
     * 获取员工详情
     */
    EmployeeVO getDetail(Long id);

    /**
     * 新增员工
     */
    Map<String, Object> create(EmployeeCreateDTO dto);

    /**
     * 更新员工
     */
    Map<String, Object> update(Long id, EmployeeUpdateDTO dto);

    /**
     * 删除员工
     */
    void delete(Long id);

    /**
     * 修改账号状态（启用/禁用）
     */
    void updateAccountStatus(Long id, String accountStatus);

    /**
     * 生成员工工号
     */
    String generateEmployeeNo();

    /**
     * 下载员工导入模板
     */
    void downloadImportTemplate(HttpServletResponse response);

    /**
     * 导入员工
     */
    EmployeeImportResultDTO importEmployees(MultipartFile file);

    /**
     * 导出员工
     */
    void exportEmployees(EmployeeQueryDTO query, HttpServletResponse response);

    /**
     * 下载导入错误文件
     */
    void downloadErrorFile(String fileName, HttpServletResponse response);
}
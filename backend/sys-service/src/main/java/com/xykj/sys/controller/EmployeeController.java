package com.xykj.sys.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.AccountStatusDTO;
import com.xykj.sys.dto.EmployeeCreateDTO;
import com.xykj.sys.dto.EmployeeImportResultDTO;
import com.xykj.sys.dto.EmployeeQueryDTO;
import com.xykj.sys.dto.EmployeeUpdateDTO;
import com.xykj.sys.service.EmployeeService;
import com.xykj.sys.vo.EmployeeVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 员工管理控制器
 * API路径: /api/v1/sys/employees
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sys/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * 员工列表（分页）
     * GET /api/v1/sys/employees
     */
    @GetMapping
    public R<PageResult<EmployeeVO>> list(EmployeeQueryDTO query) {
        PageResult<EmployeeVO> result = employeeService.list(query);
        return R.ok(result);
    }

    /**
     * 员工详情
     * GET /api/v1/sys/employees/{id}
     */
    @GetMapping("/{id}")
    public R<EmployeeVO> getDetail(@PathVariable Long id) {
        EmployeeVO employee = employeeService.getDetail(id);
        return R.ok(employee);
    }

    /**
     * 新增员工
     * POST /api/v1/sys/employees
     */
    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody EmployeeCreateDTO dto) {
        Map<String, Object> result = employeeService.create(dto);
        return R.ok(result);
    }

    /**
     * 编辑员工
     * PUT /api/v1/sys/employees/{id}
     */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody EmployeeUpdateDTO dto) {
        Map<String, Object> result = employeeService.update(id, dto);
        return R.ok(result);
    }

    /**
     * 删除员工
     * DELETE /api/v1/sys/employees/{id}
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return R.ok();
    }

    /**
     * 生成员工工号
     * GET /api/v1/sys/employees/generate-emp-no
     */
    @GetMapping("/generate-emp-no")
    public R<String> generateEmployeeNo() {
        String employeeNo = employeeService.generateEmployeeNo();
        return R.ok(employeeNo);
    }

    /**
     * 修改账号状态（启用/禁用）
     * PUT /api/v1/sys/employees/{id}/account-status
     */
    @PutMapping("/{id}/account-status")
    public R<Void> updateAccountStatus(@PathVariable Long id, @Valid @RequestBody AccountStatusDTO dto) {
        employeeService.updateAccountStatus(id, dto.getAccountStatus());
        return R.ok();
    }

    /**
     * 下载员工导入模板
     * GET /api/v1/sys/employees/import/template
     */
    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        employeeService.downloadImportTemplate(response);
    }

    /**
     * 导入员工
     * POST /api/v1/sys/employees/import
     */
    @PostMapping("/import")
    public R<EmployeeImportResultDTO> importEmployees(@RequestParam("file") MultipartFile file) {
        EmployeeImportResultDTO result = employeeService.importEmployees(file);
        return R.ok(result);
    }

    /**
     * 导出员工
     * GET /api/v1/sys/employees/export
     */
    @GetMapping("/export")
    public void exportEmployees(EmployeeQueryDTO query, HttpServletResponse response) {
        employeeService.exportEmployees(query, response);
    }

    /**
     * 下载导入错误文件
     * GET /api/v1/sys/employees/import/errors/{fileName}
     */
    @GetMapping("/import/errors/{fileName}")
    public void downloadErrorFile(@PathVariable String fileName, HttpServletResponse response) {
        employeeService.downloadErrorFile(fileName, response);
    }
}

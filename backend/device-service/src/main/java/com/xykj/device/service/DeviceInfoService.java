package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xykj.device.dto.DeviceCreateDTO;
import com.xykj.device.dto.DeviceOnlineStatusUpdateDTO;
import com.xykj.device.dto.DeviceQueryDTO;
import com.xykj.device.dto.DeviceUpdateDTO;
import com.xykj.device.dto.DeviceImportResultDTO;
import com.xykj.device.dto.DeviceBatchOperationResultDTO;
import com.xykj.device.entity.DeviceInfo;
import com.xykj.device.vo.DeviceDashboardVO;
import com.xykj.device.vo.DeviceDetailVO;
import com.xykj.device.vo.DeviceStatusLogVO;
import com.xykj.device.vo.DeviceVO;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface DeviceInfoService extends IService<DeviceInfo> {

    /**
     * 设备管理首页看板
     */
    DeviceDashboardVO getDashboard(DeviceQueryDTO query);

    /**
     * 设备列表（分页）
     */
    Page<DeviceVO> list(DeviceQueryDTO query);

    /**
     * 设备详情
     */
    DeviceDetailVO getDetail(Long id);

    /**
     * 新增设备
     */
    Long create(DeviceCreateDTO dto);

    /**
     * 编辑设备
     */
    void update(Long id, DeviceUpdateDTO dto);

    /**
     * 删除设备
     */
    void delete(Long id);

    /**
     * 批量删除设备
     */
    DeviceBatchOperationResultDTO batchDelete(List<Long> ids);

    /**
     * 更新设备在线状态
     */
    void updateOnlineStatus(Long id, DeviceOnlineStatusUpdateDTO dto);

    /**
     * 获取设备状态履历
     */
    List<DeviceStatusLogVO> getStatusLogs(Long id);

    /**
     * 切换设备启用/停用状态
     */
    void toggleStatus(Long id);

    /**
     * 批量启用设备
     */
    DeviceBatchOperationResultDTO batchEnable(List<Long> ids);

    /**
     * 批量停用设备
     */
    DeviceBatchOperationResultDTO batchDisable(List<Long> ids);

    /**
     * 导出设备列表到Excel
     */
    void exportDevices(DeviceQueryDTO query, HttpServletResponse response);

    /**
     * 批量导入设备
     */
    DeviceImportResultDTO importDevices(MultipartFile file);

    /**
     * 下载导入模板
     */
    void downloadTemplate(HttpServletResponse response);
}

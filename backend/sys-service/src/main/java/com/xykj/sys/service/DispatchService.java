package com.xykj.sys.service;

import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.DispatchQueryDTO;
import com.xykj.sys.dto.ProcessFormDTO;
import com.xykj.sys.vo.DispatchDetailVO;
import com.xykj.sys.vo.DispatchVO;
import com.xykj.sys.vo.WorkOrderRecordVO;

import java.util.List;
import java.util.Map;

/**
 * 派单服务接口
 */
public interface DispatchService {

    /**
     * 获取派单列表（分页）
     */
    PageResult<DispatchVO> list(DispatchQueryDTO query);

    /**
     * 获取派单详情
     */
    DispatchDetailVO getDetail(Long id);

    /**
     * 获取处理记录列表
     */
    List<WorkOrderRecordVO> getRecords(Long id);

    /**
     * 处理工单
     */
    Map<String, Object> process(Long id, ProcessFormDTO dto);
}

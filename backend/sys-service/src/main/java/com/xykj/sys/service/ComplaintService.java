package com.xykj.sys.service;

import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.ComplaintCreateDTO;
import com.xykj.sys.dto.ComplaintQueryDTO;
import com.xykj.sys.dto.DispatchFormDTO;
import com.xykj.sys.dto.SatisfactionFormDTO;
import com.xykj.sys.vo.ComplaintDetailVO;
import com.xykj.sys.vo.ComplaintVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

/**
 * 投诉服务接口
 */
public interface ComplaintService {

    /**
     * 获取投诉列表（分页）
     */
    PageResult<ComplaintVO> list(ComplaintQueryDTO query);

    /**
     * 获取投诉详情
     */
    ComplaintDetailVO getDetail(Long id);

    /**
     * 新增投诉
     */
    Map<String, Object> create(ComplaintCreateDTO dto);

    /**
     * 派单
     */
    Map<String, Object> dispatch(Long id, DispatchFormDTO dto);

    /**
     * 满意度评价
     */
    Map<String, Object> updateSatisfaction(Long id, SatisfactionFormDTO dto);

    /**
     * 导出投诉
     */
    void exportComplaints(ComplaintQueryDTO query, HttpServletResponse response);
}

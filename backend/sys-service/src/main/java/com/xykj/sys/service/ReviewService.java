package com.xykj.sys.service;

import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.ReviewCreateDTO;
import com.xykj.sys.dto.ReviewQueryDTO;
import com.xykj.sys.dto.ReviewReplyDTO;
import com.xykj.sys.vo.ReviewVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

/**
 * 评价服务接口
 */
public interface ReviewService {

    /**
     * 获取评价列表（分页）
     */
    PageResult<ReviewVO> list(ReviewQueryDTO query);

    /**
     * 获取评价详情
     */
    ReviewVO getDetail(Long id);

    /**
     * 新增评价
     */
    Map<String, Object> create(ReviewCreateDTO dto);

    /**
     * 回复评价
     */
    Map<String, Object> reply(Long id, ReviewReplyDTO dto);

    /**
     * 导出评价
     */
    void exportReviews(ReviewQueryDTO query, HttpServletResponse response);
}

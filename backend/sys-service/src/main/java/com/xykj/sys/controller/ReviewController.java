package com.xykj.sys.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.ReviewCreateDTO;
import com.xykj.sys.dto.ReviewQueryDTO;
import com.xykj.sys.dto.ReviewReplyDTO;
import com.xykj.sys.service.ReviewService;
import com.xykj.sys.vo.ReviewVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 评价Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sys/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 获取评价列表
     */
    @GetMapping
    public R<PageResult<ReviewVO>> list(ReviewQueryDTO query) {
        PageResult<ReviewVO> result = reviewService.list(query);
        return R.ok(result);
    }

    /**
     * 获取评价详情
     */
    @GetMapping("/{id}")
    public R<ReviewVO> getDetail(@PathVariable Long id) {
        ReviewVO vo = reviewService.getDetail(id);
        return R.ok(vo);
    }

    /**
     * 新增评价
     */
    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody ReviewCreateDTO dto) {
        Map<String, Object> result = reviewService.create(dto);
        return R.ok(result);
    }

    /**
     * 回复评价
     */
    @PostMapping("/{id}/reply")
    public R<Map<String, Object>> reply(@PathVariable Long id, @Valid @RequestBody ReviewReplyDTO dto) {
        Map<String, Object> result = reviewService.reply(id, dto);
        return R.ok(result);
    }

    /**
     * 导出评价
     */
    @GetMapping("/export")
    public void exportReviews(ReviewQueryDTO query, HttpServletResponse response) {
        reviewService.exportReviews(query, response);
    }
}

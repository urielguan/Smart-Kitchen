package com.xykj.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果
 * 符合API文档规范：
 * {
 *   "list": [],
 *   "pageNum": 1,
 *   "pageSize": 20,
 *   "total": 128,
 *   "totalPages": 7
 * }
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据列表
     */
    private List<T> list;

    /**
     * 当前页码
     */
    private Long pageNum;

    /**
     * 每页条数
     */
    private Long pageSize;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Long totalPages;

    public PageResult() {
    }

    public PageResult(List<T> list, Long pageNum, Long pageSize, Long total, Long totalPages) {
        this.list = list;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = totalPages;
    }

    /**
     * 从 MyBatis Plus IPage 转换
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(
                page.getRecords(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages()
        );
    }

    /**
     * 从 MyBatis Plus IPage 转换（带类型映射）
     */
    public static <S, T> PageResult<T> of(IPage<S> page, List<T> targetList) {
        return new PageResult<>(
                targetList,
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages()
        );
    }

    /**
     * 空分页结果
     */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 1L, 20L, 0L, 0L);
    }

    /**
     * 空分页结果（指定分页参数）
     */
    public static <T> PageResult<T> empty(Long pageNum, Long pageSize) {
        return new PageResult<>(Collections.emptyList(), pageNum, pageSize, 0L, 0L);
    }

    /**
     * 手动构建分页结果
     */
    public static <T> PageResult<T> of(List<T> list, Long pageNum, Long pageSize, Long total) {
        long totalPages = pageSize > 0 ? (total + pageSize - 1) / pageSize : 0L;
        return new PageResult<>(list, pageNum, pageSize, total, totalPages);
    }
}
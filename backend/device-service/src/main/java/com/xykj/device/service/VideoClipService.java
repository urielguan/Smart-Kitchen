package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.ClipExtractDTO;
import com.xykj.device.dto.ClipQueryDTO;
import com.xykj.device.vo.VideoClipVO;

/**
 * 视频片段截取服务接口
 */
public interface VideoClipService {

    /**
     * 提取视频片段（异步）
     * @param dto 截取请求参数
     * @return 片段信息（初始状态为 processing）
     */
    VideoClipVO extractClip(ClipExtractDTO dto);

    /**
     * 获取片段列表
     * @param query 查询参数
     * @return 分页结果
     */
    Page<VideoClipVO> getClipList(ClipQueryDTO query);

    /**
     * 获取片段详情（用于轮询状态）
     * @param id 片段ID
     * @return 片段详情
     */
    VideoClipVO getClipDetail(Long id);

    /**
     * 删除片段
     * @param id 片段ID
     */
    void deleteClip(Long id);
}

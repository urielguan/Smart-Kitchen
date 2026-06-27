package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.RecordingQueryDTO;
import com.xykj.device.vo.RecordingStatisticsVO;
import com.xykj.device.vo.RecordingVO;

/**
 * 视频录像服务接口
 */
public interface RecordingService {

    /**
     * 获取录像列表
     * @param query 查询条件
     * @return 分页结果
     */
    Page<RecordingVO> getRecordingList(RecordingQueryDTO query);

    /**
     * 获取录像统计（所有符合筛选条件的数据汇总，非仅当前页）
     * @param query 查询条件
     * @return 统计数据
     */
    RecordingStatisticsVO getRecordingStatistics(RecordingQueryDTO query);

    /**
     * 获取录像详情
     * @param id 录像ID
     * @return 录像详情
     */
    RecordingVO getRecordingDetail(Long id);

    /**
     * 获取录像播放地址
     * @param id 录像ID
     * @return 播放地址
     */
    String getPlaybackUrl(Long id);

    /**
     * 删除录像
     * @param id 录像ID
     */
    void deleteRecording(Long id);
}

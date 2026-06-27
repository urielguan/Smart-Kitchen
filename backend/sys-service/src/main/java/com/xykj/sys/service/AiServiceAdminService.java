package com.xykj.sys.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.ai.entity.AiRequestLog;
import com.xykj.sys.dto.AiServiceConfigCreateDTO;
import com.xykj.sys.dto.AiServiceConfigQueryDTO;
import com.xykj.sys.dto.AiServiceConfigStatusDTO;
import com.xykj.sys.dto.AiServiceConfigUpdateDTO;
import com.xykj.sys.vo.AiServiceConfigVO;
import com.xykj.sys.vo.AiServiceTestVO;

public interface AiServiceAdminService {

    Page<AiServiceConfigVO> page(AiServiceConfigQueryDTO query);

    AiServiceConfigVO detail(Long id);

    Long create(AiServiceConfigCreateDTO dto);

    void update(Long id, AiServiceConfigUpdateDTO dto);

    void delete(Long id);

    void changeStatus(Long id, AiServiceConfigStatusDTO dto);

    AiServiceTestVO test(Long id);

    Page<AiRequestLog> logs(Long id, Long pageNum, Long pageSize);
}

package com.xykj.wms.service;

import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.InventoryMovementQueryDTO;
import com.xykj.wms.dto.InventoryOverviewQueryDTO;
import com.xykj.wms.vo.InventoryDistributionVO;
import com.xykj.wms.vo.InventoryMovementVO;
import com.xykj.wms.vo.InventoryOverviewVO;
import com.xykj.wms.vo.InventoryShelfLifeSummaryVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface InventoryService {

    PageResult<InventoryOverviewVO> getOverview(InventoryOverviewQueryDTO query);

    List<InventoryDistributionVO> getDistribution(Long materialId);

    InventoryShelfLifeSummaryVO getShelfLifeSummary(Long materialId);

    PageResult<InventoryMovementVO> getMovements(Long materialId, InventoryMovementQueryDTO query);

    void exportOverview(InventoryOverviewQueryDTO query, HttpServletResponse response);

    void exportMovements(Long materialId, InventoryMovementQueryDTO query, HttpServletResponse response);
}

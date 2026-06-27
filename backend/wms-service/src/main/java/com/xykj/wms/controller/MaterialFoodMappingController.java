package com.xykj.wms.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.wms.dto.MaterialFoodMappingCreateDTO;
import com.xykj.wms.dto.MaterialFoodMappingQueryDTO;
import com.xykj.wms.service.MaterialFoodMappingService;
import com.xykj.wms.vo.MaterialFoodMappingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wms/material-food-mappings")
@RequiredArgsConstructor
public class MaterialFoodMappingController {

    private final MaterialFoodMappingService materialFoodMappingService;

    @GetMapping
    public R<PageResult<MaterialFoodMappingVO>> list(MaterialFoodMappingQueryDTO query) {
        return R.ok(materialFoodMappingService.list(query));
    }

    @PostMapping
    public R<MaterialFoodMappingVO> create(@Valid @RequestBody MaterialFoodMappingCreateDTO dto) {
        return R.ok(materialFoodMappingService.create(dto));
    }
}

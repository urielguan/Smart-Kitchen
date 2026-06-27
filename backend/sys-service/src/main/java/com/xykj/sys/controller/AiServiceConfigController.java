package com.xykj.sys.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.ai.entity.AiRequestLog;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.AiServiceConfigCreateDTO;
import com.xykj.sys.dto.AiServiceConfigQueryDTO;
import com.xykj.sys.dto.AiServiceConfigStatusDTO;
import com.xykj.sys.dto.AiServiceConfigUpdateDTO;
import com.xykj.sys.service.AiServiceAdminService;
import com.xykj.sys.vo.AiServiceConfigVO;
import com.xykj.sys.vo.AiServiceTestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sys/ai-services")
@RequiredArgsConstructor
public class AiServiceConfigController {

    private final AiServiceAdminService aiServiceAdminService;

    @GetMapping
    public R<PageResult<AiServiceConfigVO>> page(AiServiceConfigQueryDTO query) {
        Page<AiServiceConfigVO> page = aiServiceAdminService.page(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/{id}")
    public R<AiServiceConfigVO> detail(@PathVariable Long id) {
        return R.ok(aiServiceAdminService.detail(id));
    }

    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody AiServiceConfigCreateDTO dto) {
        Long id = aiServiceAdminService.create(dto);
        return R.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AiServiceConfigUpdateDTO dto) {
        aiServiceAdminService.update(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        aiServiceAdminService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody AiServiceConfigStatusDTO dto) {
        aiServiceAdminService.changeStatus(id, dto);
        return R.ok();
    }

    @PostMapping("/{id}/test")
    public R<AiServiceTestVO> test(@PathVariable Long id) {
        return R.ok(aiServiceAdminService.test(id));
    }

    @GetMapping("/{id}/logs")
    public R<PageResult<AiRequestLog>> logs(@PathVariable Long id,
                                            @RequestParam(defaultValue = "1") Long pageNum,
                                            @RequestParam(defaultValue = "20") Long pageSize) {
        return R.ok(PageResult.of(aiServiceAdminService.logs(id, pageNum, pageSize)));
    }
}

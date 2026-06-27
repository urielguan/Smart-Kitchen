package com.xykj.wms.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.wms.dto.OutboundImportResultDTO;
import com.xykj.wms.dto.OutboundImportTaskActionDTO;
import com.xykj.wms.dto.OutboundImportTaskDTO;
import com.xykj.wms.dto.OutboundOrderCreateDTO;
import com.xykj.wms.dto.OutboundOrderQueryDTO;
import com.xykj.wms.dto.OutboundOrderUpdateDTO;
import com.xykj.wms.dto.OutboundSuggestionPreviewDTO;
import com.xykj.wms.dto.OutboundSuggestionRevalidateDTO;
import com.xykj.wms.service.OutboundOrderService;
import com.xykj.wms.vo.OutboundOrderStatisticsVO;
import com.xykj.wms.vo.OutboundOrderVO;
import com.xykj.wms.vo.OutboundSourceOrderOptionVO;
import com.xykj.wms.vo.OutboundSuggestionPreviewVO;
import com.xykj.wms.vo.OutboundSuggestionRevalidateVO;
import com.xykj.wms.vo.OutboundTypeOptionVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/wms/outbound-orders")
@RequiredArgsConstructor
public class OutboundOrderController {

    private final OutboundOrderService outboundOrderService;

    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        outboundOrderService.downloadImportTemplate(response);
    }

    @PostMapping("/import")
    public R<OutboundImportResultDTO> importOrders(@RequestParam("file") MultipartFile file) {
        return R.ok(outboundOrderService.importOrders(file));
    }

    @GetMapping("/import/tasks/{taskNo}")
    public R<OutboundImportTaskDTO> getImportTask(@PathVariable("taskNo") String taskNo) {
        return R.ok(outboundOrderService.getImportTask(taskNo));
    }

    @PostMapping("/import/tasks/{taskNo}/resume")
    public R<OutboundImportTaskDTO> resumeImportTask(@PathVariable("taskNo") String taskNo,
                                                     @RequestBody(required = false) OutboundImportTaskActionDTO action) {
        return R.ok(outboundOrderService.resumeImportTask(taskNo, action));
    }

    @PostMapping("/import/tasks/{taskNo}/terminate")
    public R<OutboundImportTaskDTO> terminateImportTask(@PathVariable("taskNo") String taskNo,
                                                        @RequestBody(required = false) OutboundImportTaskActionDTO action) {
        return R.ok(outboundOrderService.terminateImportTask(taskNo, action));
    }

    @GetMapping("/import/errors/{fileName}")
    public void downloadImportErrorFile(@PathVariable("fileName") String fileName, HttpServletResponse response) {
        outboundOrderService.downloadImportErrorFile(fileName, response);
    }

    @GetMapping("/export")
    public void exportList(@Valid OutboundOrderQueryDTO query, HttpServletResponse response) {
        outboundOrderService.exportList(query, response);
    }

    @GetMapping("/export/details")
    public void exportDetails(@Valid OutboundOrderQueryDTO query, HttpServletResponse response) {
        outboundOrderService.exportDetails(query, response);
    }

    @GetMapping("/type-options")
    public R<List<OutboundTypeOptionVO>> typeOptions() {
        return R.ok(outboundOrderService.typeOptions());
    }

    /** GET /api/v1/wms/outbound-orders */
    @GetMapping
    public R<PageResult<OutboundOrderVO>> list(@Valid OutboundOrderQueryDTO query) {
        return R.ok(outboundOrderService.list(query));
    }

    /** GET /api/v1/wms/outbound-orders/statistics */
    @GetMapping("/statistics")
    public R<OutboundOrderStatisticsVO> statistics() {
        return R.ok(outboundOrderService.getStatistics());
    }

    @GetMapping("/source-order-options")
    public R<List<OutboundSourceOrderOptionVO>> sourceOrderOptions(@RequestParam String outboundType) {
        return R.ok(outboundOrderService.listSourceOrderOptions(outboundType));
    }

    /** GET /api/v1/wms/outbound-orders/{id} */
    @GetMapping("/{id:\\d+}")
    public R<OutboundOrderVO> detail(@PathVariable Long id) {
        return R.ok(outboundOrderService.getDetail(id));
    }

    @PostMapping("/suggestions/preview")
    public R<OutboundSuggestionPreviewVO> previewSuggestions(@Valid @RequestBody OutboundSuggestionPreviewDTO dto) {
        return R.ok(outboundOrderService.previewSuggestions(dto));
    }

    @PostMapping("/suggestions/revalidate")
    public R<OutboundSuggestionRevalidateVO> revalidateSuggestions(@Valid @RequestBody OutboundSuggestionRevalidateDTO dto) {
        return R.ok(outboundOrderService.revalidateSuggestions(dto));
    }

    /** POST /api/v1/wms/outbound-orders */
    @PostMapping
    public R<Long> create(@Valid @RequestBody OutboundOrderCreateDTO dto) {
        return R.ok(outboundOrderService.create(dto));
    }

    /** PUT /api/v1/wms/outbound-orders/{id} */
    @PutMapping("/{id:\\d+}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody OutboundOrderUpdateDTO dto) {
        outboundOrderService.update(id, dto);
        return R.ok();
    }

    /** DELETE /api/v1/wms/outbound-orders/{id} */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        outboundOrderService.delete(id);
        return R.ok();
    }

    /** POST /api/v1/wms/outbound-orders/{id}/submit */
    @PostMapping("/{id:\\d+}/submit")
    public R<Void> submit(@PathVariable Long id) {
        outboundOrderService.submit(id);
        return R.ok();
    }

    /** POST /api/v1/wms/outbound-orders/{id}/approve */
    @PostMapping("/{id:\\d+}/approve")
    public R<Void> approve(@PathVariable Long id,
                           @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("approveRemark") : null;
        outboundOrderService.approve(id, remark);
        return R.ok();
    }

    /** POST /api/v1/wms/outbound-orders/{id}/reject */
    @PostMapping("/{id:\\d+}/reject")
    public R<Void> reject(@PathVariable Long id,
                          @RequestBody Map<String, String> body) {
        outboundOrderService.reject(id, body.get("rejectReason"));
        return R.ok();
    }

    /** POST /api/v1/wms/outbound-orders/{id}/withdraw */
    @PostMapping("/{id:\\d+}/withdraw")
    public R<Void> withdraw(@PathVariable Long id) {
        outboundOrderService.withdraw(id);
        return R.ok();
    }

    /** POST /api/v1/wms/outbound-orders/{id}/execute */
    @PostMapping("/{id:\\d+}/execute")
    public R<Void> execute(@PathVariable Long id) {
        outboundOrderService.execute(id);
        return R.ok();
    }

    /** POST /api/v1/wms/outbound-orders/{id}/reverse */
    @PostMapping("/{id:\\d+}/reverse")
    public R<Void> reverse(@PathVariable Long id) {
        outboundOrderService.reverse(id);
        return R.ok();
    }

    @PostMapping("/{id:\\d+}/attachments")
    public R<Void> uploadAttachments(@PathVariable Long id, @RequestParam("files") MultipartFile[] files) {
        outboundOrderService.uploadAttachments(id, files);
        return R.ok();
    }

    @GetMapping("/{id:\\d+}/attachments/download")
    public void downloadAttachment(@PathVariable Long id,
                                   @RequestParam String url,
                                   HttpServletResponse response) {
        outboundOrderService.downloadAttachment(id, url, response);
    }
}

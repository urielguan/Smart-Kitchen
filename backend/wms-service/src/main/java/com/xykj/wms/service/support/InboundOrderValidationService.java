package com.xykj.wms.service.support;

import com.xykj.wms.dto.InboundValidationErrorDTO;
import com.xykj.wms.entity.InboundOrder;
import com.xykj.wms.exception.InboundOrderValidationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InboundOrderValidationService {

    public void validateEditable(InboundOrder order) {
        if (order == null) {
            return;
        }
        if (!"draft".equals(order.getStatus()) && !"rejected".equals(order.getStatus())) {
            throw global("当前入库单状态不允许编辑", order.getVersion(), "只有草稿或已驳回的入库单可以编辑");
        }
    }

    public void validateSubmittable(InboundOrder order) {
        if (order == null) {
            return;
        }
        if (!"draft".equals(order.getStatus()) && !"rejected".equals(order.getStatus())) {
            throw global("当前入库单状态不允许提交", order.getVersion(), "只有草稿或已驳回的入库单可以提交");
        }
    }

    public void validateDeletable(InboundOrder order) {
        if (order == null) {
            return;
        }
        if (!"draft".equals(order.getStatus())) {
            throw global(
                    "当前入库单状态不允许删除",
                    order.getVersion(),
                    "该入库单已提交审批或已完成入库生成库存数据，为保证溯源完整与库存准确，不允许删除。"
            );
        }
    }

    public void rejectPurchaseSourceUnavailable(Long latestVersion, String message) {
        throw global("采购来源单不可用于当前入库", latestVersion, message);
    }

    public void rejectPurchaseSourceMaterialMismatch(String lineKey, Long latestVersion, String message) {
        throw InboundOrderValidationException.withFieldError(
                "采购来源单不可用于当前入库",
                latestVersion,
                lineKey,
                "materialId",
                message
        );
    }

    public void rejectQuantityExceeded(String lineKey, Long latestVersion, String message) {
        throw InboundOrderValidationException.withFieldError(
                "采购入库数量超过来源单剩余可入库数量",
                latestVersion,
                lineKey,
                "quantity",
                message
        );
    }

    public void rejectWarehouseValidation(String lineKey, Long latestVersion, String message) {
        throw InboundOrderValidationException.withFieldError(
                "入库仓库校验失败",
                latestVersion,
                lineKey,
                "warehouseId",
                message
        );
    }

    public void rejectRequiredItemField(String lineKey, String field, Long latestVersion, String message) {
        throw InboundOrderValidationException.withFieldError(
                "入库明细字段缺失",
                latestVersion,
                lineKey,
                field,
                message
        );
    }

    public void rejectItemReferenceValidation(String lineKey, String field, Long latestVersion, String message) {
        throw InboundOrderValidationException.withFieldError(
                "入库明细引用校验失败",
                latestVersion,
                lineKey,
                field,
                message
        );
    }

    public void rejectProductionDateValidation(String lineKey, Long latestVersion, String message) {
        throw InboundOrderValidationException.withFieldError(
                "入库明细生产日期校验失败",
                latestVersion,
                lineKey,
                "productionDate",
                message
        );
    }

    public void rejectSubmitAreaExceeded(Long latestVersion, String message) {
        throw global("入库仓位容量校验失败", latestVersion, message);
    }

    public void rejectVersionConflict(Long latestVersion) {
        throw global("当前入库单版本已过期", latestVersion, "当前数据已被其他人更新，请刷新后重试");
    }

    public void rejectDuplicateSubmit(Long latestVersion) {
        throw global("请勿重复提交", latestVersion, "该入库单已受理过相同提交请求，请勿重复操作");
    }

    public void rejectRetryPostUnavailable(Long latestVersion) {
        throw global("当前入库单状态不允许重试过账", latestVersion, "只有过账失败的已审核入库单可以重试过账");
    }

    public void rejectPostApprovedUnavailable(Long latestVersion) {
        throw global("当前入库单状态不允许执行过账", latestVersion, "只有已审核且未过账的入库单可以执行过账");
    }

    public void rejectUnapproveUnavailable(Long latestVersion) {
        throw global("当前入库单状态不允许反审核", latestVersion, "只有已审核或已入库的入库单可以反审核");
    }

    public void rejectUnapprovePreconditionFailed(Long latestVersion, String message) {
        throw global("反审核前置校验失败", latestVersion, message);
    }

    public void rejectPostFailed(Long latestVersion, String message) {
        throw global("库存过账失败", latestVersion, message);
    }

    private InboundOrderValidationException global(String globalMessage, Long latestVersion, String detailMessage) {
        return InboundOrderValidationException.of(
                InboundValidationErrorDTO.of(
                        globalMessage,
                        latestVersion,
                        List.of(InboundValidationErrorDTO.FieldErrorDTO.of(null, null, detailMessage))
                )
        );
    }
}

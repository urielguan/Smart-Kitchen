package com.xykj.wms.exception;

import com.xykj.wms.dto.InboundValidationErrorDTO;
import lombok.Getter;

@Getter
public class InboundOrderValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final InboundValidationErrorDTO error;

    public InboundOrderValidationException(InboundValidationErrorDTO error) {
        super(error != null ? error.getGlobalMessage() : null);
        this.error = error;
    }

    public static InboundOrderValidationException of(InboundValidationErrorDTO error) {
        return new InboundOrderValidationException(error);
    }

    public static InboundOrderValidationException withFieldError(String globalMessage,
                                                                 Long latestVersion,
                                                                 String lineKey,
                                                                 String field,
                                                                 String message) {
        return of(InboundValidationErrorDTO.withFieldError(globalMessage, latestVersion, lineKey, field, message));
    }
}

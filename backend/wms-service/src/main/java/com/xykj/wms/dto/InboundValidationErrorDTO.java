package com.xykj.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboundValidationErrorDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String ERROR_TYPE = "INBOUND_ORDER_VALIDATION";

    private String errorType;
    private String globalMessage;
    private List<FieldErrorDTO> fieldErrors;
    private Long latestVersion;

    public static InboundValidationErrorDTO of(String globalMessage, Long latestVersion, List<FieldErrorDTO> fieldErrors) {
        return new InboundValidationErrorDTO(
                ERROR_TYPE,
                globalMessage,
                fieldErrors == null ? List.of() : List.copyOf(fieldErrors),
                latestVersion
        );
    }

    public static InboundValidationErrorDTO withFieldError(String globalMessage,
                                                            Long latestVersion,
                                                            String lineKey,
                                                            String field,
                                                            String message) {
        List<FieldErrorDTO> errors = new ArrayList<>(1);
        errors.add(FieldErrorDTO.of(lineKey, field, message));
        return of(globalMessage, latestVersion, errors);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldErrorDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String lineKey;
        private String field;
        private String message;

        public static FieldErrorDTO of(String lineKey, String field, String message) {
            return new FieldErrorDTO(lineKey, field, message);
        }
    }
}

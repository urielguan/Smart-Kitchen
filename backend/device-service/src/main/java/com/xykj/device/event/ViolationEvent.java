package com.xykj.device.event;

import com.xykj.device.vo.ViolationVO;
import org.springframework.context.ApplicationEvent;

/**
 * 违规事件，用于SSE推送
 */
public class ViolationEvent extends ApplicationEvent {

    private final ViolationVO violation;

    public ViolationEvent(Object source, ViolationVO violation) {
        super(source);
        this.violation = violation;
    }

    public ViolationVO getViolation() {
        return violation;
    }
}

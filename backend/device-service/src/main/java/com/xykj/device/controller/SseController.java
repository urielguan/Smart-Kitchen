package com.xykj.device.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.device.event.DeviceOnlineStatusEvent;
import com.xykj.device.event.ViolationEvent;
import com.xykj.device.vo.ViolationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE实时推送控制器
 * 提供违规事件实时推送能力
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/events")
public class SseController {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30分钟超时
    private static final long HEARTBEAT_INTERVAL = 30_000L;   // 30秒心跳

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * SSE事件流端点
     * GET /api/v1/device/events/stream
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            log.info("SSE连接完成，移除: {}", emitter);
            emitters.remove(emitter);
        });

        emitter.onTimeout(() -> {
            log.info("SSE连接超时，移除: {}", emitter);
            emitters.remove(emitter);
        });

        emitter.onError(ex -> {
            log.warn("SSE连接异常，移除: {}", emitter, ex);
            emitters.remove(emitter);
        });

        emitters.add(emitter);
        log.info("新SSE连接，当前连接数: {}", emitters.size());

        // 启动心跳
        startHeartbeat(emitter);

        return emitter;
    }

    /**
     * 监听违规事件，推送给所有SSE客户端
     */
    @EventListener
    public void onViolationEvent(ViolationEvent event) {
        ViolationVO violation = event.getViolation();
        log.info("收到违规事件推送: id={}, type={}", violation.getId(), violation.getViolationType());

        String json;
        try {
            json = objectMapper.writeValueAsString(violation);
        } catch (Exception e) {
            log.error("序列化违规事件失败", e);
            return;
        }

        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("violation")
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                log.warn("推送SSE事件失败，移除连接", e);
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    /**
     * 监听设备在线状态变更事件，推送给所有SSE客户端
     */
    @EventListener
    public void onDeviceOnlineStatusEvent(DeviceOnlineStatusEvent event) {
        log.info("收到设备状态变更推送: deviceId={}, {} -> {}", event.getDeviceId(), event.getOldStatus(), event.getNewStatus());

        String json;
        try {
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("deviceId", event.getDeviceId());
            payload.put("deviceName", event.getDeviceName());
            payload.put("deviceType", event.getDeviceType());
            payload.put("oldStatus", event.getOldStatus());
            payload.put("newStatus", event.getNewStatus());
            payload.put("timestamp", System.currentTimeMillis());
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("序列化设备状态事件失败", e);
            return;
        }

        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("device_status")
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                log.warn("推送SSE设备状态事件失败，移除连接", e);
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    /**
     * 心跳保活
     */
    private void startHeartbeat(SseEmitter emitter) {
        Thread heartbeatThread = new Thread(() -> {
            while (emitters.contains(emitter)) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data("{\"time\":" + System.currentTimeMillis() + "}",
                                    MediaType.APPLICATION_JSON));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    break;
                }
            }
        }, "sse-heartbeat-" + emitter.hashCode());
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
}

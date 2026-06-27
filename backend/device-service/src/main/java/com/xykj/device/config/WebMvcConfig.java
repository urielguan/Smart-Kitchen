package com.xykj.device.config;

import com.xykj.common.interceptor.UserContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserContextInterceptor userContextInterceptor;
    private final StreamConfig streamConfig;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/hls/**", "/recordings/**", "/clips/**", "/screenshots/**");  // HLS流、录像文件和片段文件不需要认证
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将/hls/**映射到HLS文件输出目录
        registry.addResourceHandler("/hls/**")
                .addResourceLocations("file:" + streamConfig.getHls().getOutputDir() + "/");

        // 将/recordings/**映射到录像文件输出目录
        registry.addResourceHandler("/recordings/**")
                .addResourceLocations("file:" + streamConfig.getRecording().getOutputDir() + "/");

        // 将/clips/**映射到片段文件输出目录（clips子目录）
        registry.addResourceHandler("/clips/**")
                .addResourceLocations("file:" + streamConfig.getRecording().getOutputDir() + "/clips/");

        // 将/screenshots/**映射到截图文件输出目录
        registry.addResourceHandler("/screenshots/**")
                .addResourceLocations("file:" + streamConfig.getRecording().getOutputDir() + "/screenshots/");
    }
}

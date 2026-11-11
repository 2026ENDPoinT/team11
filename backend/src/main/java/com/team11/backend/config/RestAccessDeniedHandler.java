package com.team11.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.team11.backend.common.ApiError;
import com.team11.backend.common.ApiResponse;
import com.team11.backend.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws java.io.IOException {
        response.setStatus(ErrorCode.AUTH_FORBIDDEN.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(ApiError.of(ErrorCode.AUTH_FORBIDDEN))));
    }
}

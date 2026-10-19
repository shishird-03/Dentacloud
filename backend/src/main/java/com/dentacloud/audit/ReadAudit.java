package com.dentacloud.audit;

import com.dentacloud.shared.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Audits every successful clinical read (PRD 14). Runs after the controller and before the body is
 * written, so if the audit insert fails the request fails and no clinical data leaves the server.
 * The resource id is the path's {id} or {patientId}; list reads record only a count.
 */
@RestControllerAdvice
class ReadAudit implements ResponseBodyAdvice<Object> {
    private static final Map<String, String> CLINICAL = Map.of(
            "/api/v1/patients", "patient",
            "/api/v1/patients/{id}", "patient",
            "/api/v1/patients/{patientId}/chart", "chart",
            "/api/v1/patients/{patientId}/visits", "visit",
            "/api/v1/visits/{id}", "visit",
            "/api/v1/patients/{patientId}/treatment-plans", "treatment_plan",
            "/api/v1/prescriptions/{id}", "prescription",
            "/api/v1/patients/{patientId}/prescriptions", "prescription",
            "/api/v1/sync/pull", "sync");

    private final AuditService audit;

    ReadAudit(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || body instanceof ProblemDetail || !HttpMethod.GET.equals(request.getMethod())
                || !(request instanceof ServletServerHttpRequest servlet) || TenantContext.current() == null) {
            return body;
        }
        HttpServletRequest http = servlet.getServletRequest();
        String type = CLINICAL.get((String) http.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        if (type == null) return body;
        Map<String, String> vars = (Map<String, String>) http.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String id = vars == null ? null : vars.getOrDefault("id", vars.get("patientId"));
        audit.recordRead(type, id == null ? null : UUID.fromString(id),
                body instanceof Collection<?> c ? Map.of("count", c.size()) : null);
        return body;
    }
}

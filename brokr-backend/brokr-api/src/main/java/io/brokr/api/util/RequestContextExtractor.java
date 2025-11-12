package io.brokr.api.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
public class RequestContextExtractor {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    public HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    public String getIpAddress() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }

        // Check for X-Forwarded-For header (for proxies/load balancers)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }

        // Check for X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Fallback to remote address
        return request.getRemoteAddr();
    }

    public String getUserAgent() {
        HttpServletRequest request = getRequest();
        return request != null ? request.getHeader("User-Agent") : null;
    }

    public String getRequestId() {
        String requestId = REQUEST_ID.get();
        if (requestId == null) {
            // Check if there's a request ID in the header
            HttpServletRequest request = getRequest();
            if (request != null) {
                String headerRequestId = request.getHeader("X-Request-ID");
                if (headerRequestId != null && !headerRequestId.isEmpty()) {
                    requestId = headerRequestId;
                } else {
                    // Generate a new request ID
                    requestId = UUID.randomUUID().toString();
                }
                REQUEST_ID.set(requestId);
            } else {
                requestId = UUID.randomUUID().toString();
                REQUEST_ID.set(requestId);
            }
        }
        return requestId;
    }

    public void clearRequestId() {
        REQUEST_ID.remove();
    }
}


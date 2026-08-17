package com.streamhub.platform.common.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Cross-cutting request logging for every REST controller in the
 * application.
 * <p>
 * For each endpoint invocation this logs: HTTP method, request path,
 * execution time (ms), response status (OK/ERROR), and whether the data
 * returned was served from Redis cache or fetched from the database
 * (tracked via {@link ResponseSourceContext}, which individual services set
 * as they execute).
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Around("execution(* com.streamhub.platform..controller..*.*(..))")
    public Object logEndpoint(ProceedingJoinPoint joinPoint) throws Throwable {
        ResponseSourceContext.clear();
        long start = System.currentTimeMillis();

        String httpMethod = "-";
        String path = "-";
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            httpMethod = request.getMethod();
            path = request.getRequestURI();
        }

        String signature = joinPoint.getSignature().toShortString();
        boolean errored = false;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            errored = true;
            throw ex;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            ResponseSourceContext.Source source = ResponseSourceContext.get();
            log.info("[API] {} {} -> {} | {}ms | source={} | status={}",
                    httpMethod, path, signature, durationMs, source, errored ? "ERROR" : "OK");
            ResponseSourceContext.clear();
        }
    }
}

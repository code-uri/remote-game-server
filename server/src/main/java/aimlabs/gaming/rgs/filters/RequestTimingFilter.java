package aimlabs.gaming.rgs.filters;

import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class RequestTimingFilter extends OncePerRequestFilter {

    public RequestTimingFilter() {
        log.info("*** RequestTimingFilter initialized ***");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException e) {
            // Re-throw or handle specifically so Spring's GlobalExceptionHandler can see it
            throw new RuntimeException(e);
        } finally {
            long elapsedNanos = System.nanoTime() - start;
            long elapsedMs = elapsedNanos / 1_000_000;
            String tenant = TenantContextHolder.getTenant();
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();
            log.info("RequestTiming tenant={} method={} uri={} status={} elapsedMs={}", tenant, method, uri, status, elapsedMs);
        }

    }
}
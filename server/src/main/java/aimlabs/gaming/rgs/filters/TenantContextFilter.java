package aimlabs.gaming.rgs.filters;

import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import aimlabs.gaming.rgs.utils.TenantUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Component
//@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    @Autowired
    TenantUtils tenantUtils;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String tenant = tenantUtils.getTenant(request);
        if (tenant != null) {
            MDC.put("tenant", tenant);
        }
        try {
            ScopedValue.where(TenantContextHolder.getScopedValue(), tenant)
                    .run(() -> {
                        try {
                            filterChain.doFilter(request, response);
                        } catch (IOException | ServletException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } finally {
            // Only clean up what we added (tenant)
            // Micrometer will clean up traceId/spanId automatically
            MDC.remove("tenant");
        }
    }
}

package aimlabs.gaming.rgs.filters;

import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import aimlabs.gaming.rgs.utils.TenantUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    @Autowired
    TenantUtils tenantUtils;

    public TenantContextFilter() {
        log.info("*** Tenant context filter initialized ***");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String tenant = tenantUtils.getTenant(request);
        log.info("Tenant found: {}", tenant);

        ScopedValue.where(TenantContextHolder.getScopedValue(), tenant)
                .run(() -> {
                    try {
                        filterChain.doFilter(request, response);
                    } catch (IOException | ServletException e) {
                        // Re-throw or handle specifically so Spring's GlobalExceptionHandler can see it
                        throw new RuntimeException(e);
                    }
                });

    }
}
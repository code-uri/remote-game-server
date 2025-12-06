package aimlabs.gaming.rgs.filters;

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
        String clientId = request.getHeader("X-Client-Id");
        if (clientId == null) {
            clientId = "default";
        }
        String clientSecret = request.getHeader("X-Client-Key");
        if (clientSecret == null) {
            clientSecret = "default";
        }

        log.info("Tenant found: {}", tenant);

        // Store tenant context in request attributes for downstream use
        request.setAttribute("TENANT", tenant);
        request.setAttribute("CLIENT_ID", clientId);
        request.setAttribute("CLIENT_SECRET", clientSecret);

        filterChain.doFilter(request, response);
    }
}
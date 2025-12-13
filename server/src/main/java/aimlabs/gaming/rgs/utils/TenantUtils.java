package aimlabs.gaming.rgs.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import aimlabs.gaming.rgs.tenents.TenantDocument;
import aimlabs.gaming.rgs.tenents.TenantStore;

import java.util.Collection;
import java.util.HashMap;

@Slf4j
@Component
public class TenantUtils implements CommandLineRunner {

    @Autowired
    TenantStore tenantStore;
    HashMap<String, TenantDocument> tenants = new HashMap<>();

    public static String getTenantFromContext(HttpServletRequest request) {
        Object tenant = request.getAttribute("TENANT");
        return tenant != null ? tenant.toString() : "default";
    }

    @Override
    public void run(String... args) throws Exception {
        Collection<TenantDocument> tenantList = tenantStore.findAll(Sort.unsorted());
        tenantList.forEach(tenantDocument -> {
            tenantDocument.getDomains().forEach(domain -> tenants.put(domain, tenantDocument));
        });
        log.info("Loaded all tenants from db: {}", tenants.values());
    }

    public String getTenant(HttpServletRequest request) {
        String remoteHost = request.getHeader("X-Forwarded-Host");
        String host = request.getHeader("Host");
        if (remoteHost == null && host != null) {
            remoteHost = host;
        } else {
            remoteHost = "undefined";
        }

        TenantDocument tenant = tenants.get(remoteHost.toLowerCase().trim());
        if (tenant != null && StringUtils.hasLength(tenant.getUid())) {
            return tenant.getUid();
        } else {
            log.info("remote host {} tenant list {}", remoteHost, tenants);
            return "default";
        }
    }
}
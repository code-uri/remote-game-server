package aimlabs.gaming.rgs.core;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import aimlabs.gaming.rgs.core.dto.SearchRequest;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

public abstract class AbstractEntityCurdController<T extends BaseDto>
        extends EntityController<T> {

    @Override
    @PostMapping
    public T createEntity(@RequestBody T dto) {
        applyTenant(dto);
        return super.createEntity(dto);
    }

    @Override
    @PostMapping("{uid}")
    public T updateEntity(@RequestBody T dto, @PathVariable("uid") String uid) {
        applyTenant(dto);
        return super.updateEntity(dto, uid);
    }

    @Override
    @PutMapping("{id}")
    public T updatePartial(@PathVariable("id") String uid, @RequestBody Map<String, Object> values) {
        applyTenant(values);
        return super.updatePartial(uid, values);
    }

    @Override
    @PostMapping("search")
    public SearchResponse<T> search(@RequestBody SearchRequest searchRequest) {
        applyTenant(searchRequest);
        return super.search(searchRequest);
    }

    private static boolean isSystemTenant(String tenant) {
        return tenant != null && tenant.equalsIgnoreCase("system");
    }

    private static void applyTenant(BaseDto dto) {
        String currentTenant = TenantContextHolder.getTenant();
        if (!isSystemTenant(currentTenant)) {
            dto.setTenant(currentTenant);
            return;
        }

        if (dto.getTenant() == null) {
            dto.setTenant(currentTenant);
        }
    }

    private static void applyTenant(Map<String, Object> values) {
        String currentTenant = TenantContextHolder.getTenant();
        if (!isSystemTenant(currentTenant)) {
            values.put("tenant", currentTenant);
            return;
        }

        values.computeIfAbsent("tenant", k -> currentTenant);
    }

    private static void applyTenant(SearchRequest searchRequest) {
        String currentTenant = TenantContextHolder.getTenant();
        if (!isSystemTenant(currentTenant)) {
            searchRequest.getFilters().put("tenant", List.of(currentTenant));
            return;
        }

        searchRequest.getFilters().computeIfAbsent("tenant", k -> List.of(currentTenant));
    }
}

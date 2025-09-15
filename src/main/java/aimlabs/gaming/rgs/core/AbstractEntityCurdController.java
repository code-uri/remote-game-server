package aimlabs.gaming.rgs.core;


import aimlabs.gaming.rgs.core.entity.BaseDto;

import java.util.List;
import java.util.Map;

public abstract class AbstractEntityCurdController<T extends BaseDto>
        extends EntityController<T> {

//    public Mono<T> createEntity(@RequestBody T dto) {
//        return ReactiveSecurityContextHolder.getContext().flatMap(securityContext -> {
//            UserDetails userDetails = (UserDetails) securityContext.getAuthentication().getPrincipal();
//            if (!userDetails.getTenant().toLowerCase().equals("system"))
//                dto.setTenant(userDetails.getTenant());
//            else if (dto.getTenant() == null)
//                dto.setTenant(userDetails.getTenant());
//
//            return super.createEntity(dto);
//        });
//    }
//
//
//    public Mono<T> updateEntity( @RequestBody T dto, @PathVariable("uid") String uid) {
//        return ReactiveSecurityContextHolder.getContext().flatMap(securityContext -> {
//            UserDetails userDetails = (UserDetails) securityContext.getAuthentication().getPrincipal();
//            if (!userDetails.getTenant().toLowerCase().equals("system"))
//                dto.setTenant(userDetails.getTenant());
//            else if (dto.getTenant() == null)
//                dto.setTenant(userDetails.getTenant());
//
//            return super.updateEntity(dto, uid);
//        });
//    }
//
//
//    public Mono<T> updatePartial(@PathVariable("id") String uid, @RequestBody Map<String, Object> values) {
//        return ReactiveSecurityContextHolder.getContext().flatMap(securityContext -> {
//            UserDetails userDetails = (UserDetails) securityContext.getAuthentication().getPrincipal();
//            if (!userDetails.getTenant().toLowerCase().equals("system"))
//                values.put("tenant", userDetails.getTenant());
//            else values.computeIfAbsent("tenant", k -> userDetails.getTenant());
//
//            return this.getService().updatePartial(uid, values);
//        });
//    }
//
//
//    public Mono<SearchResponse<T>> search(@RequestBody SearchRequest searchRequest) {
//        return ReactiveSecurityContextHolder.getContext().flatMap(securityContext -> {
//            UserDetails userDetails = (UserDetails) securityContext.getAuthentication().getPrincipal();
//            if (!userDetails.getTenant().toLowerCase().equals("system"))
//                searchRequest.getFilters().put("tenant", List.of(userDetails.getTenant()));
//            else searchRequest.getFilters().computeIfAbsent("tenant", k -> List.of(userDetails.getTenant()));
//
//            return this.getService().search(searchRequest);
//        });
//    }
}

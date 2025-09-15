package aimlabs.gaming.rgs.core;


import aimlabs.gaming.rgs.core.dto.SearchRequest;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import aimlabs.gaming.rgs.core.entity.IEntity;
import aimlabs.gaming.rgs.core.entity.Status;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Data
@Slf4j
public abstract class EntityController<E extends IEntity> {

    public abstract IEntityService<E> getService();

    @PostMapping
    public E createEntity(@RequestBody E dto) {
        return getService().create(dto);
    }

    @PostMapping("{uid}")
    public E updateEntity(@RequestBody E dto, @PathVariable("uid") String uid) {
        // TODO: Need to figure out a way to identify if this entity is UID enabled or not.
        // TODO: Build entity metadata inmemory and use it. Or build entity metadata at compile time
        return getService().update(dto);
    }

    @PutMapping("{id}")
    public E updatePartial(@PathVariable("id") String uid, @RequestBody Map<String, Object> values) {
        return getService().updatePartial(uid, values);
    }

    @PutMapping({"/{id}/status/{status}"})
    public void updateStatus(@PathVariable("id") String id,
                                   @PathVariable("status") Status status) {
        getService().updateStatus(id, status);
    }

    @GetMapping("{key}")
    public E findOne(@PathVariable("key") String key) {
        return getService().findOne(key);
    }

    @GetMapping
    public List<E> findAll() {
        return getService().findAll();
    }

    @PostMapping("search")
    public SearchResponse<E> search(@RequestBody SearchRequest request) {
        return getService().search(request);
    }

    @PutMapping("{id}/tags/{tag}")
    public E addTag(@PathVariable("id") String uid, String tag) {
        return getService().addTag(uid, tag);
    }
    @DeleteMapping("{id}/tags/{tag}")
    public E removeTag(@PathVariable("id") String uid, String tag) {
        return getService().removeTag(uid, tag);
    }

    @PutMapping("{id}/tags")
    public E setTags(@PathVariable("id") String uid, @RequestBody List<String> values) {
        return getService().setTags(uid, values);
    }
}

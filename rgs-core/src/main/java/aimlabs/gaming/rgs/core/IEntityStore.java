package aimlabs.gaming.rgs.core;


import aimlabs.gaming.rgs.core.dto.OrderedItem;
import aimlabs.gaming.rgs.core.dto.SearchRequest;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import aimlabs.gaming.rgs.core.entity.IEntity;
import aimlabs.gaming.rgs.core.entity.Status;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface IEntityStore<E extends IEntity> {

    // CURD Operations
    E create(E obj);
    E update(E obj);
    E findOne(String key);
    E findOneByUid(String key);
    void delete(String key);
    void deleteByUid(String key);

    // Additional Queries
    Boolean existsById(String id);
    Boolean existsByUid(String uid);

    // Additional Search/List Methods
    Collection<E> findAllByIdIn(List<String> idList);
    Collection<E> findAllByUidIn(List<String> uidList);
    Collection<E> findAll(Sort sort);
    SearchResponse<E> search(SearchRequest searchRequest);

    // Special Update Operations
    E update(String id, Map<String, Object> values);
    E updateByUid(String uid, Map<String, Object> values);
    E updateByUid(String uid, Map<String, Object> values, boolean upsert);
    default void updateStatus(String key, Status status) {
        throw  new IllegalStateException();
    }
    default void updateMemberStatus(String key, String member, String memberUid, Status status) {
       throw new IllegalStateException();
    }

    default E addTag(String key, String tag) {
       throw new IllegalStateException();
    }

    default E removeTag(String key, String tag) {
       throw  new IllegalStateException();
    }

    default E setTags(String key, String tag) {
        throw new IllegalStateException();
    }



    // Multi Entity Save Operations
    Collection<E> saveAll(List<E> entities);

    default void updateDisplayOrder(List<OrderedItem> items) {
        return;
    }
    default void updateChildrenDisplayOrder(String key, String property, List<OrderedItem> items) {
        return;
    }


    //Method Hooks
    default E beforeCreate(E entity) {
        if (entity.getId() == null)
            entity.setCreatedOn(new Date());
        entity.setModifiedOn(new Date());
        return entity;
    }
    default E afterCreate(E entity) {
        return entity;
    }

    default E beforeUpdate(E entity) {
        entity.setModifiedOn(new Date());
        return entity;
    }

    default E afterUpdate(E entity) {
        return entity;
    }

    default E afterPartialUpdate(E entity, Map<String, Object> values, Boolean upsert) {
        return entity;
    }

    default void beforeDelete(String key) {
        return;
    }

    default void afterDelete(String key) {
        return;
    }
}

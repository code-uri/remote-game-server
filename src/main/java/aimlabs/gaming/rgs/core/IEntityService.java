package aimlabs.gaming.rgs.core;


import aimlabs.gaming.rgs.core.dto.EntityOperation;
import aimlabs.gaming.rgs.core.dto.OrderedItem;
import aimlabs.gaming.rgs.core.dto.SearchRequest;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import aimlabs.gaming.rgs.core.entity.IEntity;
import aimlabs.gaming.rgs.core.entity.Status;

import java.util.List;
import java.util.Map;

public interface IEntityService<T extends IEntity> {

    // CURD Operations
    T create(T obj);
    T update(T obj);
    T findOne(String key);

    T findOneByUid(String key);

    void delete(String key);

    // Additional Search/List Methods
    List<T> findAll();
    List<T> findAllByIdIn(List<String> idList);
    List<T> findAllByUidIn(List<String> uidList);
    SearchResponse<T> search(SearchRequest searchRequest);

    // Special Update Operations
    T updatePartial(String uid, Map<String,Object> values);
    void updateStatus(String key, Status status);
    void updateMemberStatus(String key, String member, String memberUid, Status status);


    // Updated display order for UI sortable items identified by key. Key being UID or ID
    void updateDisplayOrder(List<OrderedItem> items);
    void updateChildrenDisplayOrder(String key, String property, List<OrderedItem> items);

    // Taggable entity support
    T addTag(String key, String tag);

    T removeTag(String key, String tag);

    T setTags(String uid, List<String> tags);


    // Method hooks
    default T validate(T entity, EntityOperation operation) {
        return entity;
    }

    default T beforeCreate(T entity) {
        return entity;
    }

    default T afterCreate(T entity) {
        return entity;
    }

    default T afterPartialUpdate(T entity, Map<String, Object> values, Boolean upsert) {
        return entity;
    }

    default T beforeUpdate(T entity) {
        return entity;
    }

    default T afterUpdate(T entity) {
        return entity;
    }
}

package aimlabs.gaming.rgs.core;

import aimlabs.gaming.rgs.core.dto.EntityOperation;
import aimlabs.gaming.rgs.core.dto.OrderedItem;
import aimlabs.gaming.rgs.core.dto.SearchRequest;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import aimlabs.gaming.rgs.core.entity.IEntity;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.mapper.EntityMapper;
import aimlabs.gaming.rgs.core.utils.ObjectUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Slf4j
public abstract class AbstractEntityService <T extends IEntity, E extends IEntity>
        implements IEntityService<T> {

    protected Class<E> documentClass;
    protected Class<T> dtoClass;
    protected boolean uidEnabled;
    protected boolean multipleAssets;

    public abstract IEntityStore<E> getStore();

    public abstract EntityMapper<T, E> getMapper();

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void init() {
        Type[] types = ObjectUtil.getActualTypeArguments(getClass());
        dtoClass = (Class<T>) types[0];
        documentClass = (Class<E>) types[1];

        // Check if UID is enabled for this entity.
        try {
            Method uidMethod = this.documentClass.getMethod("getUid");
            if (uidMethod != null) this.uidEnabled = true;
        } catch (NoSuchMethodException e) {
            this.uidEnabled = false;
        }

        // Check if media assets is single
        try {
            Method mediaAssetsMethod = this.documentClass.getMethod("getMediaAssets");
            if (mediaAssetsMethod != null) this.multipleAssets = true;
        } catch (NoSuchMethodException e) {
            this.multipleAssets = false;
        }
    }

    @Override
    public T create(T obj) {
        if (this.isUidEnabled()) {
            try {
                Object result = obj.getClass().getMethod("getUid").invoke(obj);
                String uuid = result != null ? String.valueOf(result) : UUID.randomUUID().toString();
                obj.getClass().getMethod("setUid", String.class).invoke(obj, uuid);
            } catch (Exception e) {
                log.error(e.getLocalizedMessage());
                e.printStackTrace();
            }
        }

        return afterCreate(getMapper().asDto(getStore()
                .create(getMapper().asEntity(beforeCreate(obj)))));
    }

    @Override
    public T update(T obj) {
        return afterUpdate(getMapper().asDto(getStore()
                .create(getMapper().asEntity(beforeUpdate(obj)))));
    }

    @Override
    public T findOne(String key) {
         if(!this.isUidEnabled()){
             return getMapper().asDto(getStore().findOne(key));
        }
        else  {
             return getMapper().asDto(getStore().findOneByUid(key));
        }

    }

    @Override
    public T findOneByUid(String key) {
        return !this.isUidEnabled()
                ? getMapper().asDto(getStore().findOne(key))
                : getMapper().asDto(getStore().findOneByUid(key));
    }

    @Override
    public void delete(String key) {
        if(!this.isUidEnabled()){
            getStore().delete(key);
        }
        else  {
            getStore().deleteByUid(key);
        }
    }

    @Override
    public List<T> findAll() {

         return getStore()
                 .findAll(Sort.by("createdOn").descending()).stream().map(e ->   getMapper().asDto(e)).toList();

    }

    @Override
    public List<T> findAllByIdIn(List<String> idList) {
        return getStore()
                .findAllByIdIn((idList)).stream().map(e -> getMapper().asDto(e)).toList();
    }

    @Override
    public List<T> findAllByUidIn(List<String> uidList) {
        return getStore()
                .findAllByUidIn((uidList)).stream().map(e -> getMapper().asDto(e)).toList();
    }

    @Override
    public SearchResponse<T> search(SearchRequest searchRequest) {
        SearchResponse<E> searchResponse
                = getStore().search(searchRequest);

        SearchResponse<T> sr = new SearchResponse<>();
        List<T> items = new ArrayList<>();
        for (E item : searchResponse.getItems()) {
            items.add(getMapper().asDto(item));
        }
        sr.setCount(searchResponse.getCount());
        sr.setItems(items);
        return sr;
    }

    @Override
    public T updatePartial(String uid, Map<String, Object> values) {

        return this.isUidEnabled()
                ? getMapper().asDto(getStore().updateByUid(uid, values))
                : getMapper().asDto(getStore().update(uid, values));
    }

    @Override
    public void updateStatus(String key, Status status) {

         getStore().updateStatus(key, status);
    }

    @Override
    public void updateMemberStatus(String key, String member, String memberUid, Status status) {
         getStore().updateMemberStatus(key, member, memberUid, status);
    }

    @Override
    public void updateDisplayOrder(List<OrderedItem> items) {

         getStore().updateDisplayOrder(items);
    }

    @Override
    public void updateChildrenDisplayOrder(String key, String property, List<OrderedItem> items) {
        getStore().updateChildrenDisplayOrder(key, property, items);
    }

    @Override
    public T addTag(String key, String tag) {

        return getMapper().asDto(getStore().addTag(key, tag));
    }
    @Override
    public T removeTag(String key, String tag) {

        return getMapper().asDto(getStore().removeTag(key, tag));
    }


    @Override
    public T setTags(String uid, List<String> tags) {
        return updatePartial(uid, Map.of("tags", tags ));
    }



    @Override
    public T validate(T entity, EntityOperation operation) {
        return entity;
    }

}

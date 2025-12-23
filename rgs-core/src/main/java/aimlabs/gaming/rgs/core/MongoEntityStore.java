package aimlabs.gaming.rgs.core;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import aimlabs.gaming.rgs.core.dto.OrderedItem;
import aimlabs.gaming.rgs.core.dto.SearchRequest;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import aimlabs.gaming.rgs.core.dto.SortOrder;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.utils.LogUtil;
import aimlabs.gaming.rgs.core.utils.ObjectUtil;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.result.UpdateResult;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Data
@Slf4j
public class MongoEntityStore<E extends EntityDocument> implements IEntityStore<E> {

    boolean fetchParent;
    private Class<E> documentClass;
    private boolean uidEnabled;
    @Autowired
    private MongoTemplate template;
    private String collection;

    @PostConstruct
    public void initialize() {
        Type[] types = ObjectUtil.getActualTypeArguments(getClass());
        Type documentType = types[0];
        if (!(documentType instanceof Class<?> documentClazz)) {
            throw new IllegalStateException("Unable to resolve document class type argument for " + getClass()
                    + ": expected a Class but got " + documentType);
        }
        @SuppressWarnings("unchecked")
        Class<E> resolvedDocumentClass = (Class<E>) documentClazz;
        this.documentClass = resolvedDocumentClass;

        org.springframework.data.mongodb.core.mapping.Document documentAnnotation = this.documentClass
                .getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class);

        collection = documentAnnotation != null ? documentAnnotation.value()
                : documentClass.getSimpleName().toLowerCase().charAt(0) + documentClass.getSimpleName().substring(1);
        // Check if UID is enabled for this entity.
        try {
            Method uidMethod = this.documentClass.getMethod("getUid");
            if (uidMethod != null)
                this.uidEnabled = true;
        } catch (NoSuchMethodException e) {
            this.uidEnabled = false;
        }

        try {
            Method getParentDocument = this.documentClass.getMethod("getParentDocument");
            this.fetchParent = true;
        } catch (NoSuchMethodException e) {
        }

    }

    public Sort getSort(SearchRequest searchRequest) {
        List<Sort.Order> sortOrders = new ArrayList<>();
        for (SortOrder so : searchRequest.getSort()) {
            Sort.Order sortOrder = Sort.Order.by(so.getProperty()).with(Sort.Direction.fromString(so.getDirection()));
            if (so.isIgnoreCase()) {
                sortOrder = sortOrder.ignoreCase();
            }
            sortOrders.add(sortOrder);
        }
        return !sortOrders.isEmpty() ? Sort.by(sortOrders) : Sort.unsorted();
    }

    @Override
    public E create(E entity) {
        entity.setTenant(TenantContextHolder.getTenant());

        return afterCreate(getTemplate().save(beforeCreate(entity)));
    }

    @Override
    public E update(E entity) {
        return afterUpdate(getTemplate().save(beforeUpdate(entity)));
    }

    @Override
    public E addTag(String key, String tag) {
        Update update = new Update();
        update.push(key, tag);
        Criteria criteria = where(isUidEnabled() ? "uid" : "id").is(key)
                .and("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant());
        return getTemplate()
                .findAndModify(query(criteria), update, documentClass);
    }

    @Override
    public E removeTag(String key, String tag) {
        Update update = new Update();
        update.pull(key, tag);
        Criteria criteria = where(isUidEnabled() ? "uid" : "id").is(key)
                .and("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant());
        return getTemplate()
                .findAndModify(query(criteria), update, documentClass);
    }

    @Override
    public E setTags(String key, String tag) {

        return update(key, Map.of("tags", tag));
    }

    @Override
    public E findOne(String key) {
        Criteria criteria = where(isUidEnabled() ? "uid" : "id").is(key)
                .and("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant());
        return getTemplate()
                .findOne(query(criteria), documentClass);
    }

    @Override
    public E findOneByUid(String key) {
        Criteria criteria = where(isUidEnabled() ? "uid" : "id").is(key)
                .and("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant());
        return getTemplate()
                .findOne(query(criteria), documentClass);
    }

    @Override
    public void delete(String key) {
        Update update = new Update();
        update.set("deleted", true);
        update.set("modifiedOn", new Date());

        Criteria criteria = where("id").is(key)
                .and("tenant").is(TenantContextHolder.getTenant());
        beforeDelete(key);

        getTemplate()
                .findAndModify(query(criteria), update, documentClass);
        afterDelete(key);
    }

    @Override
    public void deleteByUid(String key) {
        Update update = new Update();
        update.set("deleted", true);
        update.set("modifiedOn", new Date());

        Criteria criteria = where(this.isUidEnabled() ? "uid" : "id").is(key)
                .and("tenant").is(TenantContextHolder.getTenant());
        beforeDelete(key);
        getTemplate()
                .findAndModify(query(criteria), update, documentClass);
        afterDelete(key);
    }

    @Override
    public Boolean existsById(String id) {
        Criteria criteria = where("id").is(id)
                .and("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant());
        return getTemplate()
                .exists(query(criteria), documentClass);
    }

    @Override
    public Boolean existsByUid(String uid) {
        Criteria criteria = where(this.isUidEnabled() ? "uid" : "id").is(uid)
                .and("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant());
        return getTemplate()
                .exists(query(criteria), documentClass);
    }

    @Override
    public Collection<E> findAllByIdIn(List<String> idList) {
        Criteria criteria = where("id").in(idList)
                .and("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant());
        return getTemplate().find(query(criteria), documentClass);
    }

    @Override
    public Collection<E> findAllByUidIn(List<String> uidList) {
        Criteria criteria = where(this.isUidEnabled() ? "uid" : "id").in(uidList)
                .and("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant());
        return getTemplate().find(query(criteria), documentClass);
    }

    @Override
    public Collection<E> findAll(Sort sort) {
        Criteria criteria = where("deleted").is(false)
                .and("tenant").is(TenantContextHolder.getTenant());
        return getTemplate().find(query(criteria), documentClass);
    }

    @Override
    public SearchResponse<E> search(SearchRequest searchRequest) {
        log.info("{}", searchRequest);

        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();
        if (searchRequest.getQueryProperties() != null && !searchRequest.getQueryProperties().isEmpty()
                && searchRequest.getQ() != null) {
            for (String prop : searchRequest.getQueryProperties()) {
                criteriaList.add(Criteria.where(prop).regex(searchRequest.getQ(), "i"));
            }
        }

        if (searchRequest.getQ() != null) {
            TextCriteria t = TextCriteria.forDefaultLanguage().matching(searchRequest.getQ());
            if (!criteriaList.isEmpty()) {
                query.addCriteria(new Criteria().orOperator(criteriaList));
            } else {
                query.addCriteria(t);
            }

        }

        // Read properties
        if (searchRequest.getProperties() != null && !searchRequest.getProperties().isEmpty()) {
            for (String prop : searchRequest.getProperties().keySet()) {
                query.addCriteria(Criteria.where(prop).is(searchRequest.getProperties().get(prop)));
            }
        }

        // Read Filters
        if (searchRequest.getFilters() != null && !searchRequest.getFilters().isEmpty()) {
            for (String filter : searchRequest.getFilters().keySet()) {
                if (!searchRequest.getFilters().get(filter).isEmpty()) {
                    query.addCriteria(Criteria.where(filter).in(searchRequest.getFilters().get(filter)));
                }
            }
        }

        // Non null properties
        if (searchRequest.getNonNullProperties() != null && !searchRequest.getNonNullProperties().isEmpty()) {
            for (String prop : searchRequest.getNonNullProperties()) {
                query.addCriteria(Criteria.where(prop).ne(null));
            }
        }

        if (searchRequest.getSize() < 1) {
            searchRequest.setSize(25);
        }
        // Read Page Information
        query.collation(Collation.of("en"));

        if (searchRequest.getFrom() != null && searchRequest.getTo() != null) {
            query.addCriteria(Criteria
                    .where(searchRequest.getRangeProperty())
                    .gte(searchRequest.getFrom())
                    .lt(searchRequest.getTo()));
        } else if (searchRequest.getFrom() != null) {
            query.addCriteria(Criteria
                    .where(searchRequest.getRangeProperty())
                    .gte(searchRequest.getFrom()));
        } else if (searchRequest.getTo() != null) {
            query.addCriteria(Criteria
                    .where(searchRequest.getRangeProperty())
                    .lt(searchRequest.getTo()));
        }

        Criteria criteria = where("deleted").is(false);
        if (!searchRequest.getFilters().containsKey("tenant") && !searchRequest.getProperties().containsKey("tenant"))
            criteria.and("tenant").is(TenantContextHolder.getTenant());

        query.addCriteria(criteria);
        SearchResponse<E> searchResponse = new SearchResponse<>();
        log.info(query.toString());
        long count = getTemplate().count(query, documentClass);

        query.with(PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), getSort(searchRequest)));
        searchResponse.setCount(count);
        List<E> asd = getTemplate().find(query, documentClass);
        searchResponse.setItems(asd);

        return searchResponse;
    }

    @Override
    public E update(String id, Map<String, Object> values) {
        Update update = new Update();
        update.set("modifiedOn", new Date());
        values.forEach(update::set);
        update.set("tenant", TenantContextHolder.getTenant());
        return getTemplate()
                .findAndModify(query(where("id").is(id)
                        .and("tenant").is(TenantContextHolder.getTenant())),
                        update,
                        FindAndModifyOptions.options().returnNew(true),
                        documentClass);
    }

    @Override
    public E updateByUid(String uid, Map<String, Object> values) {
        Update update = new Update();
        update.set("modifiedOn", new Date());
        values.forEach(update::set);
        update.set("tenant", TenantContextHolder.getTenant());
        E as = getTemplate()
                .findAndModify(query(where("uid").is(uid)
                        .and("tenant").is(TenantContextHolder.getTenant())), update,
                        FindAndModifyOptions.options().returnNew(true),
                        documentClass);

        return afterPartialUpdate(as, values, false);
    }

    @Override
    public E updateByUid(String uid, Map<String, Object> values, boolean upsert) {
        Update update = new Update();
        update.set("modifiedOn", new Date());
        values.forEach(update::set);
        update.set("tenant", TenantContextHolder.getTenant());
        E e = getTemplate()
                .findAndModify(query(where("uid").is(uid)
                        .and("tenant").is(TenantContextHolder.getTenant())),
                        update,
                        FindAndModifyOptions.options().returnNew(true).upsert(upsert),
                        documentClass);

        return afterPartialUpdate(e, values, upsert);
    }

    @Override
    public void updateStatus(String key, Status status) {

        Query query = new Query();
        query.addCriteria(where(isUidEnabled() ? "uid" : "id").is(key)
                .and("tenant").is(TenantContextHolder.getTenant()));
        Update update = new Update();
        update.set("status", status);
        update.set("modifiedOn", new Date());
        UpdateResult updateResult = getTemplate()
                .updateFirst(query, update,
                        documentClass);

    }

    @Override
    public void updateMemberStatus(String key, String member, String memberUid, Status status) {
        Query query = new Query();
        query.addCriteria(where(isUidEnabled() ? "uid" : "id").is(key)
                .and("tenant").is(TenantContextHolder.getTenant()));
        Update update = new Update();
        update.set(member + ".$[member].status", status);
        update.filterArray(where("member.uid").is(memberUid));
        update.set("modifiedOn", new Date());
        UpdateResult updateResult = getTemplate()
                .updateFirst(query, update, documentClass);
    }

    @Override
    public Collection<E> saveAll(List<E> entities) {

        return getTemplate()
                .insertAll(entities.stream().peek(e -> e.setTenant(TenantContextHolder.getTenant())).toList());
    }

    @Override
    public void updateDisplayOrder(List<OrderedItem> items) {

        String queryProperty = isUidEnabled() ? "uid" : "id";
        String displayOrderProperty = "displayOrder";
        List<UpdateOneModel<Document>> updates = new ArrayList<>();
        items.forEach(orderedItem -> {
            // TODO: Deprecate in favor of using "key" property
            var filter = new Document(queryProperty, orderedItem.getKey());
            UpdateOneModel<Document> update = new UpdateOneModel<Document>(filter,
                    new Document("$set", new Document(Map.of(displayOrderProperty, orderedItem.getDisplayOrder()))));
            updates.add(update);
        });

        BulkWriteResult aas = getTemplate()
                .getCollection(getTemplate().getCollectionName(documentClass))
                .bulkWrite(updates);
        LogUtil.logEntity(aas);

    }

    @Override
    public void updateChildrenDisplayOrder(String key, String property, List<OrderedItem> items) {
        String queryProperty = isUidEnabled() ? "uid" : "id";
        String displayOrderProperty = "displayOrder";

        Query query = new Query();
        query.addCriteria(where(queryProperty).is(key).and("tenant").is(TenantContextHolder.getTenant()));
        Update update = new Update();
        update.set("modifiedOn", new Date());
        items.forEach(item -> {
            update.set(property + ".$[uid" + item.getKey().replace("-", "") + "]." + displayOrderProperty,
                    item.getDisplayOrder());
            update.filterArray(where("uid" + item.getKey().replace("-", "") + ".uid").is(item.getKey()));
        });

        getTemplate()
                .updateFirst(query, update, documentClass);
    }
}

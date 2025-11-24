// java
        package aimlabs.gaming.rgs.brands;

        import aimlabs.gaming.rgs.core.MongoEntityStore;

        import aimlabs.gaming.rgs.core.entity.Status;
        import lombok.Data;
        import lombok.extern.slf4j.Slf4j;
        import org.springframework.data.mongodb.core.query.Collation;
        import org.springframework.stereotype.Service;

        import jakarta.annotation.PostConstruct;
        import org.springframework.data.domain.Sort;

        import org.springframework.data.mongodb.core.index.Index;
        import org.springframework.data.mongodb.core.query.Criteria;
        import org.springframework.data.mongodb.core.query.Query;

        import java.util.Locale;

        @Data
        @Slf4j
        @Service
        public class BrandStore extends MongoEntityStore<BrandDocument> {

            @PostConstruct
            void ensureIndexes() {
                getTemplate().indexOps(BrandDocument.class).createIndex(
                        new Index("uid", Sort.Direction.ASC)
                                .background()
                                .collation(Collation.of(Locale.ENGLISH)
                                        .strength(Collation.ComparisonLevel.secondary()))
                );
            }

            public BrandDocument findOneByTenantAndBrand(String tenant, String brand) {
                Query query = Query.query(
                        Criteria.where("deleted").is(false)
                                .and("tenant").is(tenant)
                                .and("uid").is(brand)
                                .and("status").is(Status.ACTIVE)
                );
                return getTemplate().findOne(query, BrandDocument.class);
            }

            public BrandDocument findOneByUidAndNetwork(String uid, String network) {
                Query query = Query.query(
                        Criteria.where("deleted").is(false)
                                .and("network").is(network)
                                .and("uid").is(uid)
                                .and("status").is(Status.ACTIVE)
                );
                return getTemplate().findOne(query, BrandDocument.class);
            }
        }
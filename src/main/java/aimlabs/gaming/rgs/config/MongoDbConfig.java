package aimlabs.gaming.rgs.config;


import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.javamoney.moneta.Money;
import org.springframework.boot.autoconfigure.domain.EntityScanner;

import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.util.Arrays.asList;

@Slf4j
@Configuration
//@AutoConfigureBefore(MongoDataAutoConfiguration.class)
public class MongoDbConfig {

    // @Bean
	// MongoDatabaseFactorySupport<?> mongoDatabaseFactory(MongoClient mongoClient, MongoProperties properties,
	// 		MongoConnectionDetails connectionDetails) {
	// 	String database = properties.getDatabase();
	// 	if (database == null) {
	// 		database = connectionDetails.getConnectionString().getDatabase();
	// 	}
	// 	return new SimpleMongoClientDatabaseFactory(mongoClient, database);
	// }


    @Bean
    MongoMappingContext mongoMappingContext(ApplicationContext applicationContext,
                                               MongoCustomConversions mongoCustomConversions) throws ClassNotFoundException {
        PropertyMapper mapper = PropertyMapper.get().alwaysApplyingWhenNonNull();
        MongoMappingContext context = new MongoMappingContext();
        mapper.from(true).to(context::setAutoIndexCreation);
        context.setInitialEntitySet(new EntityScanner(applicationContext).scan(org.springframework.data.mongodb.core.mapping.Document.class, Persistent.class));
        context.setSimpleTypeHolder(mongoCustomConversions.getSimpleTypeHolder());
        return context;
    }

    // @Bean
    // public MappingMongoConverter mappingMongoConverter(MongoMappingContext mongoMappingContext,
    //                                                          MongoCustomConversions mongoCustomConversions) {
    //     MappingMongoConverter mappingConverter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mongoMappingContext);
    //     mappingConverter.setCustomConversions(mongoCustomConversions);
    //     return mappingConverter;
    // }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        log.info("added mongoCustomConversions");
        return new MongoCustomConversions(asList(
                // writing converter, reader converter
                new BigDecimalDecimal128Converter(), new Decimal128BigDecimalConverter(), new MoneyReadConverter(),
                new MoneyWriteConverter()
        ));
    }

    @ReadingConverter
    public static class MoneyReadConverter implements Converter<Document, MonetaryAmount> {
        
        public Money convert(Document money) {
            //Long amount = (Long) money.get("amount");
            Decimal128 amount = (Decimal128) money.get("amount");
            String currency = (String) money.get("currency");
            CurrencyUnit cu = Monetary.getCurrency(currency);
            //Integer fractionDigits = (Integer) money.get("fractionDigits");
            //return Money.ofMinor(cu, amount);
            return Money.of(amount.bigDecimalValue(), cu);
        }
    }

    @WritingConverter
    public static class MoneyWriteConverter implements Converter<MonetaryAmount, Document> {
        
        public Document convert(MonetaryAmount money) {
            Document moneyDBObject = new Document();

            moneyDBObject.put("amount", money.getNumber().numberValue(BigDecimal.class));
            //moneyDBObject.put("fractions", money.getCurrency().getDefaultFractionDigits());
            moneyDBObject.put("currency", money.getCurrency().getCurrencyCode());
            return moneyDBObject;
        }
    }

    @WritingConverter
    public class BigDecimalDecimal128Converter implements Converter<BigDecimal, Decimal128> {

        
        public Decimal128 convert(BigDecimal source) {
            return new Decimal128(source);
        }
    }

    @ReadingConverter
    public class Decimal128BigDecimalConverter implements Converter<Decimal128, BigDecimal> {

        
        public BigDecimal convert(Decimal128 source) {
            return source.bigDecimalValue();
        }
    }


    @ReadingConverter
    public class StringToDateConverter implements Converter<String, Date> {

        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

        @Override
        public Date convert(String source) {
            try {
                return dateFormat.parse(source);
            } catch (ParseException e) {
                throw new RuntimeException("Failed to parse date: " + source, e);
            }
        }
    }

 /*   @WritingConverter
    public class DateToStringConverter implements Converter<Date, String> {

        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

        @Override
        public String convert(Date source) {
            return dateFormat.format(source);
        }
    }*/
}
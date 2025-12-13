package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;

@Document("ExchangeRates")
public class ExchangeRateDocument extends EntityDocument {

    LocalDate date;
    String from;

    String to;

    @Field("exchageRate")
    String exchangeRate;
}

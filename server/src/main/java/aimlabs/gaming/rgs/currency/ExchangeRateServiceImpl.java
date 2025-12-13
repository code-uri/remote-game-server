package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import lombok.Data;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.entity.Status;

@Data
@Service
public class ExchangeRateServiceImpl extends AbstractEntityService<ExchangeRate, ExchangeRateDocument>
        implements IExchangeRateService {

    @Autowired
    ExchangeRateStore store;

    @Autowired
    ExchangeRateMapper mapper;

    @Autowired
    MongoTemplate exchangeRatesMongoTemplate;

    public Double getRate(String from, String to) {

        Query query = Query.query(Criteria.where("from").is(from)
                .and("to").is(to)
                .and("status").is(Status.ACTIVE))
                .with(Sort.by(Sort.Direction.DESC, "modifiedOn"))
                .limit(1);
        query.fields().include("exchageRate");

        Document document = exchangeRatesMongoTemplate.findOne(query, Document.class, "ExchangeRates");

        if (document == null) {
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR,
                    "Failed to get exchange rate. from " + from + ", to " + to);
        }

        if (document.get("exchageRate") instanceof Double) {
            return document.getDouble("exchageRate");
        } else if (document.get("exchageRate") instanceof Integer) {
            return Double.valueOf(document.getInteger("exchageRate"));
        } else {
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR,
                    "Failed to get exchange rate. from " + from + ", to " + to);
        }
    }
}
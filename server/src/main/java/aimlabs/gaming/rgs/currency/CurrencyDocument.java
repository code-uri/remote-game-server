package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(
    collection = "Currencies"
)
@CompoundIndexes({@CompoundIndex(
    name = "deleted_1_tenant_1_code_1",
    def = "{deleted: 1,tenant:1, code : 1}",
    unique = true
)})
public class CurrencyDocument extends EntityDocument {
    private String code;
    private int numericCode;
    private String name;
    private String type;
    private boolean iso = false;
    private String description;
    private int fractionalDigits;

    public String getCode() {
        return this.code;
    }

    public int getNumericCode() {
        return this.numericCode;
    }

    public String getName() {
        return this.name;
    }

    public String getType() {
        return this.type;
    }

    public boolean isIso() {
        return this.iso;
    }

    public String getDescription() {
        return this.description;
    }

    public int getFractionalDigits() {
        return this.fractionalDigits;
    }

    public CurrencyDocument setCode(final String code) {
        this.code = code;
        return this;
    }

    public CurrencyDocument setNumericCode(final int numericCode) {
        this.numericCode = numericCode;
        return this;
    }

    public CurrencyDocument setName(final String name) {
        this.name = name;
        return this;
    }

    public CurrencyDocument setType(final String type) {
        this.type = type;
        return this;
    }

    public CurrencyDocument setIso(final boolean iso) {
        this.iso = iso;
        return this;
    }

    public CurrencyDocument setDescription(final String description) {
        this.description = description;
        return this;
    }

    public CurrencyDocument setFractionalDigits(final int fractionalDigits) {
        this.fractionalDigits = fractionalDigits;
        return this;
    }
}

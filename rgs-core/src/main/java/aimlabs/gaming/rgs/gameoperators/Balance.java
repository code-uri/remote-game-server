package aimlabs.gaming.rgs.gameoperators;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Balance
 */
@Data
public class Balance {
    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("onHold")
    private BigDecimal onHold;

    @JsonProperty("total")
    private BigDecimal total;

    public Balance amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Balance onHold(BigDecimal onHold) {
        this.onHold = onHold;
        return this;
    }

    public BigDecimal getOnHold() {
        return onHold;
    }

    public void setOnHold(BigDecimal onHold) {
        this.onHold = onHold;
    }

    public Balance total(BigDecimal total) {
        this.total = total;
        return this;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}

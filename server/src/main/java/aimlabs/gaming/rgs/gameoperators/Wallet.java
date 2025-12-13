package aimlabs.gaming.rgs.gameoperators;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Wallet
 */
@Data
public class Wallet {

    private String currency;


    private Balance cash;


    private Balance bonus;

    private BigDecimal totalBalance;

    public Wallet currency(String currency) {
        this.currency = currency;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Wallet cash( Balance cash) {
        this.cash = cash;
        return this;
    }

    /**
     * Get cash
     *
     * @return cash
     */
    public Balance getCash() {
        return cash;
    }

    public void setCash( Balance cash) {
        this.cash = cash;
    }

    public Wallet bonus( Balance bonus) {
        this.bonus = bonus;
        return this;
    }

    /**
     * Get bonus
     *
     * @return bonus
     */
    public Balance getBonus() {
        return bonus;
    }

    public void setBonus(Balance bonus) {
        this.bonus = bonus;
    }

    public Wallet totalBalance(BigDecimal totalBalance) {
        this.totalBalance = totalBalance;
        return this;
    }

    /**
     * total available balance includes bonus.
     *
     * @return totalBalance
     */

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(BigDecimal totalBalance) {
        this.totalBalance = totalBalance;
    }

}


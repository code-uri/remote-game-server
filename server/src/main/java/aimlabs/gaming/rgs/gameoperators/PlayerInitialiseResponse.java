package aimlabs.gaming.rgs.gameoperators;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * PlayerInitialiseResponse
 */
@Data
public class PlayerInitialiseResponse {
    private String externalToken;
    private String playerId;
    private String currency;
    private Balance cash;
    private Balance bonus;
    private BigDecimal totalBalance;
    private Map<String, Object> regulationSettings;
    //private PlayerSettings playerSettings;
    private List<String> tags;
    private boolean supportsMultiCredits = true;

    @JsonIgnore
    public Wallet getWallet() {
        Wallet wallet = new Wallet();
        Balance cash = new Balance().amount(BigDecimal.ZERO).total(BigDecimal.ZERO).onHold(BigDecimal.ZERO);
        if (getCash() != null) {
            if (getCash().getAmount() != null)
                cash.setAmount(getCash().getAmount());
            if (getCash().getOnHold() != null)
                cash.setOnHold(getCash().getOnHold());
            if (getCash().getTotal() != null)
                cash.setTotal(getCash().getTotal());
        }

        Balance bonus = new Balance().amount(BigDecimal.ZERO).total(BigDecimal.ZERO).onHold(BigDecimal.ZERO);
        if (getBonus() != null) {
            if (getBonus().getAmount() != null)
                bonus.setAmount(getBonus().getAmount());
            if (getBonus().getOnHold() != null)
                bonus.setOnHold(getBonus().getOnHold());
            if (getBonus().getTotal() != null)
                bonus.setTotal(getBonus().getTotal());
        }

        wallet.setCash(cash);
        wallet.setBonus(bonus);
        wallet.setTotalBalance(getTotalBalance());
        wallet.setCurrency(getCurrency());
        return wallet;
    }

    public void unWrapWallet(Wallet wallet) {
        this.cash = wallet.getCash();
        this.bonus = wallet.getBonus();
        this.totalBalance = wallet.getTotalBalance();
        this.currency = wallet.getCurrency();
    }
}


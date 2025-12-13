package aimlabs.gaming.rgs.players;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.money.MonetaryAmount;

@Data
@NoArgsConstructor
public class WalletBalance {

    protected MonetaryAmount balance;

    protected MonetaryAmount onHold;

    protected MonetaryAmount availableBalance;

    protected WalletBalanceType type = WalletBalanceType.CASH;


    public WalletBalance(MonetaryAmount balance, MonetaryAmount onHold, MonetaryAmount availableBalance, WalletBalanceType type) {
        this.balance = balance;
        this.onHold = onHold;
        this.availableBalance = availableBalance;
        this.type = type;
    }
}

package aimlabs.gaming.rgs.players;

import lombok.Data;

import javax.money.MonetaryAmount;


@Data
public class PlayerWallet {

    protected  WalletBalance cash;

    protected WalletBalance bonus;

    protected MonetaryAmount totalAvailable;

    protected String currency;

    protected int fractions;
}
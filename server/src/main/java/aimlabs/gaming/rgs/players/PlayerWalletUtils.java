package aimlabs.gaming.rgs.players;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameoperators.Balance;
import aimlabs.gaming.rgs.gameoperators.Wallet;
import org.javamoney.moneta.Money;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;

public class PlayerWalletUtils {

    public static PlayerWallet zeroBalance(String currency) {
        PlayerWallet pw = new PlayerWallet();
        CurrencyUnit cu = Monetary.getCurrency(currency);
        WalletBalance zeroCash = new WalletBalance();
        pw.setCash(zeroCash);
        pw.setTotalAvailable(Money.zero(cu));
        return pw;
    }
    public static PlayerWallet asPlayerWallet(Wallet wallet) {
        if (wallet == null)
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Wallet is null");

        if (wallet.getCash() == null || wallet.getCash().getAmount() == null || wallet.getCash().getTotal() == null)
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Invalid cash balance " + wallet);

        if (wallet.getTotalBalance() == null)
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Wallet totalBalance is null");


        PlayerWallet pw = new PlayerWallet();
        CurrencyUnit cu = Monetary.getCurrency(wallet.getCurrency());

        WalletBalance cash = new WalletBalance();
        cash.setBalance(Money.of(wallet.getCash().getAmount(), cu));
        if (wallet.getCash().getOnHold() != null)
            cash.setOnHold(Money.of(wallet.getCash().getOnHold(), cu));
        cash.setAvailableBalance(Money.of(wallet.getCash().getTotal(), cu));
        cash.setType(WalletBalanceType.CASH);

        if (wallet.getBonus() != null && wallet.getBonus().getAmount()!=null && wallet.getBonus().getTotal()!=null) {
            WalletBalance bonus = new WalletBalance();
            bonus.setBalance(Money.of(wallet.getBonus().getAmount(), cu));
            if (wallet.getBonus().getOnHold() != null)
                bonus.setOnHold(Money.of(wallet.getBonus().getOnHold(), cu));
            bonus.setAvailableBalance(Money.of(wallet.getBonus().getTotal(), cu));
            bonus.setType(WalletBalanceType.BONUS);
            pw.setBonus(bonus);
        }
        pw.setCash(cash);
        pw.setTotalAvailable(Money.of(wallet.getTotalBalance(), cu));
        pw.setCurrency(wallet.getCurrency());
        pw.setFractions(cu.getDefaultFractionDigits());
        return pw;
    }

    public static Wallet asWallet(PlayerWallet playerWallet) {
        Wallet wallet = new Wallet();
        Balance cash = new Balance().amount(BigDecimal.ZERO).total(BigDecimal.ZERO).onHold(BigDecimal.ZERO);
        if (playerWallet.getCash() != null) {
            if (playerWallet.getCash().getBalance() != null)
                cash.setAmount(playerWallet.getCash().getBalance().getNumber().numberValueExact(BigDecimal.class));
            if (playerWallet.getCash().getOnHold() != null)
                cash.setOnHold(playerWallet.getCash().getOnHold().getNumber().numberValueExact(BigDecimal.class));
            if (playerWallet.getCash().getAvailableBalance() != null)
                cash.setTotal(playerWallet.getCash().getAvailableBalance().getNumber().numberValueExact(BigDecimal.class));
        }

        Balance bonus = new Balance().amount(BigDecimal.ZERO).total(BigDecimal.ZERO).onHold(BigDecimal.ZERO);
        if (playerWallet.getBonus() != null) {
            if (playerWallet.getBonus().getBalance() != null)
                bonus.setAmount(playerWallet.getBonus().getBalance().getNumber().numberValueExact(BigDecimal.class));
            if (playerWallet.getBonus().getOnHold() != null)
                bonus.setOnHold(playerWallet.getBonus().getOnHold().getNumber().numberValueExact(BigDecimal.class));
            if (playerWallet.getBonus().getAvailableBalance() != null)
                bonus.setTotal(playerWallet.getBonus().getAvailableBalance().getNumber().numberValueExact(BigDecimal.class));
        }

        wallet.setCash(cash);
        wallet.setBonus(bonus);
        wallet.setTotalBalance(playerWallet.getTotalAvailable().getNumber().numberValue(BigDecimal.class));
        wallet.setCurrency(playerWallet.getCurrency());
        //wallet.setFractions(playerWallet.getTotalAvailable().getCurrency().getDefaultFractionDigits());
        return wallet;
    }


    public static Wallet getWallet(String currency, BigDecimal amount) {
        Wallet wallet = new Wallet();
        wallet.setCurrency(currency);
        wallet.setCash(new Balance().amount(amount).total(amount));
        wallet.setTotalBalance(wallet.getCash().getTotal());
        return wallet;
    }

}

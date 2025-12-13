package aimlabs.gaming.rgs.gameoperators;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerGameTransaction {
    BigDecimal amount;
    String txnId;

}

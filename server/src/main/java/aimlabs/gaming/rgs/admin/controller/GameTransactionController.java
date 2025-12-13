package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.transactions.Transaction;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import aimlabs.gaming.rgs.transactions.TransactionService;
import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@RestController
@RequestMapping("/admin/game-transactions")
@SecuredEndpoint
public class GameTransactionController extends AbstractReadOnlyEntityCurdController<Transaction> {

    @Autowired
    private TransactionService service;

}

package in.aimlabs.gaming.gconnect.astroplay.service;

import in.aimlabs.gaming.gconnect.astroplay.dto.*;
import reactor.core.publisher.Mono;

public interface AstroPlayService {
    Mono<IntegrationMinimalDto> getIntegration(String providerId, long operatorId);

    Mono<BalanceResponse> init(IntegrationsBaseBalanceRequest integrationsBaseBalanceRequest);

    Mono<BalanceResponse> getPlayerBalance(IntegrationsBaseBalanceRequest integrationsBaseBalanceRequest);

    Mono<SessionState> checkSession(CheckGameSessionRequest checkGameSessionRequest);

    Mono<BalanceResponse> processPlay(ProcessBetWinRequest processBetWinRevokeRequest);

    Mono<BalanceResponse> processRevoke(ProcessRevokeRequest processRevokeRequest);

    Mono<BalanceResponse> processPartialRefund(ProcessRevokeRequest processPartialRefundRequest);

//    Mono<GameTransactionResponse> getCurrentTransaction(GetTransactionRequest getTransactionRequest);
//
//    Mono<GameTransactionResponse> getTransactionByProviderTrnId(GetTransactionByProviderTrnIdRequest getTransactionRequest);
//
//    Mono<GameTransactionResponse> getWinTransactionByProviderTrnId(GetTransactionByProviderTrnIdRequest getTransactionRequest);

    Mono<UpdateSessionDataResponse> updateGameSession(UpdateSessionDataRequest updateSessionDataRequest);

/*    Mono<SessionState> generateNewGameSession(NewGameSessionRequest newSessionDataRequest);

    Mono<IsRoundFinishedResponse> getIsRoundFinishedRequestByRoundId(GetIsRoundFinishedRequest isRoundFinishedRequest);

    Mono<GameTransactionResponse> getRevokedTransactionByProviderTrnId(GetTransactionByProviderTrnIdRequest getTransactionRequest);*/
}
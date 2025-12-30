// java
package aimlabs.gaming.rgs.gameoperators;

import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.core.exceptions.BaseException;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.transactions.TransactionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

import static aimlabs.gaming.rgs.transactions.TransactionType.*;

@Slf4j
@Getter
@Component
public class DefaultPlatformV1PlayerServiceManager implements PlayerAccountManagerFactory {

    // \@Value("\${rgs.player.connector.default.uid:generic-connector-v2}")
    String connectorUid = "generic-connector-v1";

    @Value("${rgs.player.connector.retries:3}")
    private String transactionRetries;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestClient.Builder restClientBuilder;


    @Override
    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
               || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new PlayerServiceConnector(connector);
    }

    class PlayerServiceConnector implements PlayerAccountManager {

        private final Connector connector;
        private final RestClient restClient;

        PlayerServiceConnector(Connector connector) {
            this.connector = connector;
            this.restClient = restClientBuilder.baseUrl(connector.getBaseUrl()).build();
        }


        private <TReq, TRes> TRes postForObject(String path, TReq body, Class<TRes> responseType) {
            long startMillis = System.currentTimeMillis();

            try {
                TRes result = restClient.post()
                        .uri(path)
                        .body(body)
                        .retrieve()
                        .body(responseType);

                if (result == null) {
                    throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);
                }

                log.info("HTTP {} -> {} response {}. Elapsed Time: {}ms",
                         path, responseType.getSimpleName(), result,
                         System.currentTimeMillis() - startMillis);
                return result;
            } catch (RestClientException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SocketTimeoutException) {
                    log.error("Timeout calling {} for body {}", path, body, e);
                } else {
                    log.error("HTTP error calling {} for body {}", path, body, e);
                }
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            } catch (Exception e) {
                log.error("Unexpected error calling {} for body {}", path, body, e);
                if (e instanceof BaseRuntimeException bre) {
                    throw bre;
                }
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e);
            }
        }

        private PlayerTransactionResponse rollbackSync(PlayerTransactionRequestV1 request) {
            // simple wrapper using same POST logic
            try {
                return postForObject("/player-transaction",
                        request, PlayerTransactionResponse.class);
            } catch (BaseRuntimeException e) {
                log.error("Generic player adaptor rollback {} failed.",
                          request.getTxnId(), e);
                throw e;
            }
        }

        @Override
        public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {
            long startMillis = System.currentTimeMillis();
            try {
                log.info("playerInitialise request {}", request);
                PlayerInitialiseResponse response = postForObject(
                        "/player-initialise",
                        request,
                        PlayerInitialiseResponse.class
                );
                log.info("playerInitialise response {}. Elapsed Time: {}ms",
                         response, System.currentTimeMillis() - startMillis);
                return response;
            } catch (BaseRuntimeException e) {
                log.error("Generic player adaptor playerInitialise request failed.", e);
                throw e;
            }
        }

        @Override
        public Wallet playerBalance(PlayerBalanceRequest request) {
            long startMillis = System.currentTimeMillis();
            try {
                log.info("playerBalance request {}", request);
                Wallet response = postForObject(
                        "/player-balance",
                        request,
                        Wallet.class
                );
                log.info("playerBalance response {}. Elapsed Time: {}ms",
                         response, System.currentTimeMillis() - startMillis);
                return response;
            } catch (BaseRuntimeException e) {
                log.error("Generic player adaptor playerBalance request failed.", e);
                throw e;
            }
        }

        @Override
        public PlayerTransactionResponse playerTransaction(PlayerTransactionRequest request) {
            long startMillis = System.currentTimeMillis();

            if (request.getRequestType() == CLOSED) {
                request.setRequestType(TransactionType.CREDIT);
                request.setCredit(0D);
            }

            PlayerTransactionRequestV1 requestV1 = new PlayerTransactionRequestV1(request);

            try {
                log.info("playerTransaction request {}", requestV1);

                PlayerTransactionResponse response = postForObject(
                        "/player-transaction",
                        requestV1,
                        PlayerTransactionResponse.class
                );

                if (response == null) {
                    throw new BaseRuntimeException(SystemErrorCode.COM_ERROR, "empty response " + requestV1);
                }

                log.info("playerTransaction response {}. Elapsed Time: {}ms",
                         response, System.currentTimeMillis() - startMillis);
                return response;
            }
//            catch (BaseException e){
//                throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, e);
//            }
            catch (BaseRuntimeException e) {
                // preserve special rollback mapping similar to reactive version
                if ((requestV1.getRequestType() == DEBIT || requestV1.getRequestType() == DEBIT_CREDIT)
                    && e.getErrorCode() != null
                    && SystemErrorCode.COM_ERROR.getCode().equals(e.getErrorCode().getCode())) {

                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, e);
                }

                throw e;
            } catch (Exception e) {
                // non BaseRuntimeException/BaseException -> rollback
                if (!(e instanceof BaseRuntimeException) && !(e instanceof BaseException)) {
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, e);
                }
                throw e;
            }
        }

        @Override
        public PlayerTransactionResponse rollback(PlayerTransactionRequest playerTransactionRequest) {
            long startMillis = System.currentTimeMillis();
            Map<String, Object> settings = connector.getSettings(); // kept for parity; may be unused

            PlayerTransactionRequestV1 requestV1 = new PlayerTransactionRequestV1(playerTransactionRequest);

            try {
                log.info("rollback request {}", requestV1);

                PlayerTransactionResponse response = postForObject(
                        "/player-transaction",
                        requestV1,
                        PlayerTransactionResponse.class
                );

                if (response == null) {
                    throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR);
                }

                log.info("rollback response {}. Elapsed Time: {}ms",
                         response, System.currentTimeMillis() - startMillis);
                return response;
            } catch (BaseRuntimeException e) {
                log.error("Generic player adaptor transaction {} failed.",
                          requestV1.getTxnId(), e);
                throw e;
            }
        }
    }
}
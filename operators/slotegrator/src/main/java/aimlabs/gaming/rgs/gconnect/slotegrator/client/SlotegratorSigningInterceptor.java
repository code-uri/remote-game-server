package aimlabs.gaming.rgs.gconnect.slotegrator.client;

import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpRequest;

import java.io.IOException;

/**
 * Blocking RestClient/RestTemplate interceptor to inject Slotegrator signature headers.
 */
public class SlotegratorSigningInterceptor implements ClientHttpRequestInterceptor {

    private final Signer signer;

    public SlotegratorSigningInterceptor(Signer signer) {
        this.signer = signer;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        signer.injectHeader(request.getHeaders(), body);
        return execution.execute(request, body);
    }
}

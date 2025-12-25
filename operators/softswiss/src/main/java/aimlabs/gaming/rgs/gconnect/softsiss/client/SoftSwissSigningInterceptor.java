package aimlabs.gaming.rgs.gconnect.softsiss.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Blocking RestClient/RestTemplate interceptor to inject SoftSwiss request signature headers.
 */
public class SoftSwissSigningInterceptor implements ClientHttpRequestInterceptor {

    private final Signer signer;

    public SoftSwissSigningInterceptor(Signer signer) {
        this.signer = signer;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        signer.injectHeader(request.getHeaders(), body);
        return execution.execute(request, body);
    }
}

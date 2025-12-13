package in.aimlabs.gaming.gconnect.softsiss.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * A Wrapper around the default Jackson2JsonEncoder that captures the serialized body and supplies it to a consumer
 *
 * @author rewolf
 */
@RequiredArgsConstructor
public class BodyProvidingJsonEncoder extends Jackson2JsonEncoder {
    private final Signer signer;

    /**
     * Constructor that accepts the application's configured ObjectMapper and a Signer.
     *
     * @param signer The service responsible for creating the request signature.
     * @param mapper The Spring-configured ObjectMapper instance.
     */
    public BodyProvidingJsonEncoder(Signer signer, ObjectMapper mapper) {
        // This is the most important part: pass the configured ObjectMapper to the parent class.
        // This ensures all JSON serialization uses your application's rules.
        super(mapper);
        this.signer = signer;
    }

    public Flux<DataBuffer> encode(Publisher<?> inputStream, DataBufferFactory bufferFactory,
                                   ResolvableType elementType,  MimeType mimeType, Map<String, Object> hints) {

        return super.encode(inputStream, bufferFactory, elementType, mimeType, hints).flatMap(db -> {
            return Mono.deferContextual(contextView -> {
                ClientHttpRequest clientHttpRequest = contextView.get(MessageSigningHttpConnector.REQUEST_CONTEXT_KEY);

                signer.injectHeader( clientHttpRequest, extractBytes(db));
                return Mono.just(db);
            });
        });
    }

    /**
     * Extracts bytes from the DataBuffer and resets the buffer so that it is ready to be re-read by the regular
     * request sending process.
     * @param data data buffer with encoded data
     * @return copied data as a byte array.
     */
    private byte[] extractBytes(final DataBuffer data) {
        final byte[] bytes = new byte[data.readableByteCount()];
        data.read(bytes);
        data.readPosition(0);
        return bytes;
    }
}
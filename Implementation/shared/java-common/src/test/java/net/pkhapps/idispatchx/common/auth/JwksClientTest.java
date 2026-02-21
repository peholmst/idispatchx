package net.pkhapps.idispatchx.common.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JwksClientTest {

    private static final URI TEST_JWKS_URI = URI.create("https://test.example.com/.well-known/jwks.json");

    @Test
    void constructor_rejectsNullJwksUri() {
        assertThrows(NullPointerException.class, () -> new JwksClient(null));
    }

    @Test
    void constructor_rejectsNullCacheTtl() {
        assertThrows(NullPointerException.class, () ->
                new JwksClient(TEST_JWKS_URI, null, HttpClient.newHttpClient()));
    }

    @Test
    void constructor_rejectsNullHttpClient() {
        assertThrows(NullPointerException.class, () ->
                new JwksClient(TEST_JWKS_URI, Duration.ofMinutes(5), null));
    }

    @Test
    void constructor_rejectsNegativeCacheTtl() {
        assertThrows(IllegalArgumentException.class, () ->
                new JwksClient(TEST_JWKS_URI, Duration.ofMinutes(-1), HttpClient.newHttpClient()));
    }

    @Test
    void constructor_rejectsZeroCacheTtl() {
        assertThrows(IllegalArgumentException.class, () ->
                new JwksClient(TEST_JWKS_URI, Duration.ZERO, HttpClient.newHttpClient()));
    }

    @Test
    void getJwksUri_returnsConfiguredUri() {
        var client = new JwksClient(TEST_JWKS_URI, Duration.ofMinutes(5), createMockHttpClient("{}"));
        assertEquals(TEST_JWKS_URI, client.getJwksUri());
    }

    @Test
    void getKey_nullKeyIdThrowsException() {
        var client = new JwksClient(TEST_JWKS_URI, Duration.ofMinutes(5), createMockHttpClient("{}"));
        assertThrows(NullPointerException.class, () -> client.getKey(null));
    }

    @Test
    void getKey_fetchesAndCachesJwks() throws Exception {
        var key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        var jwkSet = new JWKSet(key);
        var fetchCount = new AtomicInteger(0);

        var client = new JwksClient(TEST_JWKS_URI, Duration.ofMinutes(5),
                createMockHttpClient(jwkSet.toString(), fetchCount));

        // First call should fetch
        var result1 = client.getKey("test-key");
        assertNotNull(result1);
        assertEquals("test-key", result1.getKeyID());
        assertEquals(1, fetchCount.get());

        // Second call should use cache
        var result2 = client.getKey("test-key");
        assertNotNull(result2);
        assertEquals(1, fetchCount.get());
    }

    @Test
    void getKey_returnsNullForUnknownKey() throws Exception {
        var key = new RSAKeyGenerator(2048).keyID("known-key").generate();
        var jwkSet = new JWKSet(key);

        var client = new JwksClient(TEST_JWKS_URI, Duration.ofMinutes(5),
                createMockHttpClient(jwkSet.toString()));

        var result = client.getKey("unknown-key");
        assertNull(result);
    }

    @Test
    void getKey_refreshesOnKeyNotFound() throws Exception {
        var key1 = new RSAKeyGenerator(2048).keyID("key1").generate();
        var key2 = new RSAKeyGenerator(2048).keyID("key2").generate();

        // First request returns key1, second request returns key1 + key2
        var jwkSet1 = new JWKSet(key1);
        var jwkSet2 = new JWKSet(java.util.List.of(key1, key2));

        var responses = new String[]{jwkSet1.toString(), jwkSet2.toString()};
        var fetchCount = new AtomicInteger(0);

        // Use very short TTL to ensure cache expires between calls
        var client = new JwksClient(TEST_JWKS_URI, Duration.ofMillis(1),
                createSequentialMockHttpClient(responses, fetchCount));

        // Get key1 - should fetch
        var result1 = client.getKey("key1");
        assertNotNull(result1);
        assertEquals(1, fetchCount.get());

        // Wait for cache to expire
        Thread.sleep(10);

        // Get key2 - cache expired, should refresh and find key2
        var result2 = client.getKey("key2");
        assertNotNull(result2);
        assertEquals("key2", result2.getKeyID());
        assertEquals(2, fetchCount.get());
    }

    @Test
    void refresh_clearsCache() throws Exception {
        var key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        var jwkSet = new JWKSet(key);
        var fetchCount = new AtomicInteger(0);

        var client = new JwksClient(TEST_JWKS_URI, Duration.ofMinutes(5),
                createMockHttpClient(jwkSet.toString(), fetchCount));

        // First call
        client.getKey("test-key");
        assertEquals(1, fetchCount.get());

        // Force refresh
        client.refresh();

        // Next call should fetch again
        client.getKey("test-key");
        assertEquals(2, fetchCount.get());
    }

    @Test
    void getKey_throwsJwksExceptionOnHttpError() {
        var client = new JwksClient(TEST_JWKS_URI, Duration.ofMinutes(5),
                createErrorHttpClient(500));

        assertThrows(JwksException.class, () -> client.getKey("any-key"));
    }

    @Test
    void getKey_throwsJwksExceptionOnInvalidJson() {
        var client = new JwksClient(TEST_JWKS_URI, Duration.ofMinutes(5),
                createMockHttpClient("not valid json"));

        assertThrows(JwksException.class, () -> client.getKey("any-key"));
    }

    @Test
    void defaultCacheTtlIsFiveMinutes() {
        assertEquals(Duration.ofMinutes(5), JwksClient.DEFAULT_CACHE_TTL);
    }

    @Test
    void defaultTimeoutIsTenSeconds() {
        assertEquals(Duration.ofSeconds(10), JwksClient.DEFAULT_TIMEOUT);
    }

    // Test helper methods

    private HttpClient createMockHttpClient(String responseBody) {
        return createMockHttpClient(responseBody, new AtomicInteger());
    }

    private HttpClient createMockHttpClient(String responseBody, AtomicInteger fetchCount) {
        return new MockHttpClient(responseBody, 200, fetchCount);
    }

    private HttpClient createSequentialMockHttpClient(String[] responses, AtomicInteger fetchCount) {
        return new SequentialMockHttpClient(responses, fetchCount);
    }

    private HttpClient createErrorHttpClient(int statusCode) {
        return new MockHttpClient("Error", statusCode, new AtomicInteger());
    }

    /**
     * Mock HttpClient that returns a fixed response.
     */
    private static class MockHttpClient extends HttpClient {
        private final String responseBody;
        private final int statusCode;
        private final AtomicInteger fetchCount;

        MockHttpClient(String responseBody, int statusCode, AtomicInteger fetchCount) {
            this.responseBody = responseBody;
            this.statusCode = statusCode;
            this.fetchCount = fetchCount;
        }

        @Override
        public java.util.Optional<java.net.CookieHandler> cookieHandler() {
            return java.util.Optional.empty();
        }

        @Override
        public java.net.http.HttpClient.Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public java.util.Optional<java.net.ProxySelector> proxy() {
            return java.util.Optional.empty();
        }

        @Override
        public javax.net.ssl.SSLContext sslContext() {
            return null;
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            return null;
        }

        @Override
        public java.util.Optional<java.util.concurrent.Executor> executor() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<java.net.Authenticator> authenticator() {
            return java.util.Optional.empty();
        }

        @Override
        public java.net.http.HttpClient.Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public java.util.Optional<java.time.Duration> connectTimeout() {
            return java.util.Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            fetchCount.incrementAndGet();
            return (HttpResponse<T>) new MockHttpResponse(responseBody, statusCode);
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Mock HttpClient that returns different responses sequentially.
     */
    private static class SequentialMockHttpClient extends MockHttpClient {
        private final String[] responses;
        private final AtomicInteger index = new AtomicInteger(0);
        private final AtomicInteger sequentialFetchCount;

        SequentialMockHttpClient(String[] responses, AtomicInteger fetchCount) {
            super("", 200, fetchCount);
            this.responses = responses;
            this.sequentialFetchCount = fetchCount;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            sequentialFetchCount.incrementAndGet();
            var i = index.getAndIncrement();
            var response = i < responses.length ? responses[i] : responses[responses.length - 1];
            return (HttpResponse<T>) new MockHttpResponse(response, 200);
        }
    }

    /**
     * Mock HttpResponse.
     */
    private record MockHttpResponse(String body, int statusCode) implements HttpResponse<String> {
        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public java.util.Optional<HttpResponse<String>> previousResponse() {
            return java.util.Optional.empty();
        }

        @Override
        public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
            return java.util.Optional.empty();
        }

        @Override
        public URI uri() {
            return TEST_JWKS_URI;
        }

        @Override
        public java.net.http.HttpClient.Version version() {
            return java.net.http.HttpClient.Version.HTTP_1_1;
        }
    }
}

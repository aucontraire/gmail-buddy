package com.aucontraire.gmailbuddy.config;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import java.util.concurrent.TimeUnit;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single, application-scoped, pooled {@link HttpTransport} shared by all
 * Gmail API calls, replacing the per-request {@code GoogleNetHttpTransport.newTrustedTransport()}
 * transport that previously cold-handshaked a new connection on every call (WI-2/US1).
 *
 * <p>The transport is an {@link ApacheHttpTransport} backed by a
 * {@link PoolingHttpClientConnectionManager} sized and validated from
 * {@link GmailBuddyProperties.GmailApi.HttpTransport}: a pool depth (max-per-route/max-total)
 * adequate for the target concurrency, validate-on-borrow after a configured idle period, a
 * background idle-connection evictor, and a bounded connection time-to-live — together this
 * removes the stale/server-closed-connection failure class a bare unpooled transport (or the
 * JDK keep-alive cache) does not guard against.
 *
 * <p>Note: {@code google-http-client-apache-v2} wraps Apache <b>HttpClient 4.x</b>
 * ({@code org.apache.http.*}), not HttpClient 5 — {@link ApacheHttpTransport}'s constructor
 * requires an {@code org.apache.http.client.HttpClient}. All Apache HttpClient types are kept
 * inside this configuration class; the rest of the application depends only on the Google
 * {@link HttpTransport} abstraction.
 */
@Configuration
public class GoogleTransportConfig {
    private static final Logger logger = LoggerFactory.getLogger(GoogleTransportConfig.class);

    private final GmailBuddyProperties properties;

    @Autowired
    public GoogleTransportConfig(GmailBuddyProperties properties) {
        this.properties = properties;
    }

    /**
     * The single shared, pooled Gmail API HTTP transport. Per-request/per-caller state (the
     * OAuth2 access token) is never applied here — it rides the per-request
     * {@code HttpRequestInitializer} in {@link com.aucontraire.gmailbuddy.client.GmailClient},
     * never the transport, preserving token isolation under a shared pool (FR-002).
     */
    @Bean
    public HttpTransport httpTransport() {
        GmailBuddyProperties.GmailApi.HttpTransport config =
                properties.gmailApi().httpTransport();
        PoolingHttpClientConnectionManager connectionManager = createConnectionManager();
        CloseableHttpClient httpClient = createHttpClient(connectionManager, config);

        logger.info(
                "Configured shared Gmail API HTTP transport: maxPerRoute={}, maxTotal={}, "
                        + "validateAfterInactivityMs={}, connectionTtlMs={}",
                config.maxPerRoute(),
                config.maxTotal(),
                config.validateAfterInactivityMs(),
                config.connectionTtlMs());

        return new ApacheHttpTransport(httpClient);
    }

    /**
     * Builds the pooled connection manager from {@code httpTransport} configuration. Package-private
     * so tests can assert the resolved pool settings ({@code maxTotal}, {@code maxPerRoute},
     * {@code validateAfterInactivity}) without reaching into the built {@link CloseableHttpClient}.
     */
    PoolingHttpClientConnectionManager createConnectionManager() {
        GmailBuddyProperties.GmailApi.HttpTransport config =
                properties.gmailApi().httpTransport();

        // The (timeToLive, TimeUnit) constructor bounds how long a pooled connection may live
        // regardless of use, guarding against holding connections open indefinitely (FR-013).
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager(config.connectionTtlMs(), TimeUnit.MILLISECONDS);
        connectionManager.setMaxTotal(config.maxTotal());
        connectionManager.setDefaultMaxPerRoute(config.maxPerRoute());
        // Validate-on-borrow: a connection idle longer than this is probed before reuse, so a
        // server-closed connection is detected and discarded instead of handed back to a caller.
        connectionManager.setValidateAfterInactivity((int) config.validateAfterInactivityMs());
        return connectionManager;
    }

    private CloseableHttpClient createHttpClient(
            PoolingHttpClientConnectionManager connectionManager, GmailBuddyProperties.GmailApi.HttpTransport config) {
        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(false)
                // Background sweep that proactively closes connections idle past the same
                // threshold used for validate-on-borrow, complementing it for pooled connections
                // that are never borrowed again.
                .evictIdleConnections(config.validateAfterInactivityMs(), TimeUnit.MILLISECONDS)
                .evictExpiredConnections()
                .build();
    }
}

/*********************************************************************
* Copyright (c) 2025 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   Thomas Calmant (Kentyou) - Initial contribution
**********************************************************************/

package com.kentyou.auroral.luxactive.oha.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.sensinact.core.command.AbstractTwinCommand;
import org.eclipse.sensinact.core.command.GatewayThread;
import org.eclipse.sensinact.core.snapshot.ProviderSnapshot;
import org.eclipse.sensinact.core.twin.SensinactDigitalTwin;
import org.eclipse.sensinact.core.twin.SensinactProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.PromiseFactory;
import org.osgi.util.tracker.ServiceTracker;

import com.kentyou.auroral.luxactive.oha.LuxActiveHistoryProvider;
import com.kentyou.sensinact.history.dto.single.HistoricResourceValue;
import com.kentyou.sensinact.history.dto.single.SingleHistoricValueResponse;
import com.kentyou.sensinact.history.spi.SimpleHistory;

public class LuxActiveBridgeTest {

    static QueuedThreadPool threadPool;
    static Server server;
    static RequestHandler handler;
    static int httpPort;

    static final String auroralProviderName = "auroralBridge";

    @InjectService
    GatewayThread thread;

    @InjectService
    ConfigurationAdmin cm;

    @InjectBundleContext
    BundleContext ctx;

    final AtomicReference<Map<String, String>> arguments = new AtomicReference<>();

    /**
     * Current Auroral bridge configuration
     */
    Configuration auroralConfig;

    /**
     * Current oHA bridge configuration
     */
    Configuration ohaConfig;

    @BeforeAll
    static void setup() throws Exception {
        threadPool = new QueuedThreadPool();
        threadPool.setName("server");
        handler = new RequestHandler();
        server = new Server(threadPool);
        ServerConnector conn = new ServerConnector(server);
        conn.setPort(0);
        server.addConnector(conn);
        server.setHandler(handler);
        server.start();
        httpPort = conn.getLocalPort();
    }

    @AfterAll
    static void teardown() throws Exception {
        server.stop();
        server = null;
        threadPool.stop();
        threadPool = null;
    }

    /**
     * Reads a file from the test folder
     *
     * @param filename File name
     * @return File content as string
     * @throws Exception Error reading file
     */
    String readFile(final String filename) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/data/" + filename)) {
            if (inputStream == null) {
                throw new FileNotFoundException(filename);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Wait for the appearance of a provider
     *
     * @param providerName Name of the provider
     */
    void waitForProvider(final String providerName)
            throws AssertionFailedError, InvocationTargetException, InterruptedException {
        boolean found = false;
        for (int i = 0; i < 10; i++) {
            found = thread.execute(new AbstractTwinCommand<Boolean>() {
                @Override
                protected Promise<Boolean> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                    return pf.resolved(twin.getProvider(providerName) != null);
                }
            }).getValue();
            if (found) {
                break;
            }

            Thread.sleep(100);
        }
        assertTrue(found, "Provider '" + providerName + "' not found");
    }

    @BeforeEach
    void setupBridge() throws Exception {
        final String baseUrl = "http://localhost:" + httpPort;

        // Create a configuration for the Auroral Bridge
        auroralConfig = cm.createFactoryConfiguration("auroral.explicit", "?");
        auroralConfig.update(new Hashtable<>(Map.of("auroral.node.url", baseUrl, "oid.local", "123", "oid.remote",
                "456", "provider.name", auroralProviderName, "actions",
                "{\"log-stats-hourly-by-date-range\": {\"service\": \"svc\", \"sna.alias\": \"logHourlyByDateRange\", \"resultType\": \"array\", \"arguments\": {\"route_type\": {\"type\": \"string\"}, \"realm_id\": {\"type\": \"string\"}, \"date_from\": {\"type\": \"string\"}, \"date_to\": {\"type\": \"string\"}, \"aggregation_window\": {\"type\": \"string\"}}}}")));

        // Prepare the HTTP server
        final String response = readFile("result-log-stats-hourly-by-date-range-page.json");
        handler.setHandler("/api/properties/123/456/log-stats-hourly-by-date-range", (t, a) -> {
            arguments.set(a);
            return response;
        });

        waitForProvider(auroralProviderName);
    }

    @AfterEach
    void cleanupTest() throws Exception {
        if (auroralConfig != null) {
            auroralConfig.delete();
            auroralConfig = null;
        }

        if (ohaConfig != null) {
            ohaConfig.delete();
            ohaConfig = null;
        }

        // Also clean up providers
        thread.execute(new AbstractTwinCommand<Void>() {
            @Override
            protected Promise<Void> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                twin.getProviders().stream().filter(p -> "oha-activity".equals(p.getModelName()))
                        .forEach(SensinactProvider::delete);
                return pf.resolved(null);
            }
        }).getValue();
    }

    @Test
    void testCurrentValues() throws Exception {
        // Create a configuration for the oHA stats handler
        final String ohaProviderTemplate = "oha-history-test-%s";
        ohaConfig = cm.createFactoryConfiguration("luxactive.oha.statistics", "?");
        ohaConfig.update(new Hashtable<>(
                Map.of("oha.provider.template", ohaProviderTemplate, "bridge.provider", auroralProviderName,
                        "bridge.action", "logHourlyByDateRange", "bridge.action.service", "svc", "oha.realm", "789")));

        final AtomicReference<String> ohaProviderName = new AtomicReference<String>(
                ohaProviderTemplate.formatted("31b45057-b469-4483-b05a-1de81e8483ed"));
        waitForProvider(ohaProviderName.get());

        // Check its values
        ProviderSnapshot ps = thread.execute(new AbstractTwinCommand<ProviderSnapshot>() {
            @Override
            protected Promise<ProviderSnapshot> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                return pf.resolved(twin.snapshotProvider(ohaProviderName.get()));
            }
        }).getValue();

        assertEquals(OffsetDateTime.of(2024, 5, 2, 0, 0, 0, 0, ZoneOffset.UTC).toInstant(),
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "totalRetentionTime").getValue().getTimestamp());
        assertEquals(9d,
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "totalRetentionTime").getValue().getValue());
        assertEquals(9d,
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "averageRetentionTime").getValue().getValue());
        assertEquals(1, ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "logCount").getValue().getValue());
        assertEquals("31b45057-b469-4483-b05a-1de81e8483ed",
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "id").getValue().getValue());
        assertEquals("https://asset.myoha.at/file/abc31200-c1a0-4f75-b64f-5cda5ac4f438.png",
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "image").getValue().getValue());
        assertEquals("Emil Magdić - Tidligere fange i Beisfjord",
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "title").getValue().getValue());
        assertEquals("6d4e5a5f-7562-498c-a503-b98af7dfd3ae",
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "channelId").getValue().getValue());

        // Check another one
        ohaProviderName.set(ohaProviderTemplate.formatted("91a9876e-4b45-46f8-a6ef-9e65c82c9d81"));

        // Check its values
        ps = thread.execute(new AbstractTwinCommand<ProviderSnapshot>() {
            @Override
            protected Promise<ProviderSnapshot> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                return pf.resolved(twin.snapshotProvider(ohaProviderName.get()));
            }
        }).getValue();

        assertEquals(OffsetDateTime.of(2024, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant(),
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "totalRetentionTime").getValue().getTimestamp());
        assertEquals(9d,
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "totalRetentionTime").getValue().getValue());
        assertEquals(9d,
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "averageRetentionTime").getValue().getValue());
        assertEquals(1, ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "logCount").getValue().getValue());
        assertEquals("91a9876e-4b45-46f8-a6ef-9e65c82c9d81",
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "id").getValue().getValue());
        assertEquals("https://asset.myoha.at/file/0536f0b1-ce6e-4096-add1-2e179ef84c16.png",
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "image").getValue().getValue());
        assertEquals("Slavko Vukić – Tidligere fange i Beisfjord",
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "title").getValue().getValue());
        assertEquals("6d4e5a5f-7562-498c-a503-b98af7dfd3ae",
                ps.getResource(LuxActiveHistoryProvider.STATS_SERVICE, "channelId").getValue().getValue());
    }

    @Test
    void testHistory() throws Exception {
        // Create a configuration for the oHA stats handler
        final String ohaProviderTemplate = "oha-history-test-%s";
        ohaConfig = cm.createFactoryConfiguration("luxactive.oha.statistics", "?");
        ohaConfig.update(new Hashtable<>(
                Map.of("oha.provider.template", ohaProviderTemplate, "bridge.provider", auroralProviderName,
                        "bridge.action", "logHourlyByDateRange", "bridge.action.service", "svc", "oha.realm", "789")));

        final AtomicReference<String> ohaProviderName = new AtomicReference<String>(
                ohaProviderTemplate.formatted("31b45057-b469-4483-b05a-1de81e8483ed"));
        waitForProvider(ohaProviderName.get());

        ServiceTracker<SimpleHistory, SimpleHistory> tracker = new ServiceTracker<>(ctx, SimpleHistory.class, null);
        tracker.open();
        SimpleHistory historyApi = tracker.waitForService(1000);
        tracker.close();

        // Call the history API
        assertEquals(26, historyApi.getProvidedPaths().size()); // 13 per route
        Instant fromTime = Instant.parse("2024-05-01T00:00:00Z");
        Instant toTime = Instant.parse("2024-05-02T00:00:00Z");

        // Note: the payload sent back by the test server will always be the same
        arguments.set(null);
        SingleHistoricValueResponse history = historyApi.getValueRange(ohaProviderName.get(),
                LuxActiveHistoryProvider.STATS_SERVICE, "totalRetentionTime", fromTime, toTime);

        Map<String, String> args = arguments.getAndSet(null);
        assertNotNull(args);
        assertEquals("all", args.get("route_type"));
        assertEquals("789", args.get("realm_id"));
        assertEquals("2024-05-01Z", args.get("date_from"));
        assertEquals("2024-05-02Z", args.get("date_to"));
        assertEquals("hour", args.get("aggregation_window"));
        assertEquals(47, history.values.size());

        List<HistoricResourceValue> rcs = history.values.stream()
                .filter(hrv -> ohaProviderName.get().equals(hrv.provider))
                .filter(hrv -> "totalRetentionTime".equals(hrv.resource)).toList();
        assertEquals(2, rcs.size());
        assertEquals(18d, rcs.get(0).dto.getValue());
        assertEquals(9d, rcs.get(1).dto.getValue());

        fromTime = Instant.parse("2024-05-01T00:00:00Z");
        toTime = Instant.parse("2024-05-01T12:00:00Z");
        history = historyApi.getValueRange(ohaProviderName.get(), LuxActiveHistoryProvider.STATS_SERVICE,
                "totalRetentionTime", fromTime, toTime);

        args = arguments.getAndSet(null);
        assertNotNull(args);
        assertEquals("all", args.get("route_type"));
        assertEquals("789", args.get("realm_id"));
        assertEquals("2024-05-01Z", args.get("date_from"));
        assertEquals("2024-05-01Z", args.get("date_to"));
        assertEquals("hour", args.get("aggregation_window"));
    }

    @Test
    void testTimezone() throws Exception {
        // Create a configuration for the oHA stats handler
        final String ohaProviderTemplate = "oha-history-tz-%s";
        ohaConfig = cm.createFactoryConfiguration("luxactive.oha.statistics", "?");
        ohaConfig.update(new Hashtable<>(Map.of("oha.provider.template", ohaProviderTemplate, "bridge.provider",
                auroralProviderName, "bridge.action", "logHourlyByDateRange", "bridge.action.service", "svc",
                "oha.realm", "789", "oha.timezone", "Europe/Oslo")));

        final AtomicReference<String> ohaProviderName = new AtomicReference<String>(
                ohaProviderTemplate.formatted("31b45057-b469-4483-b05a-1de81e8483ed"));
        waitForProvider(ohaProviderName.get());

        ServiceTracker<SimpleHistory, SimpleHistory> tracker = new ServiceTracker<>(ctx, SimpleHistory.class, null);
        tracker.open();
        SimpleHistory historyApi = tracker.waitForService(1000);
        tracker.close();

        // Call the history API
        assertEquals(26, historyApi.getProvidedPaths().size()); // 13 per route
        Instant fromTime = Instant.parse("2024-05-01T00:00:00Z");
        Instant toTime = Instant.parse("2024-05-02T00:00:00Z");

        // Note: the payload sent back by the test server will always be the same
        arguments.set(null);
        SingleHistoricValueResponse history = historyApi.getValueRange(ohaProviderName.get(),
                LuxActiveHistoryProvider.STATS_SERVICE, "totalRetentionTime", fromTime, toTime);

        Map<String, String> args = arguments.getAndSet(null);
        assertNotNull(args);
        assertEquals("all", args.get("route_type"));
        assertEquals("789", args.get("realm_id"));
        assertEquals("2024-05-01+02:00", args.get("date_from"));
        assertEquals("2024-05-02+02:00", args.get("date_to"));
        assertEquals("hour", args.get("aggregation_window"));
        assertEquals(47, history.values.size());

        List<HistoricResourceValue> rcs = history.values.stream()
                .filter(hrv -> ohaProviderName.get().equals(hrv.provider))
                .filter(hrv -> "totalRetentionTime".equals(hrv.resource)).toList();
        assertEquals(2, rcs.size());
        assertEquals(18d, rcs.get(0).dto.getValue());
        assertEquals(9d, rcs.get(1).dto.getValue());

        fromTime = Instant.parse("2024-05-01T00:00:00Z");
        toTime = Instant.parse("2024-05-01T12:00:00Z");
        history = historyApi.getValueRange(ohaProviderName.get(), LuxActiveHistoryProvider.STATS_SERVICE,
                "totalRetentionTime", fromTime, toTime);

        args = arguments.getAndSet(null);
        assertNotNull(args);
        assertEquals("all", args.get("route_type"));
        assertEquals("789", args.get("realm_id"));
        assertEquals("2024-05-01+02:00", args.get("date_from"));
        assertEquals("2024-05-01+02:00", args.get("date_to"));
        assertEquals("hour", args.get("aggregation_window"));
    }
}

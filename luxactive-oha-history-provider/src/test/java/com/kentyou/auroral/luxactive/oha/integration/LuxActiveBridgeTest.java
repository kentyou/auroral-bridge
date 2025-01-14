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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.sensinact.core.command.AbstractTwinCommand;
import org.eclipse.sensinact.core.command.GatewayThread;
import org.eclipse.sensinact.core.snapshot.ProviderSnapshot;
import org.eclipse.sensinact.core.twin.SensinactDigitalTwin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.PromiseFactory;

public class LuxActiveBridgeTest {

    static QueuedThreadPool threadPool;
    static Server server;
    static RequestHandler handler;
    static int httpPort;

    @InjectService
    GatewayThread thread;

    @InjectService
    ConfigurationAdmin cm;

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

    @Test
    void testCurrentValues() throws Exception {
        final String auroralProviderName = "auroralBridge";
        final String ohaProviderName = "ohaProvider";
        final String baseUrl = "http://localhost:" + httpPort;

        // Create a configuration for the Auroral Bridge
        final Configuration auroralConfig = cm.createFactoryConfiguration("auroral.explicit", "?");
        auroralConfig.update(new Hashtable<>(Map.of("auroral.node.url", baseUrl, "oid.local", "123", "oid.remote",
                "456", "provider.name", auroralProviderName, "actions",
                "{\"log-stats-hourly-by-date-range\": {\"service\": \"svc\", \"sna.alias\": \"logHourlyByDateRange\", \"resultType\": \"array\", \"arguments\": {\"route_type\": {\"type\": \"string\"}, \"realm_id\": {\"type\": \"string\"}, \"date_from\": {\"type\": \"string\"}, \"date_to\": {\"type\": \"string\"}, \"aggregation_window\": {\"type\": \"string\"}}}}")));

        // Prepare the HTTP server
        final String response = readFile("result-log-stats-hourly-by-date-range-page.json");
        final AtomicReference<Map<String, String>> arguments = new AtomicReference<>();
        handler.setHandler("/api/properties/123/456/log-stats-hourly-by-date-range", (t, a) -> {
            arguments.set(a);
            return response;
        });

        boolean found = false;
        for (int i = 0; i < 10; i++) {
            found = thread.execute(new AbstractTwinCommand<Boolean>() {
                @Override
                protected Promise<Boolean> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                    return pf.resolved(twin.getProvider(auroralProviderName) != null);
                }
            }).getValue();
            if (found) {
                break;
            }

            Thread.sleep(100);
        }
        assertTrue(found, "Auroral bridge provider not found");

        // Create a configuration for the oHA stats handler
        final Configuration ohaConfig = cm.createFactoryConfiguration("luxactive.oha.statistics", "?");
        ohaConfig.update(new Hashtable<>(Map.of("provider", ohaProviderName, "bridge.provider", auroralProviderName,
                "bridge.action", "logHourlyByDateRange", "bridge.action.service", "svc", "oha.realm", "789")));

        found = false;
        for (int i = 0; i < 10; i++) {
            found = thread.execute(new AbstractTwinCommand<Boolean>() {
                @Override
                protected Promise<Boolean> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                    return pf.resolved(twin.getProvider(ohaProviderName) != null);
                }
            }).getValue();
            if (found) {
                break;
            }

            Thread.sleep(100);
        }
        assertTrue(found, "oHA history provider not found");

        // Check its values
        final ProviderSnapshot ps = thread.execute(new AbstractTwinCommand<ProviderSnapshot>() {
            @Override
            protected Promise<ProviderSnapshot> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                return pf.resolved(twin.snapshotProvider(ohaProviderName));
            }
        }).getValue();

        assertEquals(OffsetDateTime.of(2024, 5, 2, 0, 0, 0, 0, ZoneOffset.UTC).toInstant(),
                ps.getResource("31b45057-b469-4483-b05a-1de81e8483ed", "totalRetentionTime").getValue().getTimestamp());
        assertEquals(9d,
                ps.getResource("31b45057-b469-4483-b05a-1de81e8483ed", "totalRetentionTime").getValue().getValue());
        assertEquals(9d,
                ps.getResource("31b45057-b469-4483-b05a-1de81e8483ed", "averageRetentionTime").getValue().getValue());
        assertEquals(1, ps.getResource("31b45057-b469-4483-b05a-1de81e8483ed", "logCount").getValue().getValue());
        assertEquals("31b45057-b469-4483-b05a-1de81e8483ed",
                ps.getResource("31b45057-b469-4483-b05a-1de81e8483ed", "id").getValue().getValue());
        assertEquals("https://asset.myoha.at/file/abc31200-c1a0-4f75-b64f-5cda5ac4f438.png",
                ps.getResource("31b45057-b469-4483-b05a-1de81e8483ed", "image").getValue().getValue());
        assertEquals("Emil Magdić - Tidligere fange i Beisfjord",
                ps.getResource("31b45057-b469-4483-b05a-1de81e8483ed", "title").getValue().getValue());
        assertEquals("6d4e5a5f-7562-498c-a503-b98af7dfd3ae",
                ps.getResource("31b45057-b469-4483-b05a-1de81e8483ed", "channelId").getValue().getValue());

        assertEquals(OffsetDateTime.of(2024, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant(),
                ps.getResource("91a9876e-4b45-46f8-a6ef-9e65c82c9d81", "totalRetentionTime").getValue().getTimestamp());
        assertEquals(9d,
                ps.getResource("91a9876e-4b45-46f8-a6ef-9e65c82c9d81", "totalRetentionTime").getValue().getValue());
        assertEquals(9d,
                ps.getResource("91a9876e-4b45-46f8-a6ef-9e65c82c9d81", "averageRetentionTime").getValue().getValue());
        assertEquals(1, ps.getResource("91a9876e-4b45-46f8-a6ef-9e65c82c9d81", "logCount").getValue().getValue());
        assertEquals("91a9876e-4b45-46f8-a6ef-9e65c82c9d81",
                ps.getResource("91a9876e-4b45-46f8-a6ef-9e65c82c9d81", "id").getValue().getValue());
        assertEquals("https://asset.myoha.at/file/0536f0b1-ce6e-4096-add1-2e179ef84c16.png",
                ps.getResource("91a9876e-4b45-46f8-a6ef-9e65c82c9d81", "image").getValue().getValue());
        assertEquals("Slavko Vukić – Tidligere fange i Beisfjord",
                ps.getResource("91a9876e-4b45-46f8-a6ef-9e65c82c9d81", "title").getValue().getValue());
        assertEquals("6d4e5a5f-7562-498c-a503-b98af7dfd3ae",
                ps.getResource("91a9876e-4b45-46f8-a6ef-9e65c82c9d81", "channelId").getValue().getValue());
    }
}

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

package com.kentyou.auroral.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.sensinact.core.command.AbstractTwinCommand;
import org.eclipse.sensinact.core.command.GatewayThread;
import org.eclipse.sensinact.core.twin.SensinactDigitalTwin;
import org.eclipse.sensinact.core.twin.SensinactProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.PromiseFactory;

public class BasicTest {

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
    void testCall() throws Exception {
        final String providerName = "toto";
        final String baseUrl = "http://localhost:" + httpPort;

        // Create a configuration
        final Configuration config = cm.createFactoryConfiguration("auroral.explicit", "?");
        config.update(new Hashtable<>(Map.of("auroral.node.url", baseUrl, "oid.local", "123", "oid.remote", "456",
                "provider.name", providerName, "actions",
                "{\"values\": {\"sna.alias\": \"snaValues\", \"description\": \"Returns an hourly-aggregation for the specified days.\", \"resultType\": \"array\", \"service\": \"svc\", \"arguments\": {\"auroral-arg\": {\"sna.alias\": \"answer\", \"type\": \"number\", \"description\": \"some number\"}}}}")));

        // Prepare the HTTP server
        final String response = readFile("result-log-stats-hourly-by-date-range-page.json");
        final AtomicReference<Map<String, String>> arguments = new AtomicReference<>();
        handler.setHandler("/api/properties/123/456/values", (t, a) -> {
            arguments.set(a);
            return response;
        });

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
        assertTrue(found, "Provider not found");

        // Call the action
        final Object result = thread.execute(new AbstractTwinCommand<Object>() {
            @Override
            protected Promise<Object> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                try {
                    SensinactProvider p = twin.getProvider(providerName);
                    return p.getServices().get("svc").getResources().get("snaValues").act(Map.of("answer", 42));
                } catch (Throwable e) {
                    return pf.failed(e);
                }
            }
        }).getValue();

        // Check query
        final Map<String, String> callArgs = arguments.get();
        assertNotNull(callArgs, "No call arguments");
        assertEquals(1, callArgs.size());
        assertTrue(callArgs.containsKey("auroral-arg"), "Requested argument not found");
        assertEquals("42", callArgs.get("auroral-arg"));

        // Check result
        assertInstanceOf(List.class, result);
        assertInstanceOf(Map.class, ((List<?>) result).get(0));
        Map<?, ?> obj = (Map<?, ?>) ((List<?>) result).get(0);
        assertTrue(obj.containsKey("timestamp"), "Wrong data");
        assertEquals("2024-05-01T00:00:00+00:00", obj.get("timestamp"));
        assertEquals(2, obj.get("log_total_count"));
    }
}

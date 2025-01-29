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

package com.kentyou.auroral.luxactive.oha.activies.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
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
import org.eclipse.sensinact.gateway.geojson.GeoJsonObject;
import org.eclipse.sensinact.gateway.geojson.Point;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.PromiseFactory;

public class ActivityLoaderTest {

    static QueuedThreadPool threadPool;
    static Server server;
    static RequestHandler handler;
    static int httpPort;

    @InjectService
    GatewayThread thread;

    @InjectService
    ConfigurationAdmin cm;

    @InjectBundleContext
    BundleContext ctx;

    final AtomicReference<Map<String, String>> arguments = new AtomicReference<>();

    /**
     * Current oHA activity loader configuration
     */
    Configuration loaderConfig;

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

    @AfterEach
    void cleanupTest() throws Exception {
        if (loaderConfig != null) {
            loaderConfig.delete();
            loaderConfig = null;
        }
    }

    @Test
    void testActivitiesLoading() throws Exception {
        // Create a configuration for the oHA activity loader
        final String baseUrl = "http://localhost:" + httpPort;
        final String template = "test-oha-%s";
        final String targetProvider = template.formatted("d57b29de-a5ff-4f65-8b37-4467830749df");

        // Prepare the HTTP server
        final String oidLocal = "123";
        final String oidRemote = "456";
        final String auroralProperty = "localData";
        final String response = readFile("oha-activities-halogaland.json");
        handler.setHandler("/api/properties/" + oidLocal + "/" + oidRemote + "/" + auroralProperty, (t, a) -> {
            arguments.set(a);
            return response;
        });

        loaderConfig = cm.createFactoryConfiguration("luxactive.oha.loader", "?");
        loaderConfig.update(new Hashtable<>(Map.of("provider.template", template, "auroral.node.url", baseUrl,
                "oid.local", "123", "oid.remote", "456", "auroral.properties", auroralProperty)));
        waitForProvider(targetProvider);

        // No argument must have been given
        assertTrue(arguments.get().isEmpty());

        // Check its values
        final ProviderSnapshot ps = thread.execute(new AbstractTwinCommand<ProviderSnapshot>() {
            @Override
            protected Promise<ProviderSnapshot> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                return pf.resolved(twin.snapshotProvider(targetProvider));
            }
        }).getValue();

        // We have a bit less precision in the sensiNact timestamp that in what is
        // provided in the JSON file
        assertEquals(OffsetDateTime.of(2023, 6, 13, 10, 23, 03, 138000000, ZoneOffset.UTC).toInstant(),
                ps.getResource("activity", "id").getValue().getTimestamp());
        assertEquals(auroralProperty, ps.getResource("auroral", "property").getValue().getValue());
        assertEquals("d57b29de-a5ff-4f65-8b37-4467830749df", ps.getResource("activity", "id").getValue().getValue());
        assertEquals("Tore Hunds rolle i historien", ps.getResource("admin", "friendlyName").getValue().getValue());
        assertEquals(
                "<p><strong>Beryktet</strong><br>Tore Hund vil alltid være forbundet med St. Olav på grunn av hendelsene på Stiklestad i 1030 og alt som skjedde etterpå. Han blir sett på som drapsmann av en helgen, og det er vanskelig å riste av seg det ryktet. Men denne høvdingen fra Bjarkøy var verken en skruppelløs skurk som brutalt drepte den norske helgenkongen, eller en motstandsmann som kjempet mot den urettferdige nasjonale overmakten som en separatist. Han var en del av et norsk stormannsforbund som kjempet i en konflikt som endte med at deres politiske motstander ble drept, slik det var vanlig på den tiden. Tore Hund sin posisjon i dette forbundet viser hvor tett knyttet det nordligste høvdingdømmet var til rikspolitikken i det norske riket.&nbsp;</p><p>&nbsp;<strong>Samarbeid</strong><br>Tores Hunds rolle i historien har på mange måter gjort ham til et symbol for de mange prosessene som utspilte seg i hans tid. Og det er her det fremtidige fokuset bør ligge. Ikke på personen Tore Hund, men på betydningen av Bjarkøys rike historie gjennom mer enn 1000 år. Her, på et høvdingsete med en historie som går tilbake til 300-tallet, samlet folk kunnskap og samarbeidet med ulike grupper lokalt, nordøst- og sørover gjennom århundrene. Særlig samarbeidet man med den samiske befolkningen og la grunnlaget for et av de mest velstående høvdingdømmene i det norske riket. Etter Tore Hund ble høvdingsetet hjemmet til en av høymiddelalderens mektigste og mest innflytelsesrike adelsslekter. Bjarkøy sto derfor verken imot eller utenfor den utviklingen som hele Norge opplevde, fra lokale høvdingdømmer til et rikskongedømme, men var en del av den.&nbsp;</p>",
                ps.getResource("activity", "longDescription").getValue().getValue());
        assertEquals(
                "<p><strong>Tore Hund vil alltid være forbundet med St. Olav på grunn av hendelsene på Stiklestad i 1030 og alt som skjedde etterpå.</strong></p>",
                ps.getResource("activity", "description").getValue().getValue());

        assertEquals("no", ps.getResource("activity", "language").getValue().getValue());
        assertEquals("https://asset.myoha.at/file/8cf2af6e-0fdb-4844-a162-7ffd693027cb.png",
                ps.getResource("activity", "coverImage").getValue().getValue());
        assertEquals("6d4e5a5f-7562-498c-a503-b98af7dfd3ae",
                ps.getResource("activity", "channelId").getValue().getValue());
        assertEquals("Activities", ps.getResource("activity", "channelName").getValue().getValue());

        GeoJsonObject rawLocation = (GeoJsonObject) ps.getResource("admin", "location").getValue().getValue();
        assertNotNull(rawLocation);
        assertInstanceOf(Point.class, rawLocation);

        Point location = (Point) rawLocation;
        assertNotNull(location.coordinates);
        assertEquals(68.41728, location.coordinates.latitude, 0.000001);
        assertEquals(17.90582, location.coordinates.longitude, 0.000001);
        assertTrue(Double.isNaN(location.coordinates.elevation));
    }
}

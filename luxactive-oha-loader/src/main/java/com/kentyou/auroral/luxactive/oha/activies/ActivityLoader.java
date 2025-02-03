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

package com.kentyou.auroral.luxactive.oha.activies;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.sensinact.core.push.DataUpdate;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kentyou.auroral.luxactive.oha.activies.dto.ActivityDescriptionDTO;

@Component(configurationPid = "luxactive.oha.loader", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class ActivityLoader {

    private static final Logger logger = LoggerFactory.getLogger(ActivityLoader.class);

    /**
     * JSON mapper
     */
    ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    /**
     * Data updater
     */
    @Reference
    DataUpdate push;

    /**
     * HTTP client
     */
    HttpClient httpClient;

    /**
     * Scheduled retrials
     */
    private ScheduledExecutorService scheduler;

    /**
     * Scheduled update task
     */
    private ScheduledFuture<?> scheduledUpdate;

    /**
     * Component activated
     */
    @Activate
    void activate(final LoaderConfiguration config) throws Exception {
        final String providerModelPackageUri = config.oha_provider_model_package_uri();
        final String providerModel = config.oha_provider_model();

        final String providerNameFormat = Objects
                .requireNonNull(config.oha_provider_template(), "No provider template given").trim();
        if (providerNameFormat.isEmpty() || !providerNameFormat.contains("%s")) {
            throw new IllegalArgumentException("Provider template is invalid: '" + providerNameFormat + "'");
        }

        String strNodeUrl = config.auroral_node_url();
        if (strNodeUrl == null || strNodeUrl.isBlank()) {
            strNodeUrl = "http://localhost:81/";
        }
        final URI nodeUrl = URI.create(strNodeUrl);

        final String localOid = Objects.requireNonNull(config.oid_local(), "No local OID given");
        if (localOid.isEmpty()) {
            throw new IllegalArgumentException("Local OID can't be empty");
        }
        final String remoteOid = Objects.requireNonNull(config.oid_remote(), "No remote OID given");
        if (remoteOid.isEmpty()) {
            throw new IllegalArgumentException("Remote OID can't be empty");
        }

        final String[] properties = Objects.requireNonNull(config.auroral_properties());
        if (properties.length == 0) {
            throw new IllegalArgumentException("No Auroral property name given");
        }

        httpClient = new HttpClient();
        httpClient.start();

        scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.scheduleAtFixedRate(() -> tryLoadingDescription(nodeUrl, localOid, remoteOid, properties,
                providerModelPackageUri, providerModel, providerNameFormat), 0, 6, TimeUnit.HOURS);
    }

    @Deactivate
    void deactivate() throws Exception {
        if (scheduledUpdate != null) {
            scheduledUpdate.cancel(true);
            scheduledUpdate = null;
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        if (httpClient != null) {
            httpClient.stop();
            httpClient = null;
        }
    }

    /**
     * Tries loading descriptions of activities. Automatically schedules a new call
     * in 5 minutes if something fails
     *
     * @param nodeUrl                 Auroral node URL
     * @param localOid                Local Auroral object ID
     * @param remoteOid               Remote Auroral object ID
     * @param properties              Auroral roperties to load from
     * @param providerModelPackageUri Activity providers model package URI
     * @param providerModel           Activity providers model names
     * @param providerNameFormat      Template of the activity provider name
     */
    void tryLoadingDescription(final URI nodeUrl, final String localOid, final String remoteOid,
            final String[] properties, final String providerModelPackageUri, final String providerModel,
            final String providerNameFormat) {

        if (scheduledUpdate != null) {
            logger.debug("Unregister pending/running update task");
            scheduledUpdate.cancel(false);
            scheduledUpdate = null;
        }

        for (String property : properties) {
            final List<ActivityDescriptionDTO> activities = loadDescriptions(nodeUrl, localOid, remoteOid, property);
            if (activities != null) {
                pushDescription(property, providerModelPackageUri, providerModel, providerNameFormat, activities);
            } else if (scheduledUpdate == null) {
                logger.debug("Schedule another update in 5 minutes");
                scheduledUpdate = scheduler
                        .schedule(
                                () -> tryLoadingDescription(nodeUrl, localOid, remoteOid, properties,
                                        providerModelPackageUri, providerModel, providerNameFormat),
                                5, TimeUnit.MINUTES);
            }
        }
    }

    /**
     * Loads activities descriptions from the Auroral node
     *
     * @return A list of description
     */
    List<ActivityDescriptionDTO> loadDescriptions(final URI auroralNodeUri, final String localOid,
            final String remoteOid, final String propertyName) {
        if (httpClient == null) {
            logger.debug("No HTTP client");
            return List.of();
        }

        final Request request = httpClient.newRequest(auroralNodeUri)
                .path("/api/properties/" + localOid + "/" + remoteOid + "/" + propertyName);

        try {
            final ContentResponse response = request.send();
            if (response.getStatus() >= 300) {
                logger.error("Can't handle response status {} from {}", response.getStatus(), request.getURI());
                return null;
            }

            Map<String, Object> jsonResponse = mapper.readValue(response.getContentAsString(),
                    new TypeReference<Map<String, Object>>() {
                    });
            return mapper.convertValue(jsonResponse.get("message"), new TypeReference<List<ActivityDescriptionDTO>>() {
            });
        } catch (Exception e) {
            logger.error("Error querying URL {}", request.getURI(), e);
            return null;
        }
    }

    /**
     * Pushes the descriptions of activities to sensiNact, with one provider per
     * activity
     *
     * @param auroralProperty  Auroral property used to load the activity
     *                         description
     * @param modelPackageUri  Activity provider model package URI
     * @param model            Activity provider model name
     * @param providerTemplate Template of activity provider name
     * @param activities       Activities to register
     */
    void pushDescription(final String auroralProperty, final String modelPackageUri, final String model,
            final String providerTemplate, final List<ActivityDescriptionDTO> activities) {
        if (activities == null || activities.isEmpty()) {
            logger.warn("No activities to push");
        }

        try {
            push.pushUpdate(activities.stream().map(descr -> SensiNactActivityDTO.fromDTO(auroralProperty,
                    modelPackageUri, model, providerTemplate.formatted(descr.identifier), descr)).toList()).getValue();
        } catch (InvocationTargetException | InterruptedException e) {
            logger.error("Error pushing activity description definition", e);
        }
    }
}

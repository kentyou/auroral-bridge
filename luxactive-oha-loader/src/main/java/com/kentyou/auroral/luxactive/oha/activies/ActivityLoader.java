/*
 * Copyright 2025 Kentyou
 * Proprietary and confidential
 *
 * All Rights Reserved.
 * Unauthorized copying of this file is strictly prohibited
 */

package com.kentyou.auroral.luxactive.oha.activies;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.List;
import java.util.Objects;

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
     * Loading thread
     */
    Thread loadingThread;

    /**
     * Component activated
     */
    @Activate
    void activate(final LoaderConfiguration config) throws Exception {
        final String providerModelPackageUri = config.provider_model_package_uri();
        final String providerModel = config.provider_model();

        final String providerNameFormat = Objects
                .requireNonNull(config.provider_template(), "No provider template given").trim();
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

        // Start queries in a new thread
        loadingThread = new Thread(() -> {
            for (String property : properties) {
                final List<ActivityDescriptionDTO> activities = loadDescriptions(nodeUrl, localOid, remoteOid,
                        property);
                if (activities != null) {
                    pushDescription(providerModelPackageUri, providerModel, providerNameFormat, activities);
                }
            }
        });
        loadingThread.start();
    }

    @Deactivate
    void deactivate() throws Exception {
        if (httpClient != null) {
            httpClient.stop();
            httpClient = null;
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

            return mapper.readValue(response.getContentAsString(), new TypeReference<List<ActivityDescriptionDTO>>() {
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
     * @param modelPackageUri  Activity provider model package URI
     * @param model            Activity provider model name
     * @param providerTemplate Template of activity provider name
     * @param activities       Activities to register
     */
    void pushDescription(final String modelPackageUri, final String model, final String providerTemplate,
            final List<ActivityDescriptionDTO> activities) {
        if (activities == null || activities.isEmpty()) {
            logger.warn("No activities to push");
        }

        try {
            push.pushUpdate(activities.stream().map(descr -> SensiNactActivityDTO.fromDTO(modelPackageUri, model,
                    providerTemplate.formatted(descr.identifier), descr)).toList()).getValue();
        } catch (InvocationTargetException | InterruptedException e) {
            logger.error("Error pushing activity description definition", e);
        }
    }
}

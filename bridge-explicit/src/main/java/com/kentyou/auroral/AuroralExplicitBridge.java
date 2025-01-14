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

package com.kentyou.auroral;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.eclipse.sensinact.core.command.AbstractSensinactCommand;
import org.eclipse.sensinact.core.command.AbstractTwinCommand;
import org.eclipse.sensinact.core.command.GatewayThread;
import org.eclipse.sensinact.core.model.Model;
import org.eclipse.sensinact.core.model.Resource;
import org.eclipse.sensinact.core.model.ResourceType;
import org.eclipse.sensinact.core.model.SensinactModelManager;
import org.eclipse.sensinact.core.model.Service;
import org.eclipse.sensinact.core.twin.SensinactDigitalTwin;
import org.eclipse.sensinact.core.twin.SensinactProvider;
import org.eclipse.sensinact.core.whiteboard.WhiteboardConstants;
import org.eclipse.sensinact.core.whiteboard.WhiteboardHandler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.PromiseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kentyou.auroral.configuration.ActionDTO;
import com.kentyou.auroral.configuration.Configuration;

@Component(immediate = true, configurationPid = "auroral.explicit", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class AuroralExplicitBridge {

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(AuroralExplicitBridge.class);

    /**
     * Default resource service name
     */
    public static final String DEFAULT_SERVICE = "auroral";

    /**
     * Shared HTTP client
     */
    @Reference
    private SharedHttpClient httpClient;

    /**
     * sensiNact gateway thread
     */
    @Reference
    private GatewayThread thread;

    /**
     * The JSON object mapper
     */
    final ObjectMapper mapper = JsonMapper.builder().build();

    /**
     * Name of the provider
     */
    private String providerName;

    /**
     * Whiteboard handler
     */
    private ServiceRegistration<WhiteboardHandler> whiteboardRegistration;

    /**
     * Component activated
     */
    void activate(final BundleContext ctx, final Configuration config) throws Exception {
        String providerModelPackageUri = config.provider_model_package_uri();
        if (providerModelPackageUri != null && providerModelPackageUri.isBlank()) {
            providerModelPackageUri = null;
        }

        String providerModel = config.provider_model();
        if (providerModel != null && providerModel.isBlank()) {
            providerModel = null;
        }

        final String providerName = Objects.requireNonNull(config.provider_name(), "No provider name given").trim();
        if (providerName.isEmpty()) {
            throw new IllegalArgumentException("Provider name can't be empty");
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

        final Map<String, ActionDTO> configActions;
        final String strActions = config.actions();
        if (strActions == null || strActions.isBlank()) {
            logger.warn("No actions defined");
            configActions = Map.of();
        } else {
            configActions = mapper.readValue(config.actions(), new TypeReference<Map<String, ActionDTO>>() {
            });
        }

        // Convert actions
        final List<SnaActionDTO> snaActions = configActions.entrySet().stream()
                .map(e -> SnaActionDTO.fromAuroral(e.getKey(), e.getValue())).toList();

        try {
            // Prepare the provider
            final Map<String, String> providerInfo = createProvider(providerModelPackageUri, providerModel,
                    providerName, nodeUrl, localOid, remoteOid, snaActions);

            // Register the action handler
            whiteboardRegistration = ctx.registerService(WhiteboardHandler.class,
                    new AuroralHttpWhiteboardHandler(snaActions, httpClient.getClient(), nodeUrl, localOid, remoteOid),
                    new Hashtable<>(
                            Map.of(WhiteboardConstants.PROP_MODEL_PACKAGE_URI, providerInfo.get("modelPackageUri"),
                                    WhiteboardConstants.PROP_MODEL, providerInfo.get("model"),
                                    WhiteboardConstants.PROP_PROVIDERS, providerInfo.get("provider"))));
        } catch (Exception e) {
            logger.error("Error creating provider {}", providerName, e);
            throw e;
        }
    }

    /**
     * Component deactivated
     */
    @Deactivate
    void deactivate() throws Exception {
        if (whiteboardRegistration != null) {
            whiteboardRegistration.unregister();
            whiteboardRegistration = null;
        }

        if (providerName != null) {
            try {
                thread.execute(new AbstractTwinCommand<Void>() {
                    @Override
                    protected Promise<Void> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                        try {
                            SensinactProvider provider = twin.getProvider(providerName);
                            if (provider != null) {
                                provider.delete();
                            }
                            return pf.resolved(null);
                        } catch (Exception e) {
                            return pf.failed(e);
                        }
                    }
                }).getValue();
            } catch (Exception e) {
                logger.error("Error deleting provider {}", providerName, e);
                throw e;
            }
        }
    }

    private Map<String, String> createProvider(String providerModelPackageUri, String providerModel,
            String providerName, URI nodeUrl, String localOid, String remoteOid, Collection<SnaActionDTO> actions)
            throws Exception {
        return thread.execute(new AbstractSensinactCommand<Map<String, String>>() {
            @Override
            protected Promise<Map<String, String>> call(SensinactDigitalTwin twin, SensinactModelManager modelMgr,
                    PromiseFactory pf) {
                try {
                    // Setup the model
                    Model model;
                    if (providerModel != null) {
                        model = modelMgr.getModel(providerModelPackageUri, providerModel);
                    } else {
                        model = modelMgr.getModel(providerName);
                    }

                    // Check if a provider already exists
                    SensinactProvider provider = twin.getProvider(providerName);
                    if (model == null) {
                        // Model doesn't exist
                        if (provider != null) {
                            // ... but a provider exists: it will have an incompatible model
                            throw new IllegalStateException("A provider already exists with a different model");
                        }

                        // Create the model
                        model = modelMgr.createModel(providerModelPackageUri,
                                providerModel != null ? providerModel : providerName).build();
                    }

                    // Add actions
                    if (actions != null) {
                        registerActions(model, actions);
                    }

                    if (provider == null) {
                        // Create the provider
                        provider = twin.createProvider(model.getPackageUri(), model.getName(), providerName);
                    }
                    return pf.resolved(Map.of("modelPackageUri", provider.getModelPackageUri(), "model",
                            provider.getModelName(), "provider", provider.getName()));
                } catch (Exception e) {
                    return pf.failed(e);
                }
            }

            private void registerActions(final Model model, final Collection<SnaActionDTO> actions) {
                for (SnaActionDTO action : actions) {
                    // Prepare the service
                    final String svcName = action.service != null ? action.service : DEFAULT_SERVICE;
                    Service svc = model.getServices().get(svcName);
                    if (svc == null) {
                        svc = model.createService(svcName).build();
                    }

                    // Prepare the arguments
                    final List<Entry<String, Class<?>>> params = new ArrayList<>();
                    for (SnaActionArgumentDTO argDto : action.arguments.values()) {
                        params.add(Map.entry(argDto.name, argDto.type));
                    }

                    Resource existingRc = svc.getResources().get(action.name);
                    if (existingRc != null) {
                        if (existingRc.getResourceType() != ResourceType.ACTION) {
                            // Resource name already taken
                            logger.error("A resource with name {} already exists but is not action in model {}",
                                    action.name, model.getName());
                            throw new IllegalStateException(
                                    "Action name '" + action.name + "' already taken by another resource");
                        } else {
                            // Ignore the action as it's already registered
                            logger.debug("Already existing action {} is not updated in model {}", action.name,
                                    model.getName());
                            continue;
                        }
                    }

                    // Register the action
                    svc.createResource(action.name).withResourceType(ResourceType.ACTION).withAction(params)
                            .withType(action.resultType)
                            .withDefaultMetadata(
                                    Map.of("description", action.description != null ? action.description : ""))
                            .build();
                }
            }
        }).getValue();
    }
}

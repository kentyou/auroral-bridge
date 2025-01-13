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

package com.kentyou.auroral.luxactive.oha;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.sensinact.core.command.AbstractTwinCommand;
import org.eclipse.sensinact.core.command.GatewayThread;
import org.eclipse.sensinact.core.push.DataUpdate;
import org.eclipse.sensinact.core.push.dto.BulkGenericDto;
import org.eclipse.sensinact.core.push.dto.GenericDto;
import org.eclipse.sensinact.core.twin.SensinactDigitalTwin;
import org.eclipse.sensinact.core.twin.SensinactProvider;
import org.eclipse.sensinact.core.twin.SensinactResource;
import org.eclipse.sensinact.core.twin.SensinactService;
import org.osgi.service.component.annotations.Activate;
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
import com.kentyou.auroral.luxactive.oha.dto.AbstractRouteDTO;
import com.kentyou.auroral.luxactive.oha.dto.DtoMaker;
import com.kentyou.auroral.luxactive.oha.dto.StatEntryDTO;
import com.kentyou.auroral.luxactive.oha.dto.StatLogEntryDTO;

@Component(immediate = true, configurationPid = "luxactive.oha.statistics", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class LuxActiveHistoryProvider {

    private final static Logger logger = LoggerFactory.getLogger(LuxActiveHistoryProvider.class);

    /**
     * Gateway thread
     */
    @Reference
    private GatewayThread thread;

    /**
     * Update by DTO
     */
    @Reference
    private DataUpdate push;

    /**
     * JSON mapper
     */
    final ObjectMapper mapper = JsonMapper.builder().build();

    /**
     * Scheduled updates
     */
    private ScheduledExecutorService scheduler;

    /**
     * History provider name
     */
    private String providerName;

    /**
     * Auroral explicit bridge
     */
    private String bridgeProvider;

    /**
     * Auroral bridge service name
     */
    private String bridgeServiceName;

    /**
     * Auroral bridge log resource name
     */
    private String bridgeActionName;

    /**
     * LuxActive oHA realm ID
     */
    private String realmId;

    @Activate
    void activate(final Configuration config) throws Exception {
        providerName = config.provider();
        if (providerName == null || providerName.isBlank()) {
            logger.error("Provider name not set");
            throw new IllegalArgumentException("Provider name not set");
        }

        bridgeProvider = config.bridge_provider();
        if (bridgeProvider == null || bridgeProvider.isBlank()) {
            logger.error("Auroral Bridge provider not set");
            throw new IllegalArgumentException("Auroral Bridge provider not set");
        }

        bridgeServiceName = config.bridge_action_service();
        if (bridgeServiceName == null || bridgeServiceName.isBlank()) {
            logger.error("Auroral Bridge service not set");
            throw new IllegalArgumentException("Auroral Bridge service not set");
        }

        bridgeActionName = config.bridge_action();
        if (bridgeActionName == null || bridgeActionName.isBlank()) {
            logger.error("Auroral Bridge action not set");
            throw new IllegalArgumentException("Auroral Bridge action not set");
        }

        realmId = config.oha_realm();
        if (realmId == null || realmId.isBlank()) {
            logger.error("LuxActive realm ID not set");
            throw new IllegalArgumentException("LuxActive realm ID not set");
        }

        scheduler = new ScheduledThreadPoolExecutor(1);

        // Call the update method right now to feed in current values
        tryUpdateCurrentValues();

        // Call the update method now then every hh:05
        final Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        final long initialDelay = Duration.between(now, now.truncatedTo(ChronoUnit.HOURS).plus(65, ChronoUnit.MINUTES))
                .toMinutes();
        scheduler.scheduleAtFixedRate(this::updateCurrentValues, initialDelay, 60, TimeUnit.MINUTES);
    }

    @Deactivate
    void deactivate() throws Exception {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Tries to update the current values, or tries again in 5 seconds
     */
    void tryUpdateCurrentValues() {
        if (!updateCurrentValues()) {
            scheduler.schedule(this::tryUpdateCurrentValues, 5, TimeUnit.SECONDS);
        }
    }

    private Promise<Object> callAction(final SensinactDigitalTwin twin, final PromiseFactory pf,
            final Map<String, Object> parameters) {
        try {
            SensinactProvider provider = twin.getProvider(bridgeProvider);
            if (provider == null) {
                return pf.failed(
                        new IllegalStateException("Auroral Bridge provider" + bridgeProvider + " is not available"));
            }

            SensinactService actionSvc = provider.getServices().get(bridgeServiceName);
            if (actionSvc == null) {
                return pf.failed(new IllegalStateException(
                        "No '" + bridgeServiceName + "' service found in bridge " + bridgeProvider));
            }

            SensinactResource action = actionSvc.getResources().get(bridgeActionName);
            if (action == null) {
                return pf.failed(new IllegalStateException(
                        "No '" + bridgeActionName + "' action found in bridge " + bridgeProvider));
            }

            return action.act(parameters);
        } catch (Throwable t) {
            return pf.failed(t);
        }
    }

    /**
     * Get the latest values and update the current resources accordingly
     */
    boolean updateCurrentValues() {
        try {
            final Object rawResult = thread.execute(new AbstractTwinCommand<Object>() {
                @Override
                protected Promise<Object> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                    // TODO set arguments
                    return callAction(twin, pf, Map.of());
                }
            }).getValue();

            if (rawResult == null || !(rawResult instanceof List)) {
                logger.warn("Invalid result returned by action");
                return false;
            }

            final List<StatEntryDTO> statEntries = mapper.convertValue(rawResult,
                    new TypeReference<List<StatEntryDTO>>() {
                    });

            final Map<String, List<GenericDto>> latestDetails = new HashMap<>();
            for (StatEntryDTO statEntry : statEntries) {
                if (statEntry.timestamp == null || statEntry.timestamp.isBlank()) {
                    logger.debug("Ignore entry without timestamp: {}", statEntry);
                    continue;
                }

                final Instant timestamp = Instant.parse(statEntry.timestamp);
                if (statEntry.perRoute == null || statEntry.perRoute.isEmpty()) {
                    logger.debug("No data at {}", timestamp);
                    continue;
                }

                for (StatLogEntryDTO logEntry : statEntry.perRoute) {
                    final AbstractRouteDTO routeDto = logEntry.getRoute();
                    if (routeDto == null) {
                        logger.debug("Couldn't get details of log entry at {}", timestamp);
                        continue;
                    }

                    final List<GenericDto> currentDtos = latestDetails.get(routeDto.getId());
                    if (currentDtos == null || currentDtos.isEmpty()
                            || currentDtos.get(0).timestamp.isBefore(timestamp)) {
                        // We have a new or newer value
                        latestDetails.put(routeDto.getId(), logEntry.toDto(new DtoMaker() {
                            @Override
                            public <T> GenericDto make(String rcName, T value, Class<T> type) {
                                final GenericDto result = new GenericDto();
                                result.timestamp = timestamp;
                                result.provider = providerName;
                                result.service = routeDto.getId();
                                result.resource = rcName;
                                result.value = value;
                                result.type = type;
                                return result;
                            }
                        }));
                    }
                }
            }

            // Update the twin
            final BulkGenericDto bulk = new BulkGenericDto();
            bulk.dtos = latestDetails.values().stream().filter(Objects::nonNull).flatMap(l -> l.stream()).toList();
            if (!bulk.dtos.isEmpty()) {
                push.pushUpdate(bulk).getValue();
            }
            return true;
        } catch (Exception e) {
            logger.error("Error updating current values", e);
            return false;
        }
    }
}

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

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.sensinact.core.command.AbstractTwinCommand;
import org.eclipse.sensinact.core.command.GatewayThread;
import org.eclipse.sensinact.core.push.DataUpdate;
import org.eclipse.sensinact.core.push.dto.BulkGenericDto;
import org.eclipse.sensinact.core.push.dto.GenericDto;
import org.eclipse.sensinact.core.twin.SensinactDigitalTwin;
import org.eclipse.sensinact.core.twin.SensinactProvider;
import org.eclipse.sensinact.core.twin.SensinactResource;
import org.eclipse.sensinact.core.twin.SensinactService;
import org.eclipse.sensinact.core.twin.TimedValue;
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
import com.kentyou.sensinact.history.dto.single.HistoricResourceValue;
import com.kentyou.sensinact.history.dto.single.SingleHistoricValueResponse;
import com.kentyou.sensinact.history.spi.ResourcePath;
import com.kentyou.sensinact.history.spi.SimpleHistory;

@Component(immediate = true, configurationPid = "luxactive.oha.statistics", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class LuxActiveHistoryProvider implements SimpleHistory {

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

    /**
     * Time offset for each queries
     */
    private ZoneId timeZone;

    /**
     * Keep track of known route IDs to tell the history provider and of their type
     */
    private Map<String, String> knownRouteIds = new HashMap<>();

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

        final String strTz = config.oha_timezone();
        if (strTz == null || strTz.isBlank()) {
            timeZone = ZoneId.of("Z");
        } else {
            timeZone = ZoneId.of(strTz);
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

    private List<StatEntryDTO> loadStatEntries(final String routeType, final OffsetDateTime dateFrom,
            final OffsetDateTime dateTo, final String aggregationWindow)
            throws InvocationTargetException, InterruptedException {

        final Object rawResult = thread.execute(new AbstractTwinCommand<Object>() {
            @Override
            protected Promise<Object> call(SensinactDigitalTwin twin, PromiseFactory pf) {
                return callAction(twin, pf, Map.of("route_type", routeType, "realm_id", realmId, "date_from",
                        dateFrom.format(DateTimeFormatter.ISO_OFFSET_DATE), "date_to",
                        dateTo.format(DateTimeFormatter.ISO_OFFSET_DATE), "aggregation_window", aggregationWindow));
            }
        }).getValue();

        if (rawResult == null || !(rawResult instanceof List)) {
            logger.warn("Invalid result returned by action");
            return null;
        }

        return mapper.convertValue(rawResult, new TypeReference<List<StatEntryDTO>>() {
        });
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
            final List<StatEntryDTO> statEntries = loadStatEntries("all", OffsetDateTime.now(timeZone).minusDays(2),
                    OffsetDateTime.now(timeZone), "hour");
            if (statEntries == null) {
                // No entries
                return false;
            }
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

                        knownRouteIds.put(routeDto.getId(), routeDto.getType());
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

    @Override
    public Set<ResourcePath> getProvidedPaths() {
        final Map<String, List<String>> rcPerRouteType = knownRouteIds.values().stream().distinct()
                .collect(Collectors.toMap(Function.identity(), StatLogEntryDTO::getResources));

        return knownRouteIds.entrySet().stream().flatMap(e -> {
            final String routeId = e.getKey();
            final List<String> rcs = rcPerRouteType.get(e.getValue());
            if (rcs == null || rcs.isEmpty()) {
                return Stream.empty();
            }
            return rcs.stream().map(rcName -> new ResourcePath(providerName, providerName, routeId, rcName));
        }).collect(Collectors.toSet());
    }

    /**
     * Prepare an historic resource value bean
     *
     * @param routeId   oHA route ID
     * @param rcName    Resource (sensiNact) name
     * @param value     Historic value
     * @param timestamp Value time stamp
     * @return An historic resource value bean
     */
    private HistoricResourceValue makeHistoricResourceValue(final String routeId, final String rcName,
            final Object value, final Instant timestamp) {
        final HistoricResourceValue hrv = new HistoricResourceValue();
        hrv.provider = providerName;
        hrv.service = routeId;
        hrv.resource = rcName;
        hrv.dto = new TimedValue<Object>() {
            @Override
            public Instant getTimestamp() {
                return timestamp;
            }

            @Override
            public Object getValue() {
                return value;
            }
        };
        return hrv;
    }

    @Override
    public SingleHistoricValueResponse getValueRange(final String provider, final String service, final String resource,
            final Instant fromTime, final Instant toTime) {
        if (!providerName.equals(provider)) {
            // Wrong provider
            return null;
        }

        final List<StatEntryDTO> statEntries;
        try {
            statEntries = loadStatEntries("all", OffsetDateTime.ofInstant(fromTime, timeZone),
                    OffsetDateTime.ofInstant(toTime, timeZone), "hour");
        } catch (Exception e) {
            logger.error("Error retrieving statistics for provider {} and route ID {}", provider, service, e);
            return null;
        }
        if (statEntries == null) {
            return null;
        }

        final List<HistoricResourceValue> hrcs = new ArrayList<>();
        for (StatEntryDTO stat : statEntries) {
            final Instant timestamp = Instant.parse(stat.timestamp);
            final Optional<StatLogEntryDTO> optLogEntry = stat.perRoute.stream().filter(
                    sle -> Optional.ofNullable(sle.getRoute()).map(r -> service.equals(r.getId())).orElse(false))
                    .findFirst();
            hrcs.add(makeHistoricResourceValue(service, resource,
                    optLogEntry.map(le -> le.getFieldByName(resource)).orElse(null), timestamp));
        }

        final SingleHistoricValueResponse shvr = new SingleHistoricValueResponse();
        shvr.values = hrcs;
        return shvr;
    }
}

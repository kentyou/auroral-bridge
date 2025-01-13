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

package com.kentyou.auroral.luxactive.oha.dto;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.sensinact.core.push.dto.GenericDto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StatLogEntryDTO {

    @JsonProperty("log_total_retention_time")
    public double totalRetentionTime;

    @JsonProperty("log_average_retention_time")
    public double averageRetentionTime;

    @JsonProperty("log_count")
    public int count;

    @JsonProperty("log_device_count")
    public int deviceCount;

    @JsonProperty("log_session_count")
    public int sessionCount;

    @JsonProperty("log_source_qrcode_count")
    public int sourceQrCodeCount;

    @JsonProperty("log_source_qrcode_count_per_mille")
    public int sourceQrCodeCountPerMille;

    public int rank;

    /**
     * Described route type
     */
    @JsonProperty("log_route_type")
    public String routeType;

    /**
     * Activity description
     */
    public ActivityDTO activity;

    /**
     * Article description
     */
    public ArticleDTO article;

    /**
     * Page description
     */
    public PageDTO page;

    public List<GenericDto> toDto(final DtoMaker maker) {
        final AbstractRouteDTO route = getRoute();
        if (route == null) {
            return null;
        }

        final List<GenericDto> dtos = new ArrayList<>();
        dtos.add(maker.make("type", routeType, String.class));
        dtos.add(maker.make("rank", rank, Integer.class));
        dtos.add(maker.make("totalRetentionTime", totalRetentionTime, Double.class));
        dtos.add(maker.make("averageRetentionTime", averageRetentionTime, Double.class));
        dtos.add(maker.make("logCount", count, Integer.class));
        dtos.add(maker.make("deviceCount", deviceCount, Integer.class));
        dtos.add(maker.make("sessionCount", sessionCount, Integer.class));
        dtos.add(maker.make("qrCodeCount", sourceQrCodeCount, Integer.class));
        dtos.add(maker.make("qrCodeCountPerMille", sourceQrCodeCountPerMille, Integer.class));
        dtos.addAll(route.toDto(maker));
        return dtos;
    }

    /**
     * Returns the subject of this log or null
     */
    public AbstractRouteDTO getRoute() {
        if (routeType == null) {
            return null;
        }

        switch (routeType) {
        case "activity":
            return activity;

        case "article":
            return article;

        case "page":
            return page;

        default:
            return null;
        }
    }
}

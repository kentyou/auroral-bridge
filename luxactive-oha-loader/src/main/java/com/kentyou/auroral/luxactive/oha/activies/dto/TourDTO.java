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

package com.kentyou.auroral.luxactive.oha.activies.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TourDTO {

    /**
     * Tour difficulty
     */
    public Integer difficulty;

    /**
     * List of list of (latitude, longitude) points. Not a valid GeoJSON as it
     * doesn't ends with the start point.
     *
     * We might consider it just a list of points
     */
    @JsonProperty("geopolygon")
    public List<List<Double>> geoPolygon;
}

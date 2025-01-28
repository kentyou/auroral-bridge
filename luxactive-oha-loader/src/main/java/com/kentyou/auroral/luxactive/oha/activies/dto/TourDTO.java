/*
 * Copyright 2025 Kentyou
 * Proprietary and confidential
 *
 * All Rights Reserved.
 * Unauthorized copying of this file is strictly prohibited
 */

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

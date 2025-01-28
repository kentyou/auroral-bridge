/*
 * Copyright 2025 Kentyou
 * Proprietary and confidential
 *
 * All Rights Reserved.
 * Unauthorized copying of this file is strictly prohibited
 */

package com.kentyou.auroral.luxactive.oha.activies.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LocationDTO {

    /**
     * Latitude
     */
    @JsonProperty("lat")
    public Double latitude;

    /**
     * Longitude
     */
    @JsonProperty("long")
    public Double longitude;

    /**
     * Altitude (can be null)
     */
    @JsonProperty("alt")
    public Double altitude;
}

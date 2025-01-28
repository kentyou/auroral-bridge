/*
 * Copyright 2025 Kentyou
 * Proprietary and confidential
 *
 * All Rights Reserved.
 * Unauthorized copying of this file is strictly prohibited
 */

package com.kentyou.auroral.luxactive.oha.activies.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EventDTO {

    /**
     * Start time
     */
    public Instant start;

    /**
     * End time
     */
    public Instant end;
}

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

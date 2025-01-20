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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StatEntryDTO {

    /**
     * Entry timestamp
     */
    public String timestamp;

    /**
     * Total number of log entries
     */
    @JsonProperty("log_total_count")
    public int totalCount;

    /**
     * Log entries per route
     */
    @JsonProperty("per_route")
    public List<StatLogEntryDTO> perRoute;
}

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

package com.kentyou.auroral.configuration;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ActionDTO {

    /**
     * Name of the action resource in sensiNact
     */
    @JsonProperty("sna.alias")
    public String snaName;

    /**
     * Description of the action
     */
    public String description;

    /**
     * Name of the service holding the action
     */
    public String service;

    /**
     * Result type name (Web of Thing type alias)
     */
    public String resultType;

    /**
     * Remote argument -&gt; argument description
     */
    public Map<String, ActionArgumentDTO> arguments;
}

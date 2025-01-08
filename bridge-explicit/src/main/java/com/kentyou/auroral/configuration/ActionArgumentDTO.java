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

import com.fasterxml.jackson.annotation.JsonProperty;

public class ActionArgumentDTO {

    /**
     * Alias of the argument in the sensiNact action (null or empy to reuse the
     * property name as is)
     */
    @JsonProperty("sna.alias")
    public String snaName;

    /**
     * Resource value type name (Web of Thing type alias)
     */
    public String type;

    /**
     * Argument description
     */
    public String description;
}

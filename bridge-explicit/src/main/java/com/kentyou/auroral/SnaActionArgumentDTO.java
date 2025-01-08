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

package com.kentyou.auroral;

import org.eclipse.sensinact.gateway.southbound.wot.api.constants.Utils;

import com.kentyou.auroral.configuration.ActionArgumentDTO;

public class SnaActionArgumentDTO {
    /**
     * Argument name in sensiNact
     */
    public String name;

    /**
     * Argument name in Auroral
     */
    public String auroralName;

    /**
     * Resource value type
     */
    public Class<?> type;

    /**
     * Argument description
     */
    public String description;

    public static SnaActionArgumentDTO fromAuroral(final String auroralName, final ActionArgumentDTO dto) {
        final SnaActionArgumentDTO saa = new SnaActionArgumentDTO();
        saa.auroralName = auroralName;
        saa.name = dto.snaName != null && !dto.snaName.isBlank() ? dto.snaName : auroralName;
        saa.description = dto.description;
        saa.type = Utils.classFromType(auroralName);
        return saa;
    }
}

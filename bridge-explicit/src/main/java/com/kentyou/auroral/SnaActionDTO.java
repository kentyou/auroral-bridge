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

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.eclipse.sensinact.gateway.southbound.wot.api.constants.Utils;

import com.kentyou.auroral.configuration.ActionArgumentDTO;
import com.kentyou.auroral.configuration.ActionDTO;

public class SnaActionDTO {
    /**
     * Name of the action resource in sensiNact
     */
    public String name;

    /**
     * Name of the action in Auroral
     */
    public String auroralName;

    /**
     * Description of the action
     */
    public String description;

    /**
     * Name of the service holding the action
     */
    public String service;

    /**
     * Result type class
     */
    public Class<?> resultType;

    /**
     * SensiNact argument -&gt; argument description
     */
    public Map<String, SnaActionArgumentDTO> arguments;

    public static SnaActionDTO fromAuroral(final String auroralName, final ActionDTO dto) {
        final SnaActionDTO sa = new SnaActionDTO();
        sa.auroralName = auroralName;
        sa.name = dto.snaName != null && !dto.snaName.isBlank() ? dto.snaName : auroralName;
        sa.description = dto.description;
        sa.service = dto.service != null && !dto.service.isBlank() ? dto.service
                : AuroralExplicitBridge.DEFAULT_SERVICE;
        sa.resultType = Utils.classFromType(dto.resultType);
        sa.arguments = dto.arguments.entrySet().stream().map(e -> {
            final String auroralArgName = e.getKey();
            final ActionArgumentDTO argDto = e.getValue();
            return Map.entry(argDto.snaName != null && !argDto.snaName.isBlank() ? argDto.snaName : auroralArgName,
                    SnaActionArgumentDTO.fromAuroral(auroralArgName, e.getValue()));
        }).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        return sa;
    }
}

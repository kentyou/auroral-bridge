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

import org.eclipse.sensinact.core.push.dto.GenericDto;

public abstract class AbstractRouteDTO {

    /**
     * Returns the ID of the route
     */
    public abstract String getId();

    /**
     * Converts the route to a list of DTO
     *
     * @param maker DTO generator
     * @return The generated DTOs (can't be null)
     */
    public abstract List<GenericDto> toDto(DtoMaker maker);

    /**
     * Returns the type of route
     *
     * @return Kind of route
     */
    public abstract String getType();

    /**
     * Return the value of a field by its resource name
     *
     * @param rcName Name of the resource
     * @return The value of that field or null
     */
    public abstract Object getFieldByName(String rcName);
}

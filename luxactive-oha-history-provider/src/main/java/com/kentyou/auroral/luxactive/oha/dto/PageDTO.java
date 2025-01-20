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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.sensinact.core.push.dto.GenericDto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PageDTO extends AbstractRouteDTO {

    public static final String RC_ID = "id";
    public static final String RC_TITLE = "title";
    public static final String RC_LANGUAGE = "language";
    public static final String RC_PAGE_TYPE = "pageType";
    public static final String[] RESOURCES = { RC_ID, RC_TITLE, RC_LANGUAGE, RC_PAGE_TYPE };

    @JsonProperty("page_id")
    public String id;

    @JsonProperty("page_title")
    public String title;

    @JsonProperty("page_language")
    public String language;

    @JsonProperty("page_type")
    public String type;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<GenericDto> toDto(final DtoMaker maker) {
        final List<GenericDto> dtos = new ArrayList<>(4);
        dtos.add(maker.make(RC_ID, id, String.class));
        dtos.add(maker.make(RC_TITLE, title, String.class));
        dtos.add(maker.make(RC_LANGUAGE, language, String.class));
        dtos.add(maker.make(RC_PAGE_TYPE, type, String.class));
        return dtos;
    }

    @Override
    public String getType() {
        return "page";
    }

    @Override
    public Object getFieldByName(String rcName) {
        switch (rcName) {
            case RC_ID:
                return this.id;
            case RC_TITLE:
                return this.title;
            case RC_LANGUAGE:
                return this.language;
            case RC_PAGE_TYPE:
                return this.type;
            default:
                return null;
        }
    }
}

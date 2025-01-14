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

public class ArticleDTO extends AbstractRouteDTO {

    public static final String RC_ID = "id";
    public static final String RC_TITLE = "title";
    public static final String RC_PAGE_ID = "pageId";
    public static final String[] RESOURCES = { RC_ID, RC_TITLE, RC_PAGE_ID };

    @JsonProperty("article_id")
    public String id;

    @JsonProperty("article_title")
    public String title;

    @JsonProperty("page_article_id")
    public String pageId;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<GenericDto> toDto(final DtoMaker maker) {
        final List<GenericDto> dtos = new ArrayList<>(3);
        dtos.add(maker.make(RC_ID, id, String.class));
        dtos.add(maker.make(RC_TITLE, title, String.class));
        dtos.add(maker.make(RC_PAGE_ID, pageId, String.class));
        return dtos;
    }

    @Override
    public String getType() {
        return "article";
    }

    @Override
    public Object getFieldByName(String rcName) {
        switch (rcName) {
            case RC_ID:
                return this.id;
            case RC_TITLE:
                return this.title;
            case RC_PAGE_ID:
                return this.pageId;
            default:
                return null;
        }
    }
}

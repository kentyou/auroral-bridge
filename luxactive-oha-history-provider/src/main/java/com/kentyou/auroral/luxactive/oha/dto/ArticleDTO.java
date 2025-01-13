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
        dtos.add(maker.make("id", id, String.class));
        dtos.add(maker.make("title", title, String.class));
        dtos.add(maker.make("pageId", pageId, String.class));
        return dtos;
    }
}

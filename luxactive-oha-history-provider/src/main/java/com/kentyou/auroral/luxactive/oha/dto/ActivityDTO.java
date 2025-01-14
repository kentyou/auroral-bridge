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

public class ActivityDTO extends AbstractRouteDTO {

    public static final String RC_ID = "id";
    public static final String RC_TITLE = "title";
    public static final String RC_IMAGE = "image";
    public static final String RC_CHANNEL_ID = "channelId";
    public static final String[] RESOURCES = { RC_ID, RC_TITLE, RC_IMAGE, RC_CHANNEL_ID };

    @JsonProperty("activity_id")
    public String id;

    @JsonProperty("activity_image")
    public String image;

    @JsonProperty("activity_title")
    public String title;

    @JsonProperty("channel_id")
    public String channelId;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<GenericDto> toDto(final DtoMaker maker) {
        final List<GenericDto> dtos = new ArrayList<>(4);
        dtos.add(maker.make(RC_ID, id, String.class));
        dtos.add(maker.make(RC_TITLE, title, String.class));
        dtos.add(maker.make(RC_IMAGE, image, String.class));
        dtos.add(maker.make(RC_CHANNEL_ID, channelId, String.class));
        return dtos;
    }

    @Override
    public String getType() {
        return "activity";
    }

    @Override
    public Object getFieldByName(String rcName) {
        switch (rcName) {
            case RC_ID:
                return this.id;
            case RC_TITLE:
                return this.title;
            case RC_IMAGE:
                return this.image;
            case RC_CHANNEL_ID:
                return this.channelId;
            default:
                return null;
        }
    }
}

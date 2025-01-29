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

package com.kentyou.auroral.luxactive.oha.activies;

import java.time.Instant;

import org.eclipse.sensinact.core.annotation.dto.Data;
import org.eclipse.sensinact.core.annotation.dto.Model;
import org.eclipse.sensinact.core.annotation.dto.ModelPackageUri;
import org.eclipse.sensinact.core.annotation.dto.Provider;
import org.eclipse.sensinact.core.annotation.dto.Resource;
import org.eclipse.sensinact.core.annotation.dto.Service;
import org.eclipse.sensinact.core.annotation.dto.Timestamp;
import org.eclipse.sensinact.gateway.geojson.Coordinates;
import org.eclipse.sensinact.gateway.geojson.GeoJsonObject;
import org.eclipse.sensinact.gateway.geojson.Point;

import com.kentyou.auroral.luxactive.oha.activies.dto.ActivityDescriptionDTO;
import com.kentyou.auroral.luxactive.oha.activies.dto.LocationDTO;

/**
 * DTO sent to sensiNact to describe an activity
 */
public class SensiNactActivityDTO {

    /**
     * sensiNact model package URI
     */
    @ModelPackageUri
    public String modelPackageUri;

    /**
     * sensiNact model
     */
    @Model
    public String model;

    /**
     * Provider name
     */
    @Provider
    public String provider;

    /**
     * Update timestamp
     */
    @Timestamp
    public Instant timestamp;

    /**
     * Activity title
     */
    @Service("admin")
    @Resource("friendlyName")
    @Data
    public String title;

    /**
     * Activity location
     */
    @Service("admin")
    @Data
    public GeoJsonObject location;

    /**
     * Activity ID
     */
    @Service("activity")
    @Data
    public String id;

    /**
     * Activity language
     */
    @Service("activity")
    @Data
    public String language;

    /**
     * Activity description
     */
    @Service("activity")
    @Data
    public String description;

    /**
     * Activity description with HTML tags
     */
    @Service("activity")
    @Data
    public String htmlDescription;

    /**
     * Activity long description
     */
    @Service("activity")
    @Data
    public String longDescription;

    /**
     * Activity long description with HTML tags
     */
    @Service("activity")
    @Data
    public String htmlLongDescription;

    /**
     * Activity cover image
     */
    @Service("activity")
    @Data
    public String coverImage;

    /**
     * ID of the activity channel
     */
    @Service("activity")
    @Data
    public String channelId;

    /**
     * Name of the activity channel
     */
    @Service("activity")
    @Data
    public String channelName;

    /**
     * Auroral property used to load this activity
     */
    @Service("auroral")
    @Resource("property")
    @Data
    public String auroralProperty;

    /**
     * Removes HTML tags of the given text
     *
     * @param html HTML text
     * @return The text without HTML tags
     */
    static String removeHtmlTags(final String html) {
        return html.replace("<br>", "\n").replace("</p>", "\n").replaceAll("</?[^>]+>", "").replace("&nbsp;", " ")
                .trim();
    }

    /**
     * Construct this bean from a loaded DTO
     *
     * @param auroralProperty Auroral property used to load the activity description
     * @param modelPackageUri Model package URI
     * @param model           sensiNact model name
     * @param providerName    Name of the provider
     * @param description     Parsed description
     * @return A sensiNact update DTO
     */
    public static SensiNactActivityDTO fromDTO(final String auroralProperty, final String modelPackageUri,
            final String model, final String providerName, final ActivityDescriptionDTO description) {
        final SensiNactActivityDTO dto = new SensiNactActivityDTO();
        if (modelPackageUri != null && !modelPackageUri.isBlank()) {
            dto.modelPackageUri = modelPackageUri;
        }
        if (model != null && !model.isBlank()) {
            dto.model = model;
        }
        dto.provider = providerName;
        dto.timestamp = description.lastModified;
        dto.id = description.identifier;
        dto.title = description.title;
        dto.language = description.language;
        dto.htmlDescription = description.shortDescription;
        dto.description = removeHtmlTags(description.shortDescription);
        dto.htmlLongDescription = description.longDescription;
        dto.longDescription = removeHtmlTags(description.longDescription);
        dto.coverImage = description.coverImage;
        dto.auroralProperty = auroralProperty;

        if (description.channel != null) {
            dto.channelId = description.channel.id;
            dto.channelName = description.channel.name;
        }

        if (description.location != null) {
            final LocationDTO loc = description.location;
            if (loc.longitude != null && loc.latitude != null && Double.isFinite(loc.longitude)
                    && Double.isFinite(loc.latitude)) {
                final Point pt = new Point();
                pt.coordinates = new Coordinates();
                pt.coordinates.longitude = loc.longitude;
                pt.coordinates.latitude = loc.latitude;
                if (loc.altitude != null && Double.isFinite(loc.altitude)) {
                    pt.coordinates.elevation = loc.altitude;
                }

                dto.location = pt;
            }
        }

        return dto;
    }
}

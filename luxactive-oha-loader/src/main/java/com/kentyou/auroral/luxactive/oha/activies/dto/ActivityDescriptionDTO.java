/*
 * Copyright 2025 Kentyou
 * Proprietary and confidential
 *
 * All Rights Reserved.
 * Unauthorized copying of this file is strictly prohibited
 */

package com.kentyou.auroral.luxactive.oha.activies.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Description of an activity. Should be based on JSON schema
 * https://auroralh2020.github.io/auroral-ontology-contexts/tour/context.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityDescriptionDTO {

    /**
     * URL to the JSON schema context
     */
    @JsonProperty("@context")
    public String jsonContext;

    /**
     * Activity identifier (UUID)
     */
    public String identifier;

    /**
     * Activity title
     */
    public String title;

    /**
     * Hidden description: empty for all activities we saw
     */
    @JsonProperty("hidden_description")
    public String hiddenDescription = "";

    /**
     * Long description in HTML
     */
    @JsonProperty("long_description")
    public String longDescription = "";

    /**
     * Always empty
     */
    @JsonProperty("long_description_not_searched")
    public String longDescriptionNotSearched = "";

    /**
     * Short description in HTML
     */
    @JsonProperty("short_description")
    public String shortDescription = "";

    /**
     * Flag to indicate if the activity can be booked
     */
    public boolean bookable;

    /**
     * URL to the booking service if the activity can be booked
     */
    @JsonProperty("external_booking_url")
    public String externalBookingUrl;

    /**
     * ISO DateTime of the last update of the activity
     */
    @JsonProperty("last_modified")
    public Instant lastModified;

    /**
     * HTML text displaying references, credits, ...
     */
    public String right;

    /**
     * Activity pricing (can be null)
     */
    public PriceDTO price;

    /**
     * Activity location
     */
    public LocationDTO location;

    /**
     * Activity language
     */
    public String language;

    /**
     * URL of the activity cover image (JPG)
     */
    @JsonProperty("cover_image")
    public String coverImage;

    /**
     * URLs of additional activity images
     */
    public List<String> images;

    /**
     * Category of map point: hidden_poi, hidden_tour
     */
    @JsonProperty("map_point_category")
    public String mapPointCategory;

    /**
     * Associated channel
     */
    public ChannelDTO channel;

    /**
     * Associated event
     */
    public EventDTO event;

    /**
     * Associated tour
     */
    public TourDTO tour;
}

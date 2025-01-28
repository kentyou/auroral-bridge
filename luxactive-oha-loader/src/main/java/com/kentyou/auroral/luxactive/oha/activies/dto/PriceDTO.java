/*
 * Copyright 2025 Kentyou
 * Proprietary and confidential
 *
 * All Rights Reserved.
 * Unauthorized copying of this file is strictly prohibited
 */

package com.kentyou.auroral.luxactive.oha.activies.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PriceDTO {

    /**
     * Price amount, can be null
     */
    public Double amount;

    /**
     * Price unit ("euro_cent", "euro", "Euro", "dollar", "Dollar", "$", "€")
     */
    @JsonProperty("in_currency")
    public String currency;
}

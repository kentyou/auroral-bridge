/*
 * Copyright 2025 Kentyou
 * Proprietary and confidential
 *
 * All Rights Reserved.
 * Unauthorized copying of this file is strictly prohibited
 */

package com.kentyou.auroral.luxactive.oha.activies;

public @interface LoaderConfiguration {

    /**
     * Template of the provider name, with a string entry for the activity ID
     */
    String oha_provider_template() default "oha-%s";

    /**
     * Provider model package URI
     */
    String oha_provider_model_package_uri();

    /**
     * Provider model name
     */
    String oha_provider_model() default "oha-activity";

    /**
     * URL to the Auroral node to use (without the /api/ path)
     */
    String auroral_node_url() default "http://localhost:81/";

    /**
     * ID of the contract local object
     */
    String oid_local();

    /**
     * ID of the contract remote (partner's) object
     */
    String oid_remote();

    /**
     * List of activities groups to load
     */
    String[] auroral_properties() default { "halogaland:activities" };
}

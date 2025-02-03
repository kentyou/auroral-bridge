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

package com.kentyou.auroral.configuration;

public @interface Configuration {

    /**
     * Provider model package URI
     */
    String provider_model_package_uri();

    /**
     * Provider model name
     */
    String provider_model();

    /**
     * Provider name
     */
    String provider_name();

    /**
     * URL to the Auroral node to use (without the /api/ path)
     */
    String auroral_node_url() default "http://localhost:81/";

    /**
     * Auroral Node basic authentication user
     */
    String auroral_node_basic_user();

    /**
     * Auroral Node basic authentication password
     */
    String auroral_node_basic_password();

    /**
     * ID of the contract local object
     */
    String oid_local();

    /**
     * ID of the contract remote (partner's) object
     */
    String oid_remote();

    /**
     * Description of the actions to register in sensiNact
     */
    String actions();
}

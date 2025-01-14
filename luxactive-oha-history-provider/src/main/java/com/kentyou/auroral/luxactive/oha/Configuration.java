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

package com.kentyou.auroral.luxactive.oha;

public @interface Configuration {
    /**
     * Provider name
     */
    String provider();

    /**
     * Name of the Auroral Explicit bridge provider
     */
    String bridge_provider();

    /**
     * Bridge action name
     */
    String bridge_action();

    /**
     * Bridge action service name
     */
    String bridge_action_service() default "auroral";

    /**
     * oHA Realm ID
     */
    String oha_realm();

    /**
     * Time zone for the oHA realm
     */
    String oha_time_zone() default "UTC";
}

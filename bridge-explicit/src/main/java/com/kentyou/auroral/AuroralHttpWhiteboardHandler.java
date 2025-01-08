/*********************************************************************
* Copyright (c) 2024 Kentyou.
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

package com.kentyou.auroral;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.sensinact.core.whiteboard.WhiteboardAct;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.PromiseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AuroralHttpWhiteboardHandler implements WhiteboardAct<Object> {

    private final static Logger logger = LoggerFactory.getLogger(AuroralHttpWhiteboardHandler.class);

    /**
     * Shared HTTP client
     */
    private final HttpClient client;

    /**
     * URI of the Auroral node
     */
    private final URI auroralNodeUri;

    /**
     * Local object ID
     */
    private final String localOid;

    /**
     * Partner's object ID
     */
    private final String remoteOid;

    /**
     * Definition of actions
     */
    private final Map<String, SnaActionDTO> actions;

    /**
     * DTO mapper
     */
    private final ObjectMapper mapper = new ObjectMapper();

    public AuroralHttpWhiteboardHandler(final List<SnaActionDTO> actions, final HttpClient client,
            final URI auroralNodeUri, final String localOid, final String remoteOid) {
        this.actions = actions.stream()
                .collect(Collectors.toMap(a -> String.join("/", a.service, a.name), Function.identity()));
        this.client = client;
        this.auroralNodeUri = auroralNodeUri;
        this.localOid = localOid;
        this.remoteOid = remoteOid;
    }

    @Override
    public Promise<Object> act(PromiseFactory pf, String modelPackageUri, String model, String provider, String service,
            String resource, Map<String, Object> arguments) {
        try {
            final SnaActionDTO action = actions.get(String.join("/", service, resource));
            if (action == null) {
                logger.error("Unknown action: {}/{}/{}", provider, service, resource);
                return pf.failed(new IllegalArgumentException("Unknown action: " + resource));
            }

            // Forge the URI
            final Request request = client.newRequest(auroralNodeUri)
                    .path("/api/properties/" + localOid + "/" + remoteOid + "/" + action.auroralName);

            // Prepare the query string
            for (Entry<String, Object> callArgs : arguments.entrySet()) {
                final Object value = callArgs.getValue();
                if (value != null) {
                    final SnaActionArgumentDTO argDto = action.arguments.get(callArgs.getKey());
                    if (argDto == null) {
                        logger.error("Unknown argument {} for action {}/{}/{}", callArgs.getKey(), provider, service,
                                resource);
                        return pf.failed(new IllegalArgumentException(
                                "Unknown argument '" + callArgs.getKey() + "' for action " + resource));
                    }
                    request.param(argDto.auroralName, String.valueOf(value));
                }
            }

            final Deferred<Object> promise = pf.deferred();
            request.send(new BufferingResponseListener() {
                @Override
                public void onComplete(Result result) {
                    if (result.isFailed()) {
                        logger.error("Failed to invoke action {}/{}/{}", provider, service, resource,
                                result.getFailure());
                        promise.fail(result.getFailure());
                        return;
                    }

                    final int status = result.getResponse().getStatus();
                    if (status >= 300) {
                        logger.error("Error querying HTTP endpoint for {}/{}/{}: {} ({})", provider, service, resource,
                                status, request.getURI());
                        promise.fail(new IOException("HTTP error " + status));
                        return;
                    }

                    try {
                        final String content = getContentAsString();
                        final Map<String, Object> rootResult = mapper.readValue(content,
                                new TypeReference<Map<String, Object>>() {
                                });

                        promise.resolve(mapper.convertValue(rootResult.get("message"), action.resultType));
                    } catch (Throwable e) {
                        logger.error("Failed to parse response from action {}/{}/{}", provider, service, resource, e);
                        promise.fail(e);
                    }
                }
            });
            return promise.getPromise();
        } catch (Throwable t) {
            logger.error("Error handling Auroral action", t);
            return pf.failed(t);
        }
    }
}

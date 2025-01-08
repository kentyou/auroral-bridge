/*********************************************************************
* Copyright (c) 2024 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   Kentyou - initial implementation
**********************************************************************/
package com.kentyou.auroral.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP request handler for tests
 */
public class RequestHandler extends AbstractHandler {

    public static interface CustomHandler {
        String handle(String path, Map<String, String> args) throws Exception;
    }

    final ObjectMapper mapper = JsonMapper.builder().build();

    private final Map<String, CustomHandler> serverHandlers = new HashMap<>();

    public void setHandler(final String path, final CustomHandler handler) {
        serverHandlers.put(path, handler);
    }

    public void clear() {
        serverHandlers.clear();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        baseRequest.setHandled(true);

        CustomHandler handler = serverHandlers.get(target);
        if (handler != null) {
            try {
                final Map<String, String> parsedArgs = request.getParameterMap().entrySet().stream()
                        .collect(Collectors.toMap(Entry::getKey, e -> {
                            final String[] values = e.getValue();
                            return values.length > 0 ? values[0] : "";
                        }));
                final String strResult = handler.handle(target, parsedArgs);
                final byte[] rawResult = strResult.getBytes(StandardCharsets.UTF_8);
                response.setStatus(200);
                response.setContentLength(rawResult.length);
                response.getOutputStream().write(rawResult);
                response.flushBuffer();
            } catch (Exception e) {
                e.printStackTrace();
                response.sendError(500);
            }
        } else {
            response.sendError(404);
        }
    }
}

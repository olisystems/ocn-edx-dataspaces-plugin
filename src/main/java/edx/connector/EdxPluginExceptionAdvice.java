/*
    Copyright 2026 OLI Systems GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */

package edx.connector;

import edx.connector.cdrservice.CdrServiceException;
import edx.connector.co2provider.Co2ProviderException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class EdxPluginExceptionAdvice {

    @ExceptionHandler(CdrServiceException.class)
    public ResponseEntity<String> handleCdrServiceException(CdrServiceException exception) {
        return upstreamError(exception.statusCode(), exception.responseBody(), exception.getMessage());
    }

    @ExceptionHandler(Co2ProviderException.class)
    public ResponseEntity<String> handleCo2ProviderException(Co2ProviderException exception) {
        return upstreamError(exception.statusCode(), exception.responseBody(), exception.getMessage());
    }

    private static ResponseEntity<String> upstreamError(int statusCode, String responseBody, String message) {
        int status = statusCode > 0 ? statusCode : 502;
        String body = responseBody == null ? message : responseBody;
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
    }
}

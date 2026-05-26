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

package edx.connector.co2provider;

public final class Co2ProviderException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public Co2ProviderException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public Co2ProviderException(String message, int statusCode, String responseBody) {
        super(message + ": HTTP " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public Co2ProviderException(String message, int statusCode, String responseBody, Throwable cause) {
        super(message + ": HTTP " + statusCode, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}

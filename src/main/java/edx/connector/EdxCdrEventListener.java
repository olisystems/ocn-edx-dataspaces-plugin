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

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import snc.openchargingnetwork.node.plugins.core.OcpiObjectEvent;

@Component
public class EdxCdrEventListener {

    private final CdrForwarder forwarder;

    public EdxCdrEventListener(CdrForwarder forwarder) {
        this.forwarder = forwarder;
    }

    @EventListener
    public void onOcpiObjectEvent(OcpiObjectEvent event) {
        forwarder.forwardIfCdr(event);
    }
}

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

import edx.connector.cdrservice.CdrIngestRequestDto;
import edx.connector.cdrservice.CdrIngestResponseDto;
import edx.connector.cdrservice.CdrServiceClient;
import edx.connector.edc.CpoAssetProvisioningService;
import edx.connector.persistence.CdrIngestMappingStore;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import snc.openchargingnetwork.node.models.ocpi.CDR;
import snc.openchargingnetwork.node.models.ocpi.ModuleID;
import snc.openchargingnetwork.node.plugins.core.OcpiObjectEvent;

public final class CdrForwarder {

    private static final Logger LOGGER = Logger.getLogger(CdrForwarder.class.getName());
    static final int OCPI_SUCCESS_STATUS_CODE = 1000;
    static final int FORWARD_QUEUE_CAPACITY = 256;

    private final CdrServiceClient cdrServiceClient;
    private final CdrIngestMappingStore mappingStore;
    private final CpoAssetProvisioningService assetProvisioningService;
    private final ThreadPoolExecutor executor;

    public CdrForwarder(
        CdrServiceClient cdrServiceClient,
        CdrIngestMappingStore mappingStore,
        CpoAssetProvisioningService assetProvisioningService
    ) {
        this.cdrServiceClient = cdrServiceClient;
        this.mappingStore = mappingStore;
        this.assetProvisioningService = assetProvisioningService;
        this.executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(FORWARD_QUEUE_CAPACITY),
            task -> {
                Thread thread = new Thread(task, "edx-cdr-forwarder");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void forwardIfCdr(OcpiObjectEvent event) {
        if (event.getModule() != ModuleID.CDRS || !(event.getPayload() instanceof CDR)) {
            return;
        }
        Integer ocpiStatusCode = event.getOcpiStatusCode();
        if (ocpiStatusCode == null || ocpiStatusCode != OCPI_SUCCESS_STATUS_CODE) {
            CDR cdr = (CDR) event.getPayload();
            LOGGER.info(
                "Skipping EDX CDR forward for " + cdr.getId()
                    + ": OCPI status code in response payload must be "
                    + OCPI_SUCCESS_STATUS_CODE
                    + " (was " + ocpiStatusCode + ")"
            );
            return;
        }
        try {
            executor.submit(() -> post(event));
        } catch (RejectedExecutionException e) {
            CDR cdr = (CDR) event.getPayload();
            LOGGER.log(
                Level.WARNING,
                "EDX CDR forward queue full (capacity=" + FORWARD_QUEUE_CAPACITY + "); dropping CDR " + cdr.getId(),
                e
            );
        }
    }

    private void post(OcpiObjectEvent event) {
        CDR cdr = (CDR) event.getPayload();
        try {
            // The source tenant must match the EDC asset's x-source key, which is derived
            // from the CDR body identity — routing headers may name a hub or differ in case.
            CdrIngestRequestDto request = CdrIngestRequestDto.of(
                cdr.getCountryCode(),
                cdr.getPartyID(),
                event.getToCountryCode(),
                event.getToPartyId(),
                cdr
            );
            CdrIngestResponseDto response = cdrServiceClient.ingestCdr(request);
            if (response == null) {
                LOGGER.warning("EDX CDR ingest returned no response for CDR " + cdr.getId());
                return;
            }
            if (response.rawRecordId() == null || response.rawRecordId().isBlank()) {
                LOGGER.warning("EDX CDR ingest for " + cdr.getId() + " returned no rawRecordId: " + response);
                return;
            }
            mappingStore.recordSuccessfulIngest(
                cdr.getCountryCode(),
                cdr.getPartyID(),
                cdr.getId(),
                response.rawRecordId()
            );
            if (response.success()) {
                LOGGER.info(
                    "EDX CDR ingest mapped "
                        + cdr.getCountryCode() + "/" + cdr.getPartyID() + "/" + cdr.getId()
                        + " -> serviceId=" + response.rawRecordId()
                        + " (extractionStatus=" + response.extractionStatus() + ")"
                );
            } else {
                LOGGER.info(
                    "EDX CDR ingest mapped "
                        + cdr.getCountryCode() + "/" + cdr.getPartyID() + "/" + cdr.getId()
                        + " -> serviceId=" + response.rawRecordId()
                        + " (raw stored; extractionStatus=" + response.extractionStatus() + ", success=false)"
                );
            }
            provisionCpoAsset(cdr.getCountryCode(), cdr.getPartyID());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "EDX CDR ingest failed for CDR " + cdr.getId(), e);
        }
    }

    private void provisionCpoAsset(String countryCode, String partyId) {
        if (assetProvisioningService == null) {
            return;
        }
        if (!assetProvisioningService.ensureForCpo(countryCode, partyId)) {
            LOGGER.warning(
                "EDX dataspace asset provisioning did not complete for CPO "
                    + countryCode + "/" + partyId
            );
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}

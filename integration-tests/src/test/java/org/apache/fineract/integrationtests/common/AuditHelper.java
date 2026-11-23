/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.integrationtests.common;

import static org.apache.fineract.client.feign.util.FeignCalls.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.fineract.client.feign.ObjectMapperFactory;
import org.apache.fineract.client.feign.services.AuditsApi.RetrieveAuditEntriesQueryParams;
import org.apache.fineract.client.models.AuditData;
import org.apache.fineract.client.models.AuditSearchData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Manthan Surkar
 *
 */
public final class AuditHelper {

    private AuditHelper() {

    }

    private static final Logger LOG = LoggerFactory.getLogger(AuditHelper.class);

    // GET /v1/audits can return either a plain array (paged=false) or a
    // {totalFilteredRecords, pageItems} wrapper (paged=true). We always request
    // paged=true and normalize the Object response to pageItems.
    private static final ObjectMapper MAPPER = ObjectMapperFactory.getShared();
    private static final TypeReference<List<AuditData>> AUDIT_DATA_LIST_TYPE = new TypeReference<>() {};

    public static List<AuditData> getAuditDetails(final Integer resourceId, final String actionName, final String entityName) {
        return getAuditDetails(new RetrieveAuditEntriesQueryParams().actionName(actionName).entityName(entityName)
                .resourceId(resourceId == null ? null : resourceId.longValue()));
    }

    public static List<AuditData> getAuditDetailsBySubmittedResourceId(final Long submittedResourceId, final String actionName,
            final String entityName) {
        return getAuditDetails(new RetrieveAuditEntriesQueryParams().actionName(actionName).entityName(entityName)
                .submittedResourceId(submittedResourceId));
    }

    private static List<AuditData> getAuditDetails(final RetrieveAuditEntriesQueryParams queryParams) {
        Object response = ok(() -> FineractFeignClientHelper.getFineractFeignClient().audits()
                .retrieveAuditEntries(queryParams.orderBy("id").sortOrder("DESC").paged(true)));
        return pageItems(response);
    }

    public static List<AuditData> getAuditDetails(final int limit) {
        Object response = ok(() -> FineractFeignClientHelper.getFineractFeignClient().audits()
                .retrieveAuditEntries(new RetrieveAuditEntriesQueryParams().limit(limit).paged(true)));
        return pageItems(response);
    }

    private static List<AuditData> pageItems(final Object response) {
        final JsonNode responseNode = MAPPER.valueToTree(response);
        final JsonNode pageItemsNode = responseNode.isObject() ? responseNode.get("pageItems") : responseNode;
        if (pageItemsNode == null || pageItemsNode.isNull()) {
            throw new IllegalStateException("Unable to find pageItems in audits list response: " + response);
        }
        return MAPPER.convertValue(pageItemsNode, AUDIT_DATA_LIST_TYPE);
    }

    public static AuditSearchData getAuditSearchTemplate() {
        return ok(() -> FineractFeignClientHelper.getFineractFeignClient().audits().retrieveAuditSearchTemplate());
    }

    /**
     * Some audit actions can only be done once Eg: Creation of a client with id 123, hence we verify number of audits
     * For such operations is "equal" to 1 always
     */
    public static void verifyOneAuditOnly(final List<AuditData> auditsToCheck, final Integer id, final String actionName,
            final String entityType) {
        LOG.info("------------------------------CHECK IF AUDIT CREATED------------------------------------\n");
        assertEquals(1, auditsToCheck.size(), "More than one audit created");
        final AuditData auditToCheck = auditsToCheck.get(0);
        final String actual = auditToCheck.getActionName() + " is done on " + auditToCheck.getEntityName() + " with id "
                + auditToCheck.getResourceId();
        final String expected = actionName + " is done on " + entityType + " with id " + id;
        assertEquals(expected, actual, "Error in creating audit!");
    }

    public static void verifyMultipleAuditsOnserver(final List<AuditData> auditsRecievedInitial, final List<AuditData> auditsRecieved,
            final Integer id, final String actionName, final String entityType) {
        LOG.info("------------------------------CHECK IF AUDIT CREATED------------------------------------\n");
        assertEquals(auditsRecievedInitial.size() + 1, auditsRecieved.size(), "Audit is not Created");

        final Comparator<AuditData> compareById = Comparator.comparing(AuditData::getId);
        Collections.sort(auditsRecieved, compareById.reversed());

        // First element is new audit created(Sorted DESC by Id)
        final AuditData auditToCheck = auditsRecieved.get(0);
        final String actual = auditToCheck.getActionName() + " is done on " + auditToCheck.getEntityName() + " with id "
                + auditToCheck.getResourceId();
        final String expected = actionName + " is done on " + entityType + " with id " + id;
        assertEquals(expected, actual, "Error in creating audit!");
    }

    public static void verifyLimitParameterfor(final int limit) {
        assertEquals(limit, getAuditDetails(limit).size(), "Incorrect number of audits received for limit: " + Integer.toString(limit));
    }

    public static void verifyOrderBysupported(final String orderByValue) {
        ok(() -> FineractFeignClientHelper.getFineractFeignClient().audits()
                .retrieveAuditEntries(new RetrieveAuditEntriesQueryParams().orderBy(orderByValue).paged(true)));
    }

}

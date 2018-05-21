package org.opencds.cqf.providers;

import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import ca.uhn.fhir.jpa.provider.dstu3.JpaResourceProviderDstu3;
import ca.uhn.fhir.jpa.rp.dstu3.PatientResourceProvider;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.annotation.*;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.StringParam;
import org.hl7.fhir.dstu3.model.OperationOutcome;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Resource;
import org.opencds.cqf.data.JpaDataProviderStu3;
import org.opencds.cqf.helpers.BulkDataHelper;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

public class BulkDataPatientProvider extends PatientResourceProvider {

    private JpaDataProviderStu3 provider;
    public BulkDataPatientProvider(JpaDataProviderStu3 provider) {
        this.provider = provider;
    }

    @Operation(name = "$export", idempotent = true)
    public OperationOutcome exportAllPatientData(
            javax.servlet.http.HttpServletRequest theServletRequest,
            RequestDetails theRequestDetails,
            @OperationParam(name="_outputFormat") String outputFormat,
            @OperationParam(name="_since") DateParam since,
            @OperationParam(name="_type") StringAndListParam type) throws ServletException, IOException
    {
        BulkDataHelper helper = new BulkDataHelper(provider);

        if (theRequestDetails.getHeader("Accept") == null) {
            return helper.createErrorOutcome("Please provide the Accept header, which must be set to application/fhir+json");
        }
        else if (!theRequestDetails.getHeader("Accept").equals("application/fhir+json")) {
            return helper.createErrorOutcome("Only the application/fhir+json value for the Accept header is currently supported");
        }
        if (theRequestDetails.getHeader("Prefer") == null) {
            return helper.createErrorOutcome("Please provide the Prefer header, which must be set to respond-async");
        }
        else if (!theRequestDetails.getHeader("Prefer").equals("respond-async")) {
            return helper.createErrorOutcome("Only the respond-async value for the Prefer header is currently supported");
        }

        if (outputFormat != null) {
            if (!(outputFormat.equals("application/fhir+ndjson")
                    || outputFormat.equals("application/ndjson")
                    || outputFormat.equals("ndjson")))
            {
                return helper.createErrorOutcome("Only ndjson for the _outputFormat parameter is currently supported");
            }
        }

        SearchParameterMap searchMap = new SearchParameterMap();
        searchMap.setLastUpdated(new DateRangeParam());
        if (since != null) {
            DateRangeParam rangeParam = new DateRangeParam(since.getValue(), new Date());
            searchMap.setLastUpdated(rangeParam);
        }

        List<List<Resource> > resources = new ArrayList<>();
        List<Resource> resolvedResources;
        if (type != null) {
            for (StringOrListParam stringOrListParam : type.getValuesAsQueryTokens()) {
                for (StringParam theType : stringOrListParam.getValuesAsQueryTokens()) {
                    resolvedResources = helper.resolveType(theType.getValue(), searchMap);
                    if (!resolvedResources.isEmpty()) {
                        resources.add(resolvedResources);
                    }
                }
            }
        }
        else {
            for (String theType : helper.compartmentPatient) {
                resolvedResources = helper.resolveType(theType, searchMap);
                if (!resolvedResources.isEmpty()) {
                    resources.add(resolvedResources);
                }
            }
        }

        return helper.createOutcome(resources, theServletRequest, theRequestDetails);
    }
}

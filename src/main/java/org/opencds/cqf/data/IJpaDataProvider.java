package org.opencds.cqf.data;

import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.cql.data.DataProvider;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.runtime.DateTime;
import org.opencds.cqf.cql.runtime.Interval;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
import org.opencds.cqf.cql.terminology.ValueSetInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface IJpaDataProvider extends DataProvider {

    Collection<IResourceProvider> getProviders();
    TerminologyProvider getTerminologyProvider();
    BaseFhirDataProvider setTerminologyProvider(TerminologyProvider terminologyProvider);
    BaseFhirDataProvider setEndpoint(String endpoint);
    IGenericClient getFhirClient();
    BaseJpaResourceProvider resolveResourceProvider(String datatype);


    default Iterable<Object> resolveResourceList(List<IBaseResource> resourceList) {
        List<Object> ret = new ArrayList<>();
        for (IBaseResource res : resourceList) {
            Class clazz = res.getClass();
            ret.add(clazz.cast(res));
        }

        return ret;
    }

    default SearchParameterMap getInitialMap(String templateId, String valueSet, String codePath,
                                             Iterable<Code> codes, String dataType)
    {
        SearchParameterMap map = new SearchParameterMap();
        map.setLastUpdated(new DateRangeParam());

        if (templateId != null && !templateId.equals("")) {
            // do something?
        }

        if (valueSet != null && valueSet.startsWith("urn:oid:")) {
            valueSet = valueSet.replace("urn:oid:", "");
        }

        if (codePath == null && (codes != null || valueSet != null)) {
            throw new IllegalArgumentException("A code path must be provided when filtering on codes or a valueset.");
        }

        if (dataType == null) {
            throw new IllegalArgumentException("A data type (i.e. Procedure, Valueset, etc...) must be specified for clinical data retrieval");
        }

        return map;
    }

    default TokenOrListParam resolveCodeListParam(Iterable<Code> codes) {
        TokenOrListParam codeParams = new TokenOrListParam();
        for (Code code : codes) {
            codeParams.addOr(new TokenParam(code.getSystem(), code.getCode()));
        }
        return codeParams;
    }

    default DateRangeParam resolveDateParam(Interval dateRange) {
        DateParam low = null;
        DateParam high = null;
        if (dateRange.getLow() != null) {
            low = new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, ((DateTime) dateRange.getLow()).getJodaDateTime().toDate());
        }

        if (dateRange.getHigh() != null) {
            high = new DateParam(ParamPrefixEnum.LESSTHAN_OR_EQUALS, ((DateTime) dateRange.getHigh()).getJodaDateTime().toDate());
        }

        DateRangeParam rangeParam;
        if (low == null && high != null) {
            rangeParam = new DateRangeParam(high);
        }
        else if (high == null && low != null) {
            rangeParam = new DateRangeParam(low);
        }
        else {
            rangeParam = new DateRangeParam(low, high);
        }

        return rangeParam;
    }

    default Iterable<Code> resolveCodes(String valueSet, TerminologyProvider terminologyProvider,
                                        boolean expandValueSets, Iterable<Code> codes)
    {
        if (valueSet != null && terminologyProvider != null && expandValueSets) {
            ValueSetInfo valueSetInfo = new ValueSetInfo().withId(valueSet);
            codes = terminologyProvider.expand(valueSetInfo);
        }
        return codes;
    }
}

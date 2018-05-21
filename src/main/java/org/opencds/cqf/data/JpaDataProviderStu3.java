package org.opencds.cqf.data;

import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.jpa.provider.dstu3.JpaResourceProviderDstu3;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderStu3;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.runtime.Interval;
import org.opencds.cqf.terminology.JpaTerminologyProviderStu3;

import java.util.Collection;
import java.util.List;

public class JpaDataProviderStu3 extends FhirDataProviderStu3 implements IJpaDataProvider {

    private Collection<IResourceProvider> providers;

    public JpaDataProviderStu3(Collection<IResourceProvider> providers) {
        super();
        this.providers = providers;
        setTerminologyProvider(new JpaTerminologyProviderStu3(this));
    }

    public Iterable<Object> retrieve(String context, Object contextValue, String dataType, String templateId,
                                     String codePath, Iterable<Code> codes, String valueSet, String datePath,
                                     String dateLowPath, String dateHighPath, Interval dateRange)
    {

        SearchParameterMap map = getInitialMap(templateId, valueSet, codePath, codes, dataType);

        if (context != null && context.equals("Patient") && contextValue != null) {
            ReferenceParam patientParam = new ReferenceParam(contextValue.toString());
            map.add(getPatientSearchParam(dataType), patientParam);
        }

        if (codePath != null && !codePath.equals("")) {
            if (resolveCodes(valueSet, terminologyProvider, expandValueSets, codes) != null) {
                map.add(convertPathToSearchParam(dataType, codePath), resolveCodeListParam(codes));
            }
        }

        if (dateRange != null) {
            map.add(convertPathToSearchParam(dataType, datePath), resolveDateParam(dateRange));
        }

        JpaResourceProviderDstu3 jpaResProvider =
                (JpaResourceProviderDstu3) resolveResourceProvider(dataType);
        IBundleProvider bundleProvider = jpaResProvider.getDao().search(map);
        List<IBaseResource> resourceList = bundleProvider.getResources(0, 10000);
        return resolveResourceList(resourceList);
    }

    @Override
    public Collection<IResourceProvider> getProviders() {
        return providers;
    }

    @Override
    public BaseJpaResourceProvider resolveResourceProvider(String datatype) {
        for (IResourceProvider resource : getProviders()) {
            if (resource.getResourceType().getSimpleName().toLowerCase().equals(datatype.toLowerCase())) {
                return (BaseJpaResourceProvider) resource;
            }
        }
        throw new RuntimeException("Could not find resource provider for type: " + datatype);
    }
}

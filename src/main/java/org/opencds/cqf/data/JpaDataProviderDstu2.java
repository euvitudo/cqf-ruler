package org.opencds.cqf.data;

import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.jpa.provider.JpaResourceProviderDstu2;
import ca.uhn.fhir.jpa.rp.dstu2.ValueSetResourceProvider;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderDstu2;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.runtime.Interval;
import org.opencds.cqf.terminology.JpaTerminologyProviderDstu2;

import java.util.Collection;
import java.util.List;

public class JpaDataProviderDstu2 extends FhirDataProviderDstu2 implements IJpaDataProvider {

    private Collection<IResourceProvider> providers;
    private List<IResourceProvider> stu3Providers;

    public JpaDataProviderDstu2(Collection<IResourceProvider> providers, List<IResourceProvider> stu3Providers) {
        super();
        this.providers = providers;
        this.stu3Providers = stu3Providers;
        setTerminologyProvider(new JpaTerminologyProviderDstu2((ValueSetResourceProvider) resolveResourceProvider("ValueSet")));
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

        JpaResourceProviderDstu2 jpaResProvider =
                (JpaResourceProviderDstu2) resolveResourceProvider(dataType);
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
        for (IResourceProvider resource : stu3Providers) {
            if (resource.getResourceType().getSimpleName().toLowerCase().equals(datatype.toLowerCase())) {
                return (BaseJpaResourceProvider) resource;
            }
        }

        for (IResourceProvider resource : getProviders()) {
            if (resource.getResourceType().getSimpleName().toLowerCase().equals(datatype.toLowerCase())) {
                return (BaseJpaResourceProvider) resource;
            }
        }
        throw new RuntimeException("Could not find resource provider for type: " + datatype);
    }
}

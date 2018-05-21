package org.opencds.cqf.terminology;

import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProvider;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
import org.opencds.cqf.cql.terminology.ValueSetInfo;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public interface IJpaTerminologyProvider extends TerminologyProvider {

    default boolean in(Code code, ValueSetInfo valueSet) throws ResourceNotFoundException {
        for (Code c : expand(valueSet)) {
            if (c == null) continue;
            if (c.getCode().equals(code.getCode()) && c.getSystem().equals(code.getSystem())) {
                return true;
            }
        }
        return false;
    }

    default <T extends IBaseResource> T getValueSet(BaseJpaResourceProvider<T> provider, Class<T> clazz, ValueSetInfo valueSet) {
        if (valueSet == null || valueSet.getId() == null) {
            // TODO - throw error?
            return null;
        }

        try {
            URL url = new URL(valueSet.getId());
            // Get valueset by url
            IBundleProvider bundleProvider = provider.getResourceType().equals(ValueSet.class)
                    ? provider.getDao().search(new SearchParameterMap().add(ValueSet.SP_URL, new UriParam(url.toString())))
                    : provider.getDao().search(new SearchParameterMap().add(ca.uhn.fhir.model.dstu2.resource.ValueSet.SP_URL, new UriParam(url.toString())));
            List<IBaseResource> resources = bundleProvider.getResources(0,1);
            if (resources != null && !resources.isEmpty()) {
                return clazz.cast(resources.get(0));
            }
            // TODO - catch ClassCastException?
        } catch (MalformedURLException mfe) {
            // continue
        }

        return provider.getDao().read(new IdDt(valueSet.getId()));
    }
}

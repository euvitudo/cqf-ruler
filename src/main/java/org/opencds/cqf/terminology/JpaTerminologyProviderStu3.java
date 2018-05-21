package org.opencds.cqf.terminology;

import ca.uhn.fhir.jpa.rp.dstu3.CodeSystemResourceProvider;
import ca.uhn.fhir.jpa.rp.dstu3.ValueSetResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.terminology.CodeSystemInfo;
import org.opencds.cqf.cql.terminology.ValueSetInfo;
import org.opencds.cqf.data.JpaDataProviderStu3;

import java.util.ArrayList;
import java.util.List;

public class JpaTerminologyProviderStu3 implements IJpaTerminologyProvider {
    private ValueSetResourceProvider valueSetProvider;
    private CodeSystemResourceProvider codeSystemProvider;

    public JpaTerminologyProviderStu3(ValueSetResourceProvider valueSetProvider, CodeSystemResourceProvider codeSystemProvider) {
        this.valueSetProvider = valueSetProvider;
        this.codeSystemProvider = codeSystemProvider;
    }

    public JpaTerminologyProviderStu3(JpaDataProviderStu3 provider) {
        valueSetProvider = (ValueSetResourceProvider) provider.resolveResourceProvider("ValueSet");
        codeSystemProvider = (CodeSystemResourceProvider) provider.resolveResourceProvider("CodeSystem");
    }

    @Override
    public Iterable<Code> expand(ValueSetInfo valueSet) throws ResourceNotFoundException {
        ValueSet vs = getValueSet(valueSetProvider, ValueSet.class, valueSet);

        List<Code> codes = new ArrayList<>();
        if (vs != null) {
            for (ValueSet.ValueSetExpansionContainsComponent expansion : vs.getExpansion().getContains()) {
                codes.add(new Code().withCode(expansion.getCode()).withSystem(expansion.getSystem()));
            }

            if (codes.isEmpty()) {
                for (ValueSet.ConceptSetComponent include : vs.getCompose().getInclude()) {
                    String system = include.getSystem();
                    for (ValueSet.ConceptReferenceComponent component : include.getConcept()) {
                        codes.add(new Code().withCode(component.getCode()).withSystem(system));
                    }
                }
            }
        }

        return codes;
    }

    @Override
    public Code lookup(Code code, CodeSystemInfo codeSystem) throws ResourceNotFoundException {
        CodeSystem cs = codeSystemProvider.getDao().read(new IdType(codeSystem.getId()));
        for (CodeSystem.ConceptDefinitionComponent concept : cs.getConcept()) {
            if (concept.getCode().equals(code.getCode()))
                return code.withSystem(codeSystem.getId()).withDisplay(concept.getDisplay());
        }
        return code;
    }
}

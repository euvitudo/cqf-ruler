package org.opencds.cqf.terminology;

import ca.uhn.fhir.jpa.rp.dstu2.ValueSetResourceProvider;
import ca.uhn.fhir.model.dstu2.resource.ValueSet;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.terminology.CodeSystemInfo;
import org.opencds.cqf.cql.terminology.ValueSetInfo;

import java.util.ArrayList;
import java.util.List;

public class JpaTerminologyProviderDstu2 implements IJpaTerminologyProvider {

    private ValueSetResourceProvider valueSetProvider;

    public JpaTerminologyProviderDstu2(ValueSetResourceProvider valueSetProvider) {
        this.valueSetProvider = valueSetProvider;
    }

    @Override
    public Iterable<Code> expand(ValueSetInfo valueSet) {
        ValueSet vs = getValueSet(valueSetProvider, ValueSet.class, valueSet);

        List<Code> codes = new ArrayList<>();
        if (vs != null) {
            for (ValueSet.ExpansionContains expansion : vs.getExpansion().getContains()) {
                codes.add(new Code().withCode(expansion.getCode()).withSystem(expansion.getSystem()));
            }

            if (codes.isEmpty()) {
                for (ValueSet.ComposeInclude include : vs.getCompose().getInclude()) {
                    String system = include.getSystem();
                    for (ValueSet.ComposeIncludeConcept component : include.getConcept()) {
                        codes.add(new Code().withCode(component.getCode()).withSystem(system));
                    }
                }
            }
        }

        return codes;
    }

    @Override
    public Code lookup(Code code, CodeSystemInfo codeSystemInfo) {
        return null;
    }
}

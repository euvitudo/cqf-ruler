package org.opencds.cqf.providers;

import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import ca.uhn.fhir.jpa.provider.dstu3.JpaResourceProviderDstu3;
import ca.uhn.fhir.jpa.rp.dstu3.LibraryResourceProvider;
import ca.uhn.fhir.jpa.rp.dstu3.MeasureResourceProvider;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.IncludeDef;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.builders.MeasureReportBuilder;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.runtime.DateTime;
import org.opencds.cqf.cql.runtime.Interval;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;
import org.opencds.cqf.data.JpaDataProviderStu3;
import org.opencds.cqf.helpers.DateHelper;
import org.opencds.cqf.helpers.FhirMeasureBundler;
import org.opencds.cqf.management.ServerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class FHIRMeasureResourceProvider extends MeasureResourceProvider {

    private ServerManager manager;

    private Interval measurementPeriod;

    private static final Logger logger = LoggerFactory.getLogger(FHIRMeasureResourceProvider.class);

    public FHIRMeasureResourceProvider(ServerManager manager) {
        this.manager = manager;
    }

    /*
    *
    * NOTE that the source, user, and pass parameters are not standard parameters for the FHIR $evaluate-measure operation
    *
    * */
    @Operation(name = "$evaluate-measure", idempotent = true)
    public MeasureReport evaluateMeasure(
            @IdParam IdType theId,
            @RequiredParam(name="periodStart") String periodStart,
            @RequiredParam(name="periodEnd") String periodEnd,
            @OptionalParam(name="measure") String measureRef,
            @OptionalParam(name="reportType") String reportType,
            @OptionalParam(name="patient") String patientRef,
            @OptionalParam(name="practitioner") String practitionerRef,
            @OptionalParam(name="lastReceivedOn") String lastReceivedOn,
            @OptionalParam(name="source") String source,
            @OptionalParam(name="user") String user,
            @OptionalParam(name="pass") String pass) throws InternalErrorException, FHIRException
    {
        // fetch the measure
        Measure measure = this.getDao().read(measureRef == null ? theId : new IdType(measureRef));
        if (measure == null) {
            throw new IllegalArgumentException("Could not find Measure/" + theId);
        }

        logger.info("Evaluating Measure/" + measure.getIdElement().getIdPart());

        // load libraries
        // TODO - move into LibraryLoader
        for (Reference ref : measure.getLibrary()) {
            // if library is contained in measure, load it into server
            if (ref.getReferenceElement().getIdPart().startsWith("#")) {
                for (Resource resource : measure.getContained()) {
                    if (resource instanceof org.hl7.fhir.dstu3.model.Library
                            && resource.getIdElement().getIdPart().equals(ref.getReferenceElement().getIdPart().substring(1))) {
                        LibraryResourceProvider libraryResourceProvider = (LibraryResourceProvider) manager.getDataProvider().resolveResourceProvider("Library");
                        libraryResourceProvider.getDao().update((org.hl7.fhir.dstu3.model.Library) resource);
                    }
                }
            }
            manager.getLibraryLoader().load(
                    new VersionedIdentifier()
                            .withVersion(ref.getReferenceElement().getVersionIdPart())
                            .withId(ref.getReferenceElement().getIdPart())
            );
        }

        // resolve primary library
        Library library = resolvePrimaryLibrary(measure);

        logger.info("Resolved primary library as Library/" + library.getLocalId());

        // resolve execution context
        Context context = new Context(library);
        context.registerLibraryLoader(manager.getLibraryLoader());

        // resolve remote term svc if provided
        if (source != null) {
            logger.info("Remote terminology service provided");
            FhirTerminologyProvider terminologyProvider = user == null || pass == null
                    ? new FhirTerminologyProvider().setEndpoint(source, true)
                    : new FhirTerminologyProvider().withBasicAuth(user, pass).setEndpoint(source, true);
            manager.getDataProvider().setTerminologyProvider(terminologyProvider);
        }

        context.registerDataProvider("http://hl7.org/fhir", manager.getDataProvider());

        // resolve the measurement period
        measurementPeriod =
                new Interval(
                        DateHelper.resolveRequestDate(periodStart, true), true,
                        DateHelper.resolveRequestDate(periodEnd, false), true
                );

        logger.info("Measurement period defined as [" + measurementPeriod.getStart().toString() + ", " + measurementPeriod.getEnd().toString() + "]");

        context.setParameter(
                null, "Measurement Period",
                new Interval(
                        DateTime.fromJavaDate((Date) measurementPeriod.getStart()), true,
                        DateTime.fromJavaDate((Date) measurementPeriod.getEnd()), true
                )
        );

        // resolve report type
        if (reportType != null) {
            switch (reportType) {
                case "patient": return evaluatePatientMeasure(measure, context, patientRef);
                case "patient-list": return  evaluatePatientListMeasure(measure, context, practitionerRef);
                case "population": return evaluatePopulationMeasure(measure, context);
                default: throw new IllegalArgumentException("Invalid report type: " + reportType);
            }
        }

        // default report type is patient
        return evaluatePatientMeasure(measure, context, patientRef);
    }

    private Library resolvePrimaryLibrary(Measure measure) {
        // default is the first library reference
        Library library = manager.getLibraryLoader().getLibraries().get(measure.getLibraryFirstRep().getReferenceElement().getIdPart());

        // gather all the population criteria expressions
        List<String> criteriaExpressions = new ArrayList<>();
        for (Measure.MeasureGroupComponent grouping : measure.getGroup()) {
            for (Measure.MeasureGroupPopulationComponent population : grouping.getPopulation()) {
                criteriaExpressions.add(population.getCriteria());
            }
        }

        // check each library to see if it includes the expression namespace - return if true
        for (Library candidate : manager.getLibraryLoader().getLibraries().values()) {
            for (String expression : criteriaExpressions) {
                String namespace = expression.split("\\.")[0];
                if (!namespace.equals(expression)) {
                    for (IncludeDef include : candidate.getIncludes().getDef()) {
                        if (include.getLocalIdentifier().equals(namespace)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return library;
    }

    private MeasureReport evaluatePatientMeasure(Measure measure, Context context, String patientId) {
        logger.info("Generating individual report");

        if (patientId == null) {
            return evaluatePopulationMeasure(measure, context);
        }
        Patient patient = (Patient) manager.getDataProvider().resolveResourceProvider("Patient").getDao().read(new IdType(patientId));
        return evaluate(measure, context, patient == null ? Collections.emptyList() : Collections.singletonList(patient), MeasureReport.MeasureReportType.INDIVIDUAL);
    }

    private MeasureReport evaluatePatientListMeasure(Measure measure, Context context, String practitionerRef)
    {
        logger.info("Generating patient-list report");

        List<Patient> patients = new ArrayList<>();
        if (practitionerRef != null) {
            SearchParameterMap map = new SearchParameterMap();
            map.add(
                    "general-practitioner",
                    new ReferenceParam(
                            practitionerRef.startsWith("Practitioner/")
                                    ? practitionerRef
                                    : "Practitioner/" + practitionerRef
                    )
            );
            IBundleProvider patientProvider = manager.getDataProvider().resolveResourceProvider("Patient").getDao().search(map);
            List<IBaseResource> patientList = patientProvider.getResources(0, patientProvider.size());
            patientList.forEach(x -> patients.add((Patient) x));
        }
        return evaluate(measure, context, patients, MeasureReport.MeasureReportType.PATIENTLIST);
    }

    private MeasureReport evaluatePopulationMeasure(Measure measure, Context context) {
        logger.info("Generating summary report");

        List<Patient> patients = new ArrayList<>();
        IBundleProvider patientProvider = manager.getDataProvider().resolveResourceProvider("Patient").getDao().search(new SearchParameterMap());
        List<IBaseResource> patientList = patientProvider.getResources(0, patientProvider.size());
        patientList.forEach(x -> patients.add((Patient) x));
        return evaluate(measure, context, patients, MeasureReport.MeasureReportType.SUMMARY);
    }

    private MeasureReport evaluate(Measure measure, Context context, List<Patient> patients, MeasureReport.MeasureReportType type)
    {
        MeasureReportBuilder reportBuilder = new MeasureReportBuilder();
        reportBuilder.buildStatus("complete");
        reportBuilder.buildType(type);
        reportBuilder.buildMeasureReference(measure.getIdElement().getValue());
        if (type == MeasureReport.MeasureReportType.INDIVIDUAL && !patients.isEmpty()) {
            reportBuilder.buildPatientReference(patients.get(0).getIdElement().getValue());
        }
        reportBuilder.buildPeriod(measurementPeriod);

        MeasureReport report = reportBuilder.build();

        List<Patient> initialPopulation = getInitalPopulation(measure, patients, context);
        HashMap<String,Resource> resources = new HashMap<>();

        for (Measure.MeasureGroupComponent group : measure.getGroup()) {
            MeasureReport.MeasureReportGroupComponent reportGroup = new MeasureReport.MeasureReportGroupComponent();
            reportGroup.setIdentifier(group.getIdentifier());
            report.getGroup().add(reportGroup);

            for (Measure.MeasureGroupPopulationComponent pop : group.getPopulation()) {
                int count = 0;
                // Worried about performance here with big populations...
                for (Patient patient : initialPopulation) {
                    context.setContextValue("Patient", patient.getIdElement().getIdPart());
                    Object result = context.resolveExpressionRef(pop.getCriteria()).evaluate(context);
                    if (result instanceof Boolean) {
                        count += (Boolean) result ? 1 : 0;
                    }
                    else if (result instanceof Iterable) {
                        for (Object item : (Iterable) result) {
                            count++;
                            if (item instanceof Resource) {
                                resources.put(((Resource) item).getId(), (Resource) item);
                            }
                        }
                    }
                }
                MeasureReport.MeasureReportGroupPopulationComponent populationReport = new MeasureReport.MeasureReportGroupPopulationComponent();
                populationReport.setCount(count);
                populationReport.setCode(pop.getCode());
                populationReport.setIdentifier(pop.getIdentifier());
                reportGroup.getPopulation().add(populationReport);
            }
        }

        FhirMeasureBundler bundler = new FhirMeasureBundler();
        org.hl7.fhir.dstu3.model.Bundle evaluatedResources = bundler.bundle(resources.values());
        evaluatedResources.setId(UUID.randomUUID().toString());
        report.setEvaluatedResources(new Reference('#' + evaluatedResources.getId()));
        report.addContained(evaluatedResources);
        return report;
    }

    private List<Patient> getInitalPopulation(Measure measure, List<Patient> population, Context context) {
        List<Patient> initalPop = new ArrayList<>();
        for (Measure.MeasureGroupComponent group : measure.getGroup()) {
            for (Measure.MeasureGroupPopulationComponent pop : group.getPopulation()) {
                if (pop.getCode().getCodingFirstRep().getCode().equals("initial-population")) {
                    for (Patient patient : population) {
                        context.setContextValue("Patient", patient.getIdElement().getIdPart());
                        Object result = context.resolveExpressionRef(pop.getCriteria()).evaluate(context);
                        if (result == null) {
                            continue;
                        }
                        if ((Boolean) result) {
                            initalPop.add(patient);
                        }
                    }
                }
            }
        }
        return initalPop;
    }

    // TODO - this needs a lot of work
//    @Operation(name = "$data-requirements", idempotent = true)
//    public org.hl7.fhir.dstu3.model.Library dataRequirements(
//            @IdParam IdType theId,
//            @RequiredParam(name="startPeriod") String startPeriod,
//            @RequiredParam(name="endPeriod") String endPeriod)
//            throws InternalErrorException, FHIRException
//    {
//        Measure measure = this.getDao().read(theId);
//
//        // NOTE: This assumes there is only one library and it is the primary library for the measure.
//        org.hl7.fhir.dstu3.model.Library libraryResource =
//                (org.hl7.fhir.dstu3.model.Library) provider.resolveResourceProvider("Library")
//                        .getDao()
//                        .read(new IdType(measure.getLibraryFirstRep().getReference()));
//
//        List<RelatedArtifact> dependencies = new ArrayList<>();
//        for (RelatedArtifact dependency : libraryResource.getRelatedArtifact()) {
//            if (dependency.getType().toCode().equals("depends-on")) {
//                dependencies.add(dependency);
//            }
//        }
//
//        List<Coding> typeCoding = new ArrayList<>();
//        typeCoding.add(new Coding().setCode("module-definition"));
//        org.hl7.fhir.dstu3.model.Library library =
//                new org.hl7.fhir.dstu3.model.Library().setType(new CodeableConcept().setCoding(typeCoding));
//
//        if (!dependencies.isEmpty()) {
//            library.setRelatedArtifact(dependencies);
//        }
//
//        return library
//                .setDataRequirement(libraryResource.getDataRequirement())
//                .setParameter(libraryResource.getParameter());
//    }
}

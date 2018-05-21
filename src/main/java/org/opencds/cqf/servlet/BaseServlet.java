package org.opencds.cqf.servlet;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.config.WebsocketDispatcherConfig;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.provider.JpaConformanceProviderDstu2;
import ca.uhn.fhir.jpa.provider.JpaSystemProviderDstu2;
import ca.uhn.fhir.jpa.provider.dstu3.JpaConformanceProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.JpaSystemProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.TerminologyUploaderProviderDstu3;
import ca.uhn.fhir.jpa.rp.dstu3.*;
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.ETagSupportEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.*;
import org.apache.commons.lang3.StringUtils;
import org.opencds.cqf.config.FhirServerConfigDstu2;
import org.opencds.cqf.config.FhirServerConfigDstu3;
import org.opencds.cqf.management.ServerManager;
import org.opencds.cqf.data.JpaDataProviderDstu2;
import org.opencds.cqf.data.JpaDataProviderStu3;
import org.opencds.cqf.providers.*;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BaseServlet extends RestfulServer {

    private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(BaseServlet.class);
    private AnnotationConfigWebApplicationContext myAppCtx;
    private static List<IResourceProvider> stu3Beans;

    ServerManager manager;

    @SuppressWarnings("unchecked")
    @Override
    protected void initialize() throws ServletException {
        super.initialize();

        WebApplicationContext parentAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();

        String implDesc = getInitParameter("ImplementationDescription");
        String fhirVersionParam = getInitParameter("FhirVersion");
        if (StringUtils.isBlank(fhirVersionParam)) {
            fhirVersionParam = "DSTU3";
        }
        fhirVersionParam = fhirVersionParam.trim().toUpperCase();

        List<IResourceProvider> beans;
        @SuppressWarnings("rawtypes")
        IFhirSystemDao systemDao;
        ETagSupportEnum etagSupport;
        String baseUrlProperty;
        List<Object> plainProviders = new ArrayList<>();

        switch (fhirVersionParam) {
            case "DSTU2": {
                myAppCtx = new AnnotationConfigWebApplicationContext();
                myAppCtx.setServletConfig(getServletConfig());
                myAppCtx.setParent(parentAppCtx);
                myAppCtx.register(FhirServerConfigDstu2.class, WebsocketDispatcherConfig.class);
//                baseUrlProperty = FHIR_BASEURL_DSTU2;
                myAppCtx.refresh();
                setFhirContext(FhirContext.forDstu2());
                beans = myAppCtx.getBean("myResourceProvidersDstu2", List.class);
                plainProviders.add(myAppCtx.getBean("mySystemProviderDstu2", JpaSystemProviderDstu2.class));
                systemDao = myAppCtx.getBean("mySystemDaoDstu2", IFhirSystemDao.class);
                etagSupport = ETagSupportEnum.ENABLED;
                JpaConformanceProviderDstu2 confProvider = new JpaConformanceProviderDstu2(this, systemDao, myAppCtx.getBean(DaoConfig.class));
                confProvider.setImplementationDescription(implDesc);
                setServerConformanceProvider(confProvider);
                manager = new ServerManager(new JpaDataProviderDstu2(beans, stu3Beans));
                break;
            }
            case "DSTU3": {
                myAppCtx = new AnnotationConfigWebApplicationContext();
                myAppCtx.setServletConfig(getServletConfig());
                myAppCtx.setParent(parentAppCtx);
                myAppCtx.register(FhirServerConfigDstu3.class, WebsocketDispatcherConfig.class);
//                baseUrlProperty = FHIR_BASEURL_DSTU3;
                myAppCtx.refresh();
                setFhirContext(FhirContext.forDstu3());
                beans = myAppCtx.getBean("myResourceProvidersDstu3", List.class);
                plainProviders.add(myAppCtx.getBean("mySystemProviderDstu3", JpaSystemProviderDstu3.class));
                systemDao = myAppCtx.getBean("mySystemDaoDstu3", IFhirSystemDao.class);
                etagSupport = ETagSupportEnum.ENABLED;
                JpaConformanceProviderDstu3 confProvider = new JpaConformanceProviderDstu3(this, systemDao, myAppCtx.getBean(DaoConfig.class));
                confProvider.setImplementationDescription(implDesc);
                setServerConformanceProvider(confProvider);
                plainProviders.add(myAppCtx.getBean(TerminologyUploaderProviderDstu3.class));
                manager = new ServerManager(new JpaDataProviderStu3(beans));
                resolveResourceProviders();
                break;
            }
//            case "R4": {
//                myAppCtx = new AnnotationConfigWebApplicationContext();
//                myAppCtx.setServletConfig(getServletConfig());
//                myAppCtx.setParent(parentAppCtx);
//                myAppCtx.register(TestR4Config.class, WebsocketDispatcherConfig.class);
//                baseUrlProperty = FHIR_BASEURL_R4;
//                myAppCtx.refresh();
//                setFhirContext(FhirContext.forR4());
//                beans = myAppCtx.getBean("myResourceProvidersR4", List.class);
//                plainProviders.add(myAppCtx.getBean("mySystemProviderR4", JpaSystemProviderR4.class));
//                systemDao = myAppCtx.getBean("mySystemDaoR4", IFhirSystemDao.class);
//                etagSupport = ETagSupportEnum.ENABLED;
//                JpaConformanceProviderR4 confProvider = new JpaConformanceProviderR4(this, systemDao, myAppCtx.getBean(DaoConfig.class));
//                confProvider.setImplementationDescription(implDesc);
//                setServerConformanceProvider(confProvider);
//                plainProviders.add(myAppCtx.getBean(TerminologyUploaderProviderR4.class));
//                break;
//            }
            default:
                throw new ServletException("Unknown FHIR version specified in init-param[FhirVersion]: " + fhirVersionParam);
        }

        if (fhirVersionParam.equals("DSTU3") && stu3Beans == null) {
            stu3Beans = new ArrayList<>();
            stu3Beans.add(manager.getDataProvider().resolveResourceProvider("Library"));
            stu3Beans.add(manager.getDataProvider().resolveResourceProvider("PlanDefinition"));
            stu3Beans.add(manager.getDataProvider().resolveResourceProvider("ActivityDefinition"));
            stu3Beans.add(manager.getDataProvider().resolveResourceProvider("Measure"));
        }

        setETagSupport(etagSupport);

        FhirContext ctx = getFhirContext();
        ctx.setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());

        for (IResourceProvider nextResourceProvider : beans) {
            ourLog.info(" * Have resource provider for: {}", nextResourceProvider.getResourceType().getSimpleName());
        }
        setResourceProviders(beans);

        setPlainProviders(plainProviders);

        CorsInterceptor corsInterceptor = new CorsInterceptor();
        registerInterceptor(corsInterceptor);

        ResponseHighlighterInterceptor responseHighlighterInterceptor = new ResponseHighlighterInterceptor();
        responseHighlighterInterceptor.setShowRequestHeaders(false);
        responseHighlighterInterceptor.setShowResponseHeaders(true);
        registerInterceptor(responseHighlighterInterceptor);

        registerInterceptor(new BanUnsupportedHttpMethodsInterceptor());

        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(EncodingEnum.JSON);

        setPagingProvider(myAppCtx.getBean(DatabaseBackedPagingProvider.class));

        Collection<IServerInterceptor> interceptorBeans = myAppCtx.getBeansOfType(IServerInterceptor.class).values();
        for (IServerInterceptor interceptor : interceptorBeans) {
            this.registerInterceptor(interceptor);
        }

    }

    @Override
    public void destroy() {
        super.destroy();
        ourLog.info("Server is shutting down");
        myAppCtx.close();
    }

    private void resolveResourceProviders() throws ServletException {

        FHIRLibraryResourceProvider libraryProvider = new FHIRLibraryResourceProvider(manager);
        LibraryResourceProvider jpaLibraryProvider = (LibraryResourceProvider) manager.getDataProvider().resolveResourceProvider("Library");
        libraryProvider.setDao(jpaLibraryProvider.getDao());
        libraryProvider.setContext(jpaLibraryProvider.getContext());

        try {
            unregister(jpaLibraryProvider, manager.getDataProvider().getProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(libraryProvider, manager.getDataProvider().getProviders());

        // Bundle processing
        FHIRBundleResourceProvider bundleProvider = new FHIRBundleResourceProvider(manager.getDataProvider());
        BundleResourceProvider jpaBundleProvider = (BundleResourceProvider) manager.getDataProvider().resolveResourceProvider("Bundle");
        bundleProvider.setDao(jpaBundleProvider.getDao());
        bundleProvider.setContext(jpaBundleProvider.getContext());

        try {
            unregister(jpaBundleProvider, manager.getDataProvider().getProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(bundleProvider, manager.getDataProvider().getProviders());

        // Measure processing
        FHIRMeasureResourceProvider measureProvider = new FHIRMeasureResourceProvider(manager);
        MeasureResourceProvider jpaMeasureProvider = (MeasureResourceProvider) manager.getDataProvider().resolveResourceProvider("Measure");
        measureProvider.setDao(jpaMeasureProvider.getDao());
        measureProvider.setContext(jpaMeasureProvider.getContext());

        try {
            unregister(jpaMeasureProvider, manager.getDataProvider().getProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(measureProvider, manager.getDataProvider().getProviders());

        // ActivityDefinition processing
        FHIRActivityDefinitionResourceProvider actDefProvider = new FHIRActivityDefinitionResourceProvider(manager);
        ActivityDefinitionResourceProvider jpaActDefProvider = (ActivityDefinitionResourceProvider) manager.getDataProvider().resolveResourceProvider("ActivityDefinition");
        actDefProvider.setDao(jpaActDefProvider.getDao());
        actDefProvider.setContext(jpaActDefProvider.getContext());

        try {
            unregister(jpaActDefProvider, manager.getDataProvider().getProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(actDefProvider, manager.getDataProvider().getProviders());

        // PlanDefinition processing
        FHIRPlanDefinitionResourceProvider planDefProvider = new FHIRPlanDefinitionResourceProvider(manager);
        PlanDefinitionResourceProvider jpaPlanDefProvider = (PlanDefinitionResourceProvider) manager.getDataProvider().resolveResourceProvider("PlanDefinition");
        planDefProvider.setDao(jpaPlanDefProvider.getDao());
        planDefProvider.setContext(jpaPlanDefProvider.getContext());

        try {
            unregister(jpaPlanDefProvider, manager.getDataProvider().getProviders());
        } catch (Exception e) {
            throw new ServletException("Unable to unregister provider: " + e.getMessage());
        }

        register(planDefProvider, manager.getDataProvider().getProviders());

        // StructureMap processing
//        FHIRStructureMapResourceProvider structureMapProvider = new FHIRStructureMapResourceProvider(manager.getDataProvider());
//        StructureMapResourceProvider jpaStructMapProvider = (StructureMapResourceProvider) manager.getDataProvider().resolveResourceProvider("StructureMap");
//        structureMapProvider.setDao(jpaStructMapProvider.getDao());
//        structureMapProvider.setContext(jpaStructMapProvider.getContext());
//
//        try {
//            unregister(jpaStructMapProvider, manager.getDataProvider().getProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(structureMapProvider, manager.getDataProvider().getProviders());

        // Patient processing - for bulk data export
//        BulkDataPatientProvider bulkDataPatientProvider = new BulkDataPatientProvider(manager.getDataProvider());
//        PatientResourceProvider jpaPatientProvider = (PatientResourceProvider) manager.getDataProvider().resolveResourceProvider("Patient");
//        bulkDataPatientProvider.setDao(jpaPatientProvider.getDao());
//        bulkDataPatientProvider.setContext(jpaPatientProvider.getContext());
//
//        try {
//            unregister(jpaPatientProvider, manager.getDataProvider().getProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(bulkDataPatientProvider, manager.getDataProvider().getProviders());
//
//        // Group processing - for bulk data export
//        BulkDataGroupProvider bulkDataGroupProvider = new BulkDataGroupProvider(manager.getDataProvider());
//        GroupResourceProvider jpaGroupProvider = (GroupResourceProvider) manager.getDataProvider().resolveResourceProvider("Group");
//        bulkDataGroupProvider.setDao(jpaGroupProvider.getDao());
//        bulkDataGroupProvider.setContext(jpaGroupProvider.getContext());
//
//        try {
//            unregister(jpaGroupProvider, manager.getDataProvider().getProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(bulkDataGroupProvider, manager.getDataProvider().getProviders());
    }

    private void register(IResourceProvider provider, Collection<IResourceProvider> providers) {
        providers.add(provider);
    }

    private void unregister(IResourceProvider provider, Collection<IResourceProvider> providers) {
        providers.remove(provider);
    }

    public IResourceProvider getProvider(String name) {

        for (IResourceProvider res : getResourceProviders()) {
            if (res.getResourceType().getSimpleName().equals(name)) {
                return res;
            }
        }

        throw new IllegalArgumentException("This should never happen!");
    }
}

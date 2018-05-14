package org.opencds.cqf.servlet;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.jpa.config.WebsocketDispatcherConfig;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.provider.JpaConformanceProviderDstu2;
import ca.uhn.fhir.jpa.provider.JpaSystemProviderDstu2;
import ca.uhn.fhir.jpa.provider.dstu3.JpaConformanceProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.JpaResourceProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.JpaSystemProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.TerminologyUploaderProviderDstu3;
import ca.uhn.fhir.jpa.provider.r4.JpaSystemProviderR4;
import ca.uhn.fhir.jpa.rp.dstu3.*;
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.ETagSupportEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.*;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.opencds.cqf.config.FhirServerConfigDstu2;
import org.opencds.cqf.config.FhirServerConfigDstu3;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
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

    @SuppressWarnings("unchecked")
    @Override
    protected void initialize() throws ServletException {
        super.initialize();

        // Get the spring context from the web container (it's declared in web.xml)
        WebApplicationContext parentAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();

        // These two parmeters are also declared in web.xml
        String implDesc = getInitParameter("ImplementationDescription");
        String fhirVersionParam = getInitParameter("FhirVersion");
        if (StringUtils.isBlank(fhirVersionParam)) {
            fhirVersionParam = "DSTU3";
        }

        // Depending on the version this server is supporing, we will
        // retrieve all the appropriate resource providers and the
        // conformance provider
        List<IResourceProvider> beans;
        @SuppressWarnings("rawtypes")
        IFhirSystemDao systemDao;
        ETagSupportEnum etagSupport;
        String baseUrlProperty;
        List<Object> plainProviders = new ArrayList<>();

        switch (fhirVersionParam.trim().toUpperCase()) {
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

		/*
		 * On the DSTU2 endpoint, we want to enable ETag support
		 */
        setETagSupport(etagSupport);

		/*
		 * This server tries to dynamically generate narratives
		 */
        FhirContext ctx = getFhirContext();
        ctx.setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());

		/*
		 * The resource and system providers (which actually implement the various FHIR
		 * operations in this server) are all retrieved from the spring context above
		 * and are provided to the server here.
		 */
        for (IResourceProvider nextResourceProvider : beans) {
            ourLog.info(" * Have resource provider for: {}", nextResourceProvider.getResourceType().getSimpleName());
        }
        setResourceProviders(beans);

        setPlainProviders(plainProviders);

		/*
		 * Enable CORS
		 */
        CorsInterceptor corsInterceptor = new CorsInterceptor();
        registerInterceptor(corsInterceptor);

		/*
		 * We want to format the response using nice HTML if it's a browser, since this
		 * makes things a little easier for testers.
		 */
        ResponseHighlighterInterceptor responseHighlighterInterceptor = new ResponseHighlighterInterceptor();
        responseHighlighterInterceptor.setShowRequestHeaders(false);
        responseHighlighterInterceptor.setShowResponseHeaders(true);
        registerInterceptor(responseHighlighterInterceptor);

        registerInterceptor(new BanUnsupportedHttpMethodsInterceptor());

		/*
		 * Default to JSON with pretty printing
		 */
        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(EncodingEnum.JSON);

		/*
		 * The server's base URL (e.g. http://fhirtest.uhn.ca/baseDstu2) is
		 * pulled from a system property, which is helpful if you want to try
		 * hosting your own copy of this server.
		 */
//        String baseUrl = System.getProperty(baseUrlProperty);
//        if (StringUtils.isBlank(baseUrl)) {
//            // Try to fall back in case the property isn't set
//            baseUrl = System.getProperty("fhir.baseurl");
//            if (StringUtils.isBlank(baseUrl)) {
//                throw new ServletException("Missing system property: " + baseUrlProperty);
//            }
//        }
//        setServerAddressStrategy(new MyHardcodedServerAddressStrategy(baseUrl));

		/*
		 * Spool results to the database
		 */
        setPagingProvider(myAppCtx.getBean(DatabaseBackedPagingProvider.class));

		/*
		 * Load interceptors for the server from Spring
		 */
        Collection<IServerInterceptor> interceptorBeans = myAppCtx.getBeansOfType(IServerInterceptor.class).values();
        for (IServerInterceptor interceptor : interceptorBeans) {
            this.registerInterceptor(interceptor);
        }

    }

    @Override
    public void destroy() {
        super.destroy();
        ourLog.info("Server is shutting down");
        myAppCtx.destroy();
    }

//    @SuppressWarnings("unchecked")
//    @Override
//    protected void initialize() throws ServletException {
//
//        super.initialize();
//
//        FhirVersionEnum fhirVersion = FhirVersionEnum.DSTU3;
//        setFhirContext(new FhirContext(fhirVersion));
//
//        // Get the spring context from the web container (it's declared in web.xml)
//        WebApplicationContext myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();
//
//        String resourceProviderBeanName = "myResourceProvidersDstu3";
//        List<IResourceProvider> beans = myAppCtx.getBean(resourceProviderBeanName, List.class);
//        setResourceProviders(beans);
//
//        Object systemProvider = myAppCtx.getBean("mySystemProviderDstu3", JpaSystemProviderDstu3.class);
//        setPlainProviders(systemProvider);
//
//        IFhirSystemDao<Bundle, Meta> systemDao = myAppCtx.getBean("mySystemDaoDstu3", IFhirSystemDao.class);
//        JpaConformanceProviderDstu3 confProvider = new JpaConformanceProviderDstu3(this, systemDao,
//                myAppCtx.getBean(DaoConfig.class));
//        confProvider.setImplementationDescription("Measure and Opioid Processing Server");
//        setServerConformanceProvider(confProvider);
//
//        setDefaultPrettyPrint(true);
//        setDefaultResponseEncoding(EncodingEnum.JSON);
//        setPagingProvider(myAppCtx.getBean(DatabaseBackedPagingProvider.class));
//
//        /*
//		 * Enable CORS
//		 */
////        CorsConfiguration config = new CorsConfiguration();
////        CorsInterceptor corsInterceptor = new CorsInterceptor(config);
////        config.addAllowedHeader("Origin");
////        config.addAllowedHeader("Accept");
////        config.addAllowedHeader("X-Requested-With");
////        config.addAllowedHeader("Content-Type");
////        config.addAllowedHeader("Access-Control-Request-Method");
////        config.addAllowedHeader("Access-Control-Request-Headers");
////        config.addAllowedOrigin("*");
////        config.addExposedHeader("Location");
////        config.addExposedHeader("Content-Location");
////        config.setAllowedMethods(Arrays.asList("GET","POST","PUT","DELETE","OPTIONS"));
////        registerInterceptor(corsInterceptor);
//
//        /*
//		 * Load interceptors for the server from Spring (these are defined in FhirServerConfig.java)
//		 */
//        Collection<IServerInterceptor> interceptorBeans = myAppCtx.getBeansOfType(IServerInterceptor.class).values();
//        for (IServerInterceptor interceptor : interceptorBeans) {
//            this.registerInterceptor(interceptor);
//        }
//
//        JpaDataProvider provider = new JpaDataProvider(getResourceProviders());
//        JpaResourceProviderDstu3<ValueSet> vs = (ValueSetResourceProvider)   provider.resolveResourceProvider("ValueSet");
//        JpaResourceProviderDstu3<CodeSystem> cs = (CodeSystemResourceProvider) provider.resolveResourceProvider("CodeSystem");
//        TerminologyProvider terminologyProvider = new JpaTerminologyProvider(vs, cs);
//        provider.setTerminologyProvider(terminologyProvider);
//
//        resolveResourceProviders(provider);
//
//        // Register the logging interceptor
//        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
//        this.registerInterceptor(loggingInterceptor);
//
//        // The SLF4j logger "test.accesslog" will receive the logging events
//        loggingInterceptor.setLoggerName("logging.accesslog");
//
//        // This is the format for each line. A number of substitution variables may
//        // be used here. See the JavaDoc for LoggingInterceptor for information on
//        // what is available.
//        loggingInterceptor.setMessageFormat("Source[${remoteAddr}] Operation[${operationType} ${idOrResourceName}] UA[${requestHeader.user-agent}] Params[${requestParameters}]");
//
//        //setServerAddressStrategy(new HardcodedServerAddressStrategy("http://mydomain.com/fhir/baseDstu2"));
//        //registerProvider(myAppCtx.getBean(TerminologyUploaderProviderDstu3.class));
//    }
//
//    private void resolveResourceProviders(JpaDataProvider provider) throws ServletException {
//        // Bundle processing
//        FHIRBundleResourceProvider bundleProvider = new FHIRBundleResourceProvider(provider);
//        BundleResourceProvider jpaBundleProvider = (BundleResourceProvider) provider.resolveResourceProvider("Bundle");
//        bundleProvider.setDao(jpaBundleProvider.getDao());
//        bundleProvider.setContext(jpaBundleProvider.getContext());
//
//        try {
//            unregister(jpaBundleProvider, provider.getCollectionProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(bundleProvider, provider.getCollectionProviders());
//
//        // Measure processing
//        FHIRMeasureResourceProvider measureProvider = new FHIRMeasureResourceProvider(provider);
//        MeasureResourceProvider jpaMeasureProvider = (MeasureResourceProvider) provider.resolveResourceProvider("Measure");
//        measureProvider.setDao(jpaMeasureProvider.getDao());
//        measureProvider.setContext(jpaMeasureProvider.getContext());
//
//        try {
//            unregister(jpaMeasureProvider, provider.getCollectionProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(measureProvider, provider.getCollectionProviders());
//
//        // ActivityDefinition processing
//        FHIRActivityDefinitionResourceProvider actDefProvider = new FHIRActivityDefinitionResourceProvider(provider);
//        ActivityDefinitionResourceProvider jpaActDefProvider = (ActivityDefinitionResourceProvider) provider.resolveResourceProvider("ActivityDefinition");
//        actDefProvider.setDao(jpaActDefProvider.getDao());
//        actDefProvider.setContext(jpaActDefProvider.getContext());
//
//        try {
//            unregister(jpaActDefProvider, provider.getCollectionProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(actDefProvider, provider.getCollectionProviders());
//
//        // PlanDefinition processing
//        FHIRPlanDefinitionResourceProvider planDefProvider = new FHIRPlanDefinitionResourceProvider(provider);
//        PlanDefinitionResourceProvider jpaPlanDefProvider = (PlanDefinitionResourceProvider) provider.resolveResourceProvider("PlanDefinition");
//        planDefProvider.setDao(jpaPlanDefProvider.getDao());
//        planDefProvider.setContext(jpaPlanDefProvider.getContext());
//
//        try {
//            unregister(jpaPlanDefProvider, provider.getCollectionProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(planDefProvider, provider.getCollectionProviders());
//
//        // StructureMap processing
//        FHIRStructureMapResourceProvider structureMapProvider = new FHIRStructureMapResourceProvider(provider);
//        StructureMapResourceProvider jpaStructMapProvider = (StructureMapResourceProvider) provider.resolveResourceProvider("StructureMap");
//        structureMapProvider.setDao(jpaStructMapProvider.getDao());
//        structureMapProvider.setContext(jpaStructMapProvider.getContext());
//
//        try {
//            unregister(jpaStructMapProvider, provider.getCollectionProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(structureMapProvider, provider.getCollectionProviders());
//
//        // Patient processing - for bulk data export
//        BulkDataPatientProvider bulkDataPatientProvider = new BulkDataPatientProvider(provider);
//        PatientResourceProvider jpaPatientProvider = (PatientResourceProvider) provider.resolveResourceProvider("Patient");
//        bulkDataPatientProvider.setDao(jpaPatientProvider.getDao());
//        bulkDataPatientProvider.setContext(jpaPatientProvider.getContext());
//
//        try {
//            unregister(jpaPatientProvider, provider.getCollectionProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(bulkDataPatientProvider, provider.getCollectionProviders());
//
//        // Group processing - for bulk data export
//        BulkDataGroupProvider bulkDataGroupProvider = new BulkDataGroupProvider(provider);
//        GroupResourceProvider jpaGroupProvider = (GroupResourceProvider) provider.resolveResourceProvider("Group");
//        bulkDataGroupProvider.setDao(jpaGroupProvider.getDao());
//        bulkDataGroupProvider.setContext(jpaGroupProvider.getContext());
//
//        try {
//            unregister(jpaGroupProvider, provider.getCollectionProviders());
//        } catch (Exception e) {
//            throw new ServletException("Unable to unregister provider: " + e.getMessage());
//        }
//
//        register(bulkDataGroupProvider, provider.getCollectionProviders());
//    }
//
//    private void register(IResourceProvider provider, Collection<IResourceProvider> providers) {
//        providers.add(provider);
//    }
//
//    private void unregister(IResourceProvider provider, Collection<IResourceProvider> providers) {
//        providers.remove(provider);
//    }

    public IResourceProvider getProvider(String name) {

        for (IResourceProvider res : getResourceProviders()) {
            if (res.getResourceType().getSimpleName().equals(name)) {
                return res;
            }
        }

        throw new IllegalArgumentException("This should never happen!");
    }
}

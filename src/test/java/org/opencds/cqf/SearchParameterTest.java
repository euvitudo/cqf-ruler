package org.opencds.cqf;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import ca.uhn.fhir.jpa.provider.dstu3.JpaResourceProviderDstu3;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencds.cqf.cql.runtime.Code;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SearchParameterTest {

    private static IGenericClient ourClient;
    private static FhirContext ourCtx = FhirContext.forDstu3();
    private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SearchParameterTest.class);

    private static int ourPort;

    private static Server ourServer;
    private static String ourServerBase;

    private static IIdType patientId;
    private static IIdType observationId;

    @Test
    public void CodeListSearchParamTest() throws IOException {
        ourLog.info("Base URL is: http://localhost:" + ourPort + "/cqf-ruler/baseDstu3");

        // This here is a test to display an issue with the marriage between Hapi and the Ruler. =(
        // The issue is with requests with more than 5 elements in a search param list like the following:
        // {BASE}/Condition?code=http%3A%2F%2Fsnomed.info%2Fsct%7C109838007,http%3A%2F%2Fsnomed.info%2Fsct%7C1701000119104,http%3A%2F%2Fsnomed.info%2Fsct%7C187757001,http%3A%2F%2Fsnomed.info%2Fsct%7C187758006,http%3A%2F%2Fsnomed.info%2Fsct%7C447395005&patient=Patient-12214
        Patient patient = new Patient();
        patient.setActive(true);
        patientId = ourClient.create().resource(patient).execute().getId();

        Observation observation = new Observation().setStatus(Observation.ObservationStatus.FINAL);
        CodeableConcept concept = new CodeableConcept();
        Coding coding = new Coding().setSystem("http://snomed.info/sct").setCode("187758006");
        observation.setCode(concept.addCoding(coding));
        observationId = ourClient.create().resource(observation).execute().getId();

        // Prepare search params
//        SearchParameterMap map = new SearchParameterMap();
//        TokenOrListParam codeParams = new TokenOrListParam();
//        TokenParam tokenParam = new TokenParam().setSystem("http://snomed.info/sct");
//        codeParams.add(tokenParam.setValue("109838007"));
//        codeParams.add(tokenParam.setValue("1701000119104"));
//        codeParams.add(tokenParam.setValue("187757001"));
//        codeParams.add(tokenParam.setValue("187758006"));
//        codeParams.add(tokenParam.setValue("208150008"));
//
//        map.add("code",  codeParams);

        // Search with 5 params in code list
        Bundle bundle =
                ourClient
                .search()
                .byUrl(ourServerBase + "/Condition?code=http%3A%2F%2Fsnomed.info%2Fsct%7C109838007,http%3A%2F%2Fsnomed.info%2Fsct%7C1701000119104,http%3A%2F%2Fsnomed.info%2Fsct%7C187757001,http%3A%2F%2Fsnomed.info%2Fsct%7C187758006,http%3A%2F%2Fsnomed.info%2Fsct%7C208150008&patient=Patient-12214")
                .returnBundle(Bundle.class)
                .execute();

        Assert.assertTrue(bundle.getEntry().size() == 1);

        // Search with 6 params in code list
        // This is where the error will occur
        try {
            bundle =
                    ourClient
                            .search()
                            .byUrl(ourServerBase + "/Condition?code=http%3A%2F%2Fsnomed.info%2Fsct%7C109838007,http%3A%2F%2Fsnomed.info%2Fsct%7C1701000119104,http%3A%2F%2Fsnomed.info%2Fsct%7C187757001,http%3A%2F%2Fsnomed.info%2Fsct%7C187758006,http%3A%2F%2Fsnomed.info%2Fsct%7C187758009,http%3A%2F%2Fsnomed.info%2Fsct%7C208150008&patient=Patient-12214")
                            .returnBundle(Bundle.class)
                            .execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterClass
    public static void afterClass() throws Exception {
        ourClient.delete().resourceById(patientId);
        ourClient.delete().resourceById(observationId);
        ourServer.stop();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {

        String path = Paths.get("").toAbsolutePath().toString();

        ourLog.info("Project base path is: {}", path);

        ourPort = RandomServerPortProvider.findFreePort();
        ourServer = new Server(ourPort);

        WebAppContext webAppContext = new WebAppContext();
        webAppContext.setContextPath("/cqf-ruler");
        webAppContext.setDescriptor(path + "/src/main/webapp/WEB-INF/web.xml");
        webAppContext.setResourceBase(path + "/target/cqf-ruler");
        webAppContext.setParentLoaderPriority(true);

        ourServer.setHandler(webAppContext);
        ourServer.start();

        ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        ourCtx.getRestfulClientFactory().setSocketTimeout(1200 * 1000);
        ourServerBase = "http://localhost:" + ourPort + "/cqf-ruler/baseDstu3";
        ourClient = ourCtx.newRestfulGenericClient(ourServerBase);
        ourClient.registerInterceptor(new LoggingInterceptor(true));

    }
}

class RandomServerPortProvider {

    private static List<Integer> ourPorts = new ArrayList<Integer>();

    public static int findFreePort() {
        ServerSocket server;
        try {
            server = new ServerSocket(0);
            int port = server.getLocalPort();
            ourPorts.add(port);
            server.close();
            Thread.sleep(500);
            return port;
        } catch (IOException | InterruptedException e) {
            throw new Error(e);
        }
    }

    public static List<Integer> list() {
        return ourPorts;
    }

}

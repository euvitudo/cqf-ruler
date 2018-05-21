package org.opencds.cqf.providers;

import ca.uhn.fhir.jpa.rp.dstu3.LibraryResourceProvider;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Library;
import org.opencds.cqf.management.ServerManager;

import javax.servlet.http.HttpServletRequest;

public class FHIRLibraryResourceProvider extends LibraryResourceProvider {

    private ServerManager manager;

    public FHIRLibraryResourceProvider(ServerManager manager) {
        this.manager = manager;
    }

    @Override
    @Create
    public MethodOutcome create(
            HttpServletRequest theRequest,
            @ResourceParam Library theResource,
            @ConditionalUrlParam String theConditional,
            RequestDetails theRequestDetails)
    {
        updateLibraryCache(theResource);
        return super.create(theRequest, theResource, theConditional, theRequestDetails);
    }

    @Override
    @Update
    public MethodOutcome update(
            HttpServletRequest theRequest,
            @ResourceParam Library theResource,
            @IdParam IdType theId,
            @ConditionalUrlParam String theConditional,
            RequestDetails theRequestDetails)
    {
        updateLibraryCache(theResource);
        return super.update(theRequest, theResource, theId, theConditional, theRequestDetails);
    }

    private void updateLibraryCache(Library library) {
        manager.getLibraryLoader()
                .putLibrary(
                        library.getIdElement().getIdPart(),
                        manager.getLibraryLoader().toElmLibrary(library)
                );
    }
}

package org.opencds.cqf.management;

import ca.uhn.fhir.model.primitive.IdDt;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.dstu3.model.Attachment;
import org.opencds.cqf.cql.execution.LibraryLoader;
import org.opencds.cqf.helpers.LibraryHelper;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class JpaLibraryLoader implements LibraryLoader {

    // TODO - maybe need 2 caches for STU3 and DSTU2 libraries?
    private Map<String, Library> libraries;
    private ServerManager manager;

    public JpaLibraryLoader(ServerManager manager) {
        libraries = new HashMap<>();
        this.manager = manager;
    }

    public Map<String, Library> getLibraries() {
        return libraries;
    }
    public void putLibrary(String id, Library library) {
        libraries.put(id, library);
    }

    private Library resolveLibrary(VersionedIdentifier libraryIdentifier) {
        if (libraryIdentifier == null) {
            throw new IllegalArgumentException("Library identifier is null.");
        }

        if (libraryIdentifier.getId() == null) {
            throw new IllegalArgumentException("Library identifier id is null.");
        }

        Library library = libraries.get(libraryIdentifier.getId());
        if (library != null && libraryIdentifier.getVersion() != null
                && !libraryIdentifier.getVersion().equals(library.getIdentifier().getVersion())) {
            throw new IllegalArgumentException(String.format("Could not load library %s, version %s because version %s is already loaded.",
                    libraryIdentifier.getId(), libraryIdentifier.getVersion(), library.getIdentifier().getVersion()));
        }
        else if (library == null) {
            library = fetchLibrary(libraryIdentifier);
            libraries.put(libraryIdentifier.getId(), library);
        }

        return library;
    }

    private Library fetchLibrary(VersionedIdentifier libraryIdentifier) {
        org.hl7.fhir.dstu3.model.Library library =
                (org.hl7.fhir.dstu3.model.Library) manager.getDataProvider()
                        .resolveResourceProvider("Library")
                        .getDao().read(new IdDt(libraryIdentifier.getId()));

        Library elmLibrary = toElmLibrary(library);
        if (elmLibrary != null) {
            return elmLibrary;
        }

        org.hl7.elm.r1.VersionedIdentifier identifier = new org.hl7.elm.r1.VersionedIdentifier()
                .withId(libraryIdentifier.getId())
                .withSystem(libraryIdentifier.getSystem())
                .withVersion(libraryIdentifier.getVersion());

        ArrayList<CqlTranslatorException> errors = new ArrayList<>();
        org.hl7.elm.r1.Library translatedLibrary = manager.getLibraryManager().resolveLibrary(identifier, errors).getLibrary();

        if (errors.size() > 0) {
            throw new IllegalArgumentException(LibraryHelper.errorsToString(errors));
        }
        try {
            return LibraryHelper.readLibrary(
                    new ByteArrayInputStream(
                            LibraryHelper.getTranslator("", manager.getLibraryManager(), manager.getModelManager())
                                    .convertToXml(translatedLibrary).getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (JAXBException e) {
            throw new IllegalArgumentException(String.format("Errors occurred translating library %s%s.",
                    identifier.getId(), identifier.getVersion() != null ? ("-" + identifier.getVersion()) : ""));
        }
    }

    public Library toElmLibrary(org.hl7.fhir.dstu3.model.Library library) {
        InputStream is;
        for (Attachment content : library.getContent()) {
            if (content.hasData()) {
                is = new ByteArrayInputStream(content.getData());
                if (content.getContentType().equals("application/elm+xml")) {
                    return LibraryHelper.readLibrary(is);
                } else if (content.getContentType().equals("text/cql")) {
                    return LibraryHelper.translateLibrary(is, manager.getLibraryManager(), manager.getModelManager());
                }
            }
        }
        return null;
    }

    @Override
    public Library load(VersionedIdentifier versionedIdentifier) {
        return resolveLibrary(versionedIdentifier);
    }
}

package org.opencds.cqf.management;

import ca.uhn.fhir.jpa.rp.dstu3.LibraryResourceProvider;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.opencds.cqf.data.IJpaDataProvider;

public class ServerManager {

    private LibraryManager libraryManager;
    private ModelManager modelManager;
    private JpaLibraryLoader libraryLoader;
    private JpaLibrarySourceProvider sourceProvider;
    private IJpaDataProvider dataProvider;

    public ServerManager(IJpaDataProvider dataProvider) {
        modelManager = new ModelManager();
        libraryManager = new LibraryManager(modelManager);
        this.dataProvider = dataProvider;

        sourceProvider = new JpaLibrarySourceProvider((LibraryResourceProvider) dataProvider.resolveResourceProvider("Library"));
        libraryManager.getLibrarySourceLoader().clearProviders();
        libraryManager.getLibrarySourceLoader().registerProvider(sourceProvider);
        libraryLoader = new JpaLibraryLoader(this);
    }

    public LibraryManager getLibraryManager() {
        return libraryManager;
    }

    public void setLibraryManager(LibraryManager libraryManager) {
        this.libraryManager = libraryManager;
    }

    public ModelManager getModelManager() {
        return modelManager;
    }

    public void setModelManager(ModelManager modelManager) {
        this.modelManager = modelManager;
    }

    public JpaLibraryLoader getLibraryLoader() {
        return libraryLoader;
    }

    public void setLibraryLoader(JpaLibraryLoader libraryLoader) {
        this.libraryLoader = libraryLoader;
    }

    public JpaLibrarySourceProvider getSourceProvider() {
        return sourceProvider;
    }

    public void setSourceProvider(JpaLibrarySourceProvider sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    public IJpaDataProvider getDataProvider() {
        return dataProvider;
    }

    public void setDataProvider(IJpaDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }
}

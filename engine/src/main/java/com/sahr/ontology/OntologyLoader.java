package com.sahr.ontology;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StreamDocumentSource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

public final class OntologyLoader {
    private OntologyLoader() {
    }

    public static OWLOntology loadFromClasspath(List<String> resourcePaths) {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        manager.getIRIMappers().add(ignoreHttpImports());
        OWLOntology merged = createMerged(manager);
        ClassLoader loader = OntologyLoader.class.getClassLoader();

        for (String resourcePath : resourcePaths) {
            try (InputStream rawStream = loader.getResourceAsStream(resourcePath)) {
                if (rawStream == null) {
                    throw new IllegalArgumentException("Missing classpath resource: " + resourcePath);
                }
                try (InputStream stream = wrapIfGz(resourcePath, rawStream)) {
                    OWLOntology loaded = manager.loadOntologyFromOntologyDocument(new StreamDocumentSource(stream), config);
                    manager.addAxioms(merged, loaded.axioms());
                }
            } catch (IOException | OWLOntologyCreationException e) {
                throw new IllegalStateException("Failed to load ontology resource: " + resourcePath, e);
            }
        }

        return merged;
    }

    public static OWLOntology loadFromFiles(List<Path> paths) {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        manager.getIRIMappers().add(ignoreHttpImports());
        OWLOntology merged = createMerged(manager);

        for (Path path : paths) {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Missing ontology file: " + path);
            }
            try (InputStream rawStream = Files.newInputStream(path);
                 InputStream stream = wrapIfGz(path.toString(), rawStream)) {
                OWLOntology loaded = manager.loadOntologyFromOntologyDocument(new StreamDocumentSource(stream), config);
                manager.addAxioms(merged, loaded.axioms());
            } catch (IOException | OWLOntologyCreationException e) {
                throw new IllegalStateException("Failed to load ontology file: " + path, e);
            }
        }

        return merged;
    }

    private static OWLOntology createMerged(OWLOntologyManager manager) {
        try {
            return manager.createOntology(IRI.generateDocumentIRI());
        } catch (OWLOntologyCreationException e) {
            throw new IllegalStateException("Failed to create merged ontology", e);
        }
    }

    private static InputStream wrapIfGz(String name, InputStream stream) throws IOException {
        if (name.endsWith(".gz")) {
            return new GZIPInputStream(stream);
        }
        return stream;
    }

    private static OWLOntologyIRIMapper ignoreHttpImports() {
        return ontologyIri -> {
            String iri = ontologyIri == null ? "" : ontologyIri.toString();
            if (iri.startsWith("http://") || iri.startsWith("https://")) {
                return IRI.create("file:/dev/null");
            }
            return null;
        };
    }
}

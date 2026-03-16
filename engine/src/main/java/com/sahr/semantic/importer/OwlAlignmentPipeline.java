package com.sahr.semantic.importer;

import com.sahr.config.EngineConfig;
import com.sahr.ontology.OntologyLoader;
import com.sahr.semantic.alignment.AlignmentInput;
import com.sahr.semantic.alignment.AlignmentOutput;
import com.sahr.semantic.alignment.OntologyBackedSemanticAlignmentCompiler;
import com.sahr.semantic.alignment.SemanticAlignmentCompiler;
import org.semanticweb.owlapi.model.OWLOntology;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OwlAlignmentPipeline {
    private final OwlSemanticImporter importer;
    private final SemanticAlignmentCompiler compiler;

    public OwlAlignmentPipeline(OwlSemanticImporter importer, SemanticAlignmentCompiler compiler) {
        this.importer = Objects.requireNonNull(importer, "importer");
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    public static OwlAlignmentPipeline defaultPipeline() {
        return new OwlAlignmentPipeline(
                new OwlSemanticImporter(),
                OntologyBackedSemanticAlignmentCompiler.loadDefault()
        );
    }

    public OwlAlignmentResult run(OWLOntology ontology, String sourceName) {
        OwlImportResult imported = importer.importOntology(ontology, sourceName);
        AlignmentInput input = imported.input();
        AlignmentOutput aligned = compiler.compile(input);
        return new OwlAlignmentResult(aligned, imported.report());
    }

    public OwlAlignmentResult runFromClasspath(List<String> resources, String sourceName) {
        OWLOntology ontology = OntologyLoader.loadFromClasspath(resources);
        return run(ontology, sourceName);
    }

    public OwlAlignmentResult runFromConfig(EngineConfig config, String sourceName) {
        Objects.requireNonNull(config, "config");
        List<String> resources = new ArrayList<>();
        for (String id : config.ontologyIds()) {
            List<String> pack = config.ontologyResources().get(id);
            if (pack != null) {
                resources.addAll(pack);
            }
        }
        return runFromClasspath(resources, sourceName);
    }

    public OwlAlignmentResult runFromFiles(List<Path> paths, String sourceName) {
        OWLOntology ontology = OntologyLoader.loadFromFiles(paths);
        return run(ontology, sourceName);
    }
}

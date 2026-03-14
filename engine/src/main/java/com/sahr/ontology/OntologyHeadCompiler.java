package com.sahr.ontology;

import com.sahr.heads.OntologyHeadDefinition;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLInverseObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLTransitiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLSymmetricObjectPropertyAxiom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class OntologyHeadCompiler {
    private static final String NS = "https://sahr.ai/ontology/reasoning#";

    private static final String REASONING_HEAD = NS + "ReasoningHead";
    private static final String PATTERN = NS + "Pattern";
    private static final String TRIPLE_PATTERN = NS + "TriplePattern";
    private static final String VARIABLE = NS + "Variable";
    private static final String CONSTANT = NS + "Constant";
    private static final String ADD_ASSERTION = NS + "AddAssertion";
    private static final String EXECUTOR_PARAM = NS + "ExecutorParam";

    private static final String HAS_PATTERN = NS + "hasPattern";
    private static final String HAS_TRIPLE = NS + "hasTriple";
    private static final String HAS_ACTION = NS + "hasAction";
    private static final String HAS_SCORE_POLICY = NS + "hasScorePolicy";
    private static final String HAS_EXECUTOR_PARAM = NS + "hasExecutorParam";
    private static final String SUBJECT = NS + "subject";
    private static final String PREDICATE = NS + "predicate";
    private static final String OBJECT = NS + "object";
    private static final String NAME = NS + "name";
    private static final String VAR_NAME = NS + "varName";
    private static final String VALUE = NS + "value";
    private static final String BASE_WEIGHT = NS + "baseWeight";
    private static final String EXECUTOR_TYPE = NS + "executorType";
    private static final String PARAM_KEY = NS + "paramKey";
    private static final String PARAM_VALUE = NS + "paramValue";
    private static final String ENABLED = NS + "enabled";
    private static final double META_TRANSITIVE_WEIGHT = 0.75;
    private static final double META_SYMMETRIC_WEIGHT = 0.65;
    private static final double META_INVERSE_WEIGHT = 0.7;

    private OntologyHeadCompiler() {
    }

    public static List<OntologyHeadDefinition> compile(OWLOntology ontology) {
        if (ontology == null) {
            return List.of();
        }
        OWLDataFactory factory = OWLManager.createOWLOntologyManager().getOWLDataFactory();
        OWLClass headClass = factory.getOWLClass(IRI.create(REASONING_HEAD));
        OWLClass patternClass = factory.getOWLClass(IRI.create(PATTERN));
        OWLClass tripleClass = factory.getOWLClass(IRI.create(TRIPLE_PATTERN));
        OWLClass variableClass = factory.getOWLClass(IRI.create(VARIABLE));
        OWLClass constantClass = factory.getOWLClass(IRI.create(CONSTANT));
        OWLClass addAssertionClass = factory.getOWLClass(IRI.create(ADD_ASSERTION));
        OWLClass executorParamClass = factory.getOWLClass(IRI.create(EXECUTOR_PARAM));

        OWLObjectProperty hasPattern = factory.getOWLObjectProperty(IRI.create(HAS_PATTERN));
        OWLObjectProperty hasTriple = factory.getOWLObjectProperty(IRI.create(HAS_TRIPLE));
        OWLObjectProperty hasAction = factory.getOWLObjectProperty(IRI.create(HAS_ACTION));
        OWLObjectProperty hasScorePolicy = factory.getOWLObjectProperty(IRI.create(HAS_SCORE_POLICY));
        OWLObjectProperty hasExecutorParam = factory.getOWLObjectProperty(IRI.create(HAS_EXECUTOR_PARAM));
        OWLObjectProperty subjectProp = factory.getOWLObjectProperty(IRI.create(SUBJECT));
        OWLObjectProperty predicateProp = factory.getOWLObjectProperty(IRI.create(PREDICATE));
        OWLObjectProperty objectProp = factory.getOWLObjectProperty(IRI.create(OBJECT));

        OWLDataProperty nameProp = factory.getOWLDataProperty(IRI.create(NAME));
        OWLDataProperty varNameProp = factory.getOWLDataProperty(IRI.create(VAR_NAME));
        OWLDataProperty valueProp = factory.getOWLDataProperty(IRI.create(VALUE));
        OWLDataProperty baseWeightProp = factory.getOWLDataProperty(IRI.create(BASE_WEIGHT));
        OWLDataProperty executorTypeProp = factory.getOWLDataProperty(IRI.create(EXECUTOR_TYPE));
        OWLDataProperty paramKeyProp = factory.getOWLDataProperty(IRI.create(PARAM_KEY));
        OWLDataProperty paramValueProp = factory.getOWLDataProperty(IRI.create(PARAM_VALUE));
        OWLDataProperty enabledProp = factory.getOWLDataProperty(IRI.create(ENABLED));

        List<OntologyHeadDefinition> definitions = new ArrayList<>();
        Set<String> signature = new HashSet<>();
        Set<OWLNamedIndividual> heads = individualsOfClass(ontology, headClass);
        for (OWLNamedIndividual head : heads) {
            String name = dataPropertyValue(ontology, head, nameProp)
                    .orElse(head.getIRI().getShortForm());
            boolean enabled = dataPropertyValue(ontology, head, enabledProp)
                    .map(OntologyHeadCompiler::parseBoolean)
                    .orElse(true);
            if (!enabled) {
                continue;
            }
            String executorType = dataPropertyValue(ontology, head, executorTypeProp)
                    .orElse(OntologyHeadDefinition.EXECUTOR_PATTERN_MATCH)
                    .toUpperCase(Locale.ROOT);
            java.util.Map<String, String> executorParams = loadExecutorParams(
                    ontology,
                    head,
                    hasExecutorParam,
                    executorParamClass,
                    paramKeyProp,
                    paramValueProp
            );
            List<OntologyHeadDefinition.TriplePattern> patterns = new ArrayList<>();
            for (OWLNamedIndividual pattern : objectPropertyValues(ontology, head, hasPattern)) {
                if (!isInstanceOf(ontology, pattern, patternClass)) {
                    continue;
                }
                for (OWLNamedIndividual triple : objectPropertyValues(ontology, pattern, hasTriple)) {
                    if (!isInstanceOf(ontology, triple, tripleClass)) {
                        continue;
                    }
                    Optional<OntologyHeadDefinition.TriplePattern> parsed = parseTriple(
                            ontology,
                            triple,
                            subjectProp,
                            predicateProp,
                            objectProp,
                            variableClass,
                            constantClass,
                            varNameProp,
                            valueProp
                    );
                    parsed.ifPresent(patterns::add);
                }
            }
            boolean isPatternHead = OntologyHeadDefinition.EXECUTOR_PATTERN_MATCH.equals(executorType);
            if (patterns.isEmpty() && isPatternHead) {
                continue;
            }

            OWLNamedIndividual actionNode = objectPropertyValues(ontology, head, hasAction)
                    .stream()
                    .filter(action -> isInstanceOf(ontology, action, addAssertionClass))
                    .findFirst()
                    .orElse(null);
            Optional<OntologyHeadDefinition.TriplePattern> action = Optional.empty();
            if (actionNode != null) {
                action = parseTriple(
                        ontology,
                        actionNode,
                        subjectProp,
                        predicateProp,
                        objectProp,
                        variableClass,
                        constantClass,
                        varNameProp,
                        valueProp
                );
            }
            if (isPatternHead && action.isEmpty()) {
                continue;
            }

            double baseWeight = objectPropertyValues(ontology, head, hasScorePolicy).stream()
                    .map(policy -> dataPropertyValue(ontology, policy, baseWeightProp))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(OntologyHeadCompiler::parseDouble)
                    .findFirst()
                    .orElse(0.7);

            OntologyHeadDefinition definition = new OntologyHeadDefinition(
                    name,
                    patterns,
                    action.orElse(null),
                    baseWeight,
                    executorType,
                    executorParams
            );
            if (signature.add(signature(definition))) {
                definitions.add(definition);
            }
        }

        addMetaHeads(ontology, definitions, signature);
        return definitions;
    }

    private static Optional<OntologyHeadDefinition.TriplePattern> parseTriple(OWLOntology ontology,
                                                                              OWLNamedIndividual node,
                                                                              OWLObjectProperty subjectProp,
                                                                              OWLObjectProperty predicateProp,
                                                                              OWLObjectProperty objectProp,
                                                                              OWLClass variableClass,
                                                                              OWLClass constantClass,
                                                                              OWLDataProperty varNameProp,
                                                                              OWLDataProperty valueProp) {
        Optional<OntologyHeadDefinition.Term> subject = parseTerm(ontology, node, subjectProp, variableClass, constantClass, varNameProp, valueProp);
        Optional<OntologyHeadDefinition.Term> predicate = parseTerm(ontology, node, predicateProp, variableClass, constantClass, varNameProp, valueProp);
        Optional<OntologyHeadDefinition.Term> object = parseTerm(ontology, node, objectProp, variableClass, constantClass, varNameProp, valueProp);
        if (subject.isEmpty() || predicate.isEmpty() || object.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OntologyHeadDefinition.TriplePattern(subject.get(), predicate.get(), object.get()));
    }

    private static Optional<OntologyHeadDefinition.Term> parseTerm(OWLOntology ontology,
                                                                   OWLNamedIndividual node,
                                                                   OWLObjectProperty property,
                                                                   OWLClass variableClass,
                                                                   OWLClass constantClass,
                                                                   OWLDataProperty varNameProp,
                                                                   OWLDataProperty valueProp) {
        OWLNamedIndividual value = objectPropertyValues(ontology, node, property)
                .stream()
                .findFirst()
                .orElse(null);
        if (value == null) {
            return Optional.empty();
        }
        if (isInstanceOf(ontology, value, variableClass)) {
            String name = dataPropertyValue(ontology, value, varNameProp)
                    .orElseGet(() -> value.getIRI().getShortForm());
            return Optional.of(OntologyHeadDefinition.variable(name));
        }
        if (isInstanceOf(ontology, value, constantClass)) {
            String constant = dataPropertyValue(ontology, value, valueProp)
                    .orElseGet(() -> value.getIRI().toString());
            return Optional.of(OntologyHeadDefinition.constant(constant));
        }
        return Optional.of(OntologyHeadDefinition.constant(value.getIRI().toString()));
    }

    private static boolean isInstanceOf(OWLOntology ontology, OWLNamedIndividual individual, OWLClass cls) {
        return ontology.classAssertionAxioms(individual)
                .anyMatch(ax -> ax.getClassesInSignature().contains(cls));
    }

    private static Set<OWLNamedIndividual> individualsOfClass(OWLOntology ontology, OWLClass cls) {
        return ontology.classAssertionAxioms(cls)
                .map(ax -> ax.getIndividual())
                .filter(individual -> !individual.isAnonymous())
                .map(individual -> individual.asOWLNamedIndividual())
                .collect(Collectors.toSet());
    }

    private static Set<OWLNamedIndividual> objectPropertyValues(OWLOntology ontology,
                                                                OWLNamedIndividual subject,
                                                                OWLObjectProperty property) {
        return ontology.objectPropertyAssertionAxioms(subject)
                .filter(ax -> ax.getProperty().asOWLObjectProperty().equals(property))
                .map(ax -> ax.getObject())
                .filter(obj -> !obj.isAnonymous())
                .map(obj -> obj.asOWLNamedIndividual())
                .collect(Collectors.toSet());
    }

    private static Optional<String> dataPropertyValue(OWLOntology ontology,
                                                      OWLNamedIndividual subject,
                                                      OWLDataProperty property) {
        return ontology.dataPropertyAssertionAxioms(subject)
                .filter(ax -> ax.getProperty().asOWLDataProperty().equals(property))
                .map(ax -> ax.getObject())
                .map(literal -> literal.getLiteral())
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private static double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0.7;
        }
    }

    private static boolean parseBoolean(String raw) {
        if (raw == null) {
            return false;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    private static void addMetaHeads(OWLOntology ontology,
                                     List<OntologyHeadDefinition> definitions,
                                     Set<String> signature) {
        List<OntologyHeadDefinition> meta = new ArrayList<>();
        meta.addAll(buildTransitiveHeads(ontology));
        meta.addAll(buildSymmetricHeads(ontology));
        meta.addAll(buildInverseHeads(ontology));
        for (OntologyHeadDefinition definition : meta) {
            if (signature.add(signature(definition))) {
                definitions.add(definition);
            }
        }
    }

    private static List<OntologyHeadDefinition> buildTransitiveHeads(OWLOntology ontology) {
        List<OntologyHeadDefinition> definitions = new ArrayList<>();
        for (OWLTransitiveObjectPropertyAxiom axiom : ontology.getAxioms(org.semanticweb.owlapi.model.AxiomType.TRANSITIVE_OBJECT_PROPERTY)) {
            OWLObjectPropertyExpression property = axiom.getProperty();
            if (property.isAnonymous()) {
                continue;
            }
            String predicate = property.asOWLObjectProperty().getIRI().toString();
            OntologyHeadDefinition definition = new OntologyHeadDefinition(
                    "meta-transitive-" + shortForm(predicate),
                    List.of(
                            triple(OntologyHeadDefinition.variable("a"), OntologyHeadDefinition.constant(predicate), OntologyHeadDefinition.variable("b")),
                            triple(OntologyHeadDefinition.variable("b"), OntologyHeadDefinition.constant(predicate), OntologyHeadDefinition.variable("c"))
                    ),
                    triple(OntologyHeadDefinition.variable("a"), OntologyHeadDefinition.constant(predicate), OntologyHeadDefinition.variable("c")),
                    META_TRANSITIVE_WEIGHT
            );
            definitions.add(definition);
        }
        return definitions;
    }

    private static List<OntologyHeadDefinition> buildSymmetricHeads(OWLOntology ontology) {
        List<OntologyHeadDefinition> definitions = new ArrayList<>();
        for (OWLSymmetricObjectPropertyAxiom axiom : ontology.getAxioms(org.semanticweb.owlapi.model.AxiomType.SYMMETRIC_OBJECT_PROPERTY)) {
            OWLObjectPropertyExpression property = axiom.getProperty();
            if (property.isAnonymous()) {
                continue;
            }
            String predicate = property.asOWLObjectProperty().getIRI().toString();
            OntologyHeadDefinition definition = new OntologyHeadDefinition(
                    "meta-symmetric-" + shortForm(predicate),
                    List.of(triple(
                            OntologyHeadDefinition.variable("a"),
                            OntologyHeadDefinition.constant(predicate),
                            OntologyHeadDefinition.variable("b")
                    )),
                    triple(
                            OntologyHeadDefinition.variable("b"),
                            OntologyHeadDefinition.constant(predicate),
                            OntologyHeadDefinition.variable("a")
                    ),
                    META_SYMMETRIC_WEIGHT
            );
            definitions.add(definition);
        }
        return definitions;
    }

    private static List<OntologyHeadDefinition> buildInverseHeads(OWLOntology ontology) {
        List<OntologyHeadDefinition> definitions = new ArrayList<>();
        for (OWLInverseObjectPropertiesAxiom axiom : ontology.getAxioms(org.semanticweb.owlapi.model.AxiomType.INVERSE_OBJECT_PROPERTIES)) {
            List<OWLObjectPropertyExpression> props = axiom.getProperties().stream()
                    .filter(prop -> !prop.isAnonymous())
                    .collect(Collectors.toList());
            if (props.size() != 2) {
                continue;
            }
            String left = props.get(0).asOWLObjectProperty().getIRI().toString();
            String right = props.get(1).asOWLObjectProperty().getIRI().toString();
            definitions.add(new OntologyHeadDefinition(
                    "meta-inverse-" + shortForm(left),
                    List.of(triple(
                            OntologyHeadDefinition.variable("a"),
                            OntologyHeadDefinition.constant(left),
                            OntologyHeadDefinition.variable("b")
                    )),
                    triple(
                            OntologyHeadDefinition.variable("b"),
                            OntologyHeadDefinition.constant(right),
                            OntologyHeadDefinition.variable("a")
                    ),
                    META_INVERSE_WEIGHT
            ));
            definitions.add(new OntologyHeadDefinition(
                    "meta-inverse-" + shortForm(right),
                    List.of(triple(
                            OntologyHeadDefinition.variable("a"),
                            OntologyHeadDefinition.constant(right),
                            OntologyHeadDefinition.variable("b")
                    )),
                    triple(
                            OntologyHeadDefinition.variable("b"),
                            OntologyHeadDefinition.constant(left),
                            OntologyHeadDefinition.variable("a")
                    ),
                    META_INVERSE_WEIGHT
            ));
        }
        return definitions;
    }

    private static OntologyHeadDefinition.TriplePattern triple(OntologyHeadDefinition.Term subject,
                                                               OntologyHeadDefinition.Term predicate,
                                                               OntologyHeadDefinition.Term object) {
        return new OntologyHeadDefinition.TriplePattern(subject, predicate, object);
    }

    private static String shortForm(String iri) {
        if (iri == null) {
            return "predicate";
        }
        int idx = Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/'));
        if (idx >= 0 && idx < iri.length() - 1) {
            return iri.substring(idx + 1);
        }
        return iri.replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase(Locale.ROOT);
    }

    private static String signature(OntologyHeadDefinition definition) {
        StringBuilder builder = new StringBuilder();
        builder.append(definition.executorType()).append(':');
        if (!definition.executorParams().isEmpty()) {
            definition.executorParams().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append(entry.getKey())
                            .append('=')
                            .append(entry.getValue())
                            .append(','));
        }
        for (OntologyHeadDefinition.TriplePattern pattern : definition.patterns()) {
            appendPattern(builder, pattern);
        }
        builder.append("->");
        if (definition.action() != null) {
            appendPattern(builder, definition.action());
        }
        return builder.toString();
    }

    private static void appendPattern(StringBuilder builder, OntologyHeadDefinition.TriplePattern pattern) {
        builder.append(termSignature(pattern.subject()))
                .append('|')
                .append(termSignature(pattern.predicate()))
                .append('|')
                .append(termSignature(pattern.object()))
                .append(';');
    }

    private static String termSignature(OntologyHeadDefinition.Term term) {
        if (term == null) {
            return "null";
        }
        return (term.isVariable() ? "?" : "") + term.value();
    }

    private static java.util.Map<String, String> loadExecutorParams(OWLOntology ontology,
                                                                    OWLNamedIndividual head,
                                                                    OWLObjectProperty hasExecutorParam,
                                                                    OWLClass executorParamClass,
                                                                    OWLDataProperty paramKeyProp,
                                                                    OWLDataProperty paramValueProp) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        for (OWLNamedIndividual param : objectPropertyValues(ontology, head, hasExecutorParam)) {
            if (!isInstanceOf(ontology, param, executorParamClass)) {
                continue;
            }
            Optional<String> key = dataPropertyValue(ontology, param, paramKeyProp);
            Optional<String> value = dataPropertyValue(ontology, param, paramValueProp);
            if (key.isPresent() && value.isPresent()) {
                params.put(key.get(), value.get());
            }
        }
        return params;
    }
}

package com.sahr.ontology;

public final class SahrAnnotationVocabulary {
    private SahrAnnotationVocabulary() {}

    public static final String NAMESPACE = "https://sahr.ai/ontology/annotations#";

    public static final String ANSWER_TEMPLATE = NAMESPACE + "answerTemplate";
    public static final String ANSWER_TEMPLATE_TRUE = NAMESPACE + "answerTemplateTrue";
    public static final String ANSWER_TEMPLATE_FALSE = NAMESPACE + "answerTemplateFalse";
    public static final String DYNAMIC_WEIGHT = NAMESPACE + "dynamicWeight";
    public static final String TEMPORAL_WEIGHT = NAMESPACE + "temporalWeight";
    public static final String EVIDENCE_WEIGHT = NAMESPACE + "evidenceWeight";
}

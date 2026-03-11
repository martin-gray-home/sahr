package com.sahr.nlp;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;

import java.util.Properties;

final class CoreNlpPipeline {
    private static final StanfordCoreNLP PIPELINE = buildPipeline();

    private CoreNlpPipeline() {
    }

    static StanfordCoreNLP get() {
        return PIPELINE;
    }

    private static StanfordCoreNLP buildPipeline() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse");
        props.setProperty("ssplit.isOneSentence", "true");
        props.setProperty("depparse.model", "edu/stanford/nlp/models/parser/nndep/english_UD.gz");
        return new StanfordCoreNLP(props);
    }
}

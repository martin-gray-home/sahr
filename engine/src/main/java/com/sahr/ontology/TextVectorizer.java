package com.sahr.ontology;

public interface TextVectorizer {
    float[] vectorize(String text);

    int dimensions();
}

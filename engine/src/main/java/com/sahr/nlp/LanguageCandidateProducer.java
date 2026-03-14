package com.sahr.nlp;

import java.util.List;

public interface LanguageCandidateProducer {
    List<LanguageQueryCandidate> produce(String input);
}

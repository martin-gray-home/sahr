package com.sahr.ontology;

import com.sahr.core.OntologyService;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

public final class CachedOntologyService implements OntologyService {
    private static final Logger logger = Logger.getLogger(CachedOntologyService.class.getName());

    private final OntologyService delegate;
    private final ConcurrentMap<String, Boolean> symmetricCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> transitiveCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Optional<String>> inverseCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> superCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> subCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> subPropertyCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> rangeCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Boolean>> subclassCache = new ConcurrentHashMap<>();

    public CachedOntologyService(OntologyService delegate) {
        this.delegate = delegate;
        logger.info("CachedOntologyService initialized (lazy memoization).");
    }

    @Override
    public boolean isSubclassOf(String child, String parent) {
        return subclassCache
                .computeIfAbsent(child, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(parent, key -> delegate.isSubclassOf(child, parent));
    }

    @Override
    public boolean isSymmetricProperty(String property) {
        return symmetricCache.computeIfAbsent(property, delegate::isSymmetricProperty);
    }

    @Override
    public boolean isTransitiveProperty(String property) {
        return transitiveCache.computeIfAbsent(property, delegate::isTransitiveProperty);
    }

    @Override
    public Optional<String> getInverseProperty(String property) {
        return inverseCache.computeIfAbsent(property, delegate::getInverseProperty);
    }

    @Override
    public Set<String> getSuperclasses(String concept) {
        return superCache.computeIfAbsent(concept, delegate::getSuperclasses);
    }

    @Override
    public Set<String> getSubclasses(String concept) {
        return subCache.computeIfAbsent(concept, delegate::getSubclasses);
    }

    @Override
    public Set<String> getSubproperties(String property) {
        return subPropertyCache.computeIfAbsent(property, delegate::getSubproperties);
    }

    @Override
    public Set<String> getObjectPropertyRanges(String property) {
        return rangeCache.computeIfAbsent(property, delegate::getObjectPropertyRanges);
    }
}

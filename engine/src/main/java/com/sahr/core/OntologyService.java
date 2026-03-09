package com.sahr.core;

import java.util.Optional;
import java.util.Set;

public interface OntologyService {
    boolean isSubclassOf(String child, String parent);

    boolean isSymmetricProperty(String property);

    boolean isTransitiveProperty(String property);

    Optional<String> getInverseProperty(String property);

    Set<String> getSuperclasses(String concept);

    Set<String> getSubclasses(String concept);

    Set<String> getSubproperties(String property);

    Set<String> getObjectPropertyRanges(String property);
}

package dev.recipeeditor.recipeengine;

/** Confidence describes evidence, not the likelihood of a guessed owner. */
public enum ProvenanceConfidence {
    UNKNOWN, PHYSICAL_LOCATION_ONLY, PROJECT_OVERLAY, RUNTIME_OBSERVED_ORIGIN_UNKNOWN
}

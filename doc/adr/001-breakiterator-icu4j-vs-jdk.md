# ADR 001: JDK BreakIterator vs ICU4J for Grapheme Clustering

## Status

Accepted

## Context

Terminal UI applications need to correctly measure text width for layout purposes. This requires splitting strings into grapheme clusters—what users perceive as single characters, even when composed of multiple Unicode code points (e.g., emoji sequences like "👨‍👩‍👧" which use Zero-Width Joiners).

Two options exist for grapheme cluster detection in the JVM:

1. **ICU4J** (`com.ibm.icu.text.BreakIterator`)
2. **JDK** (`java.text.BreakIterator`)

## Decision

Use the JDK's built-in `java.text.BreakIterator`.

## Consequences

### Pros of JDK BreakIterator

- **No external dependency** - reduces JAR size by ~13MB
- **Faster startup** - no additional classes to load
- **Simpler native-image compilation** - no reflection configuration needed for ICU4J internals
- **Sufficient for modern JDKs** - JDK 17+ handles most grapheme clusters correctly, including emoji ZWJ sequences

### Cons of JDK BreakIterator

- **Unicode version tied to JDK** - ICU4J is updated more frequently with latest Unicode standard
- **Edge cases on older JDKs** - JDK 11 and earlier may not handle all emoji sequences correctly
- **Less control** - cannot upgrade Unicode support independently of JDK version

### Pros of ICU4J (if reconsidering)

- **Latest Unicode support** - frequently updated, independent of JDK releases
- **Consistent behavior** - same results across all JDK versions
- **Additional features** - locale-specific break iteration, more granular control

### Cons of ICU4J

- **Large dependency** - ~13MB for icu4j alone
- **Native-image complexity** - requires reflection and resource configuration for GraalVM
- **Overkill** - most features unused when only grapheme clustering is needed

## Notes

The decision can be revisited if:
- Users report grapheme clustering issues on supported JDK versions
- A need arises for locale-specific text segmentation
- Unicode sequences emerge that JDK handles incorrectly

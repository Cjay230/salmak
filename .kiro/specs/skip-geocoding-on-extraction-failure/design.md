# Design Document: Skip Geocoding on Extraction Failure

## Overview

This feature adds a guard clause inside the `Location_Extractor` component that short-circuits the pipeline before the `Geocoding_Step` whenever the `Extraction_Result` is null, empty, or whitespace-only. The change is surgical: it introduces a single validation check between the text-parsing sub-step and the geocoding sub-step, emits a structured `EXTRACTION_FAILED` log entry on the failure path, and returns an empty result without touching the `Geocoding_Service`.

The motivation is threefold:
1. **Cost and rate-limit hygiene** — the geocoding service is an external paid API; calling it with empty input wastes quota.
2. **Latency** — skipping the network round-trip on a known-bad input reduces end-to-end processing time.
3. **Observability** — operators currently cannot distinguish "the post had no location text" from "the geocoder failed"; this feature makes that distinction explicit in the logs.

No new AWS resources are required. The change is entirely within the `Location_Extractor` Lambda function.

---

## Architecture

The `Location_Extractor` Lambda sits between the `X_Monitor` (which enqueues `Warning_Post` events) and the `Proximity_Engine` (which consumes resolved coordinates). Its internal pipeline currently has two sequential sub-steps:

```
Warning_Post
     │
     ▼
┌─────────────────────┐
│  Text Parsing Step  │  ← extracts raw location string from post text
└─────────────────────┘
     │  Extraction_Result (String | null)
     ▼
┌─────────────────────┐
│   Geocoding Step    │  ← calls external Geocoding_Service
└─────────────────────┘
     │  Coordinate (lat/lng) | null
     ▼
Proximity_Engine / discard
```

After this feature, a guard is inserted between the two sub-steps:

```
Warning_Post
     │
     ▼
┌─────────────────────┐
│  Text Parsing Step  │
└─────────────────────┘
     │  Extraction_Result (String | null)
     ▼
┌──────────────────────────────────────────┐
│  Extraction Guard                        │
│  isBlank(result)?                        │
│    YES → log EXTRACTION_FAILED, return ∅ │
│    NO  → continue                        │
└──────────────────────────────────────────┘
     │  non-blank Extraction_Result
     ▼
┌─────────────────────┐
│   Geocoding Step    │
└─────────────────────┘
     │  Coordinate | null
     ▼
Proximity_Engine / discard (GEOCODING_FAILED)
```

The guard is a pure in-process check — no I/O, no new dependencies.

---

## Components and Interfaces

### ExtractionGuard

A small, stateless utility (a static method or a dedicated class) responsible for validating the `Extraction_Result` before the geocoding sub-step is invoked.

```java
/**
 * Returns true if the extraction result is null, empty, or contains
 * only whitespace characters (space U+0020, tab U+0009, newline U+000A).
 */
public static boolean isBlankExtractionResult(String result) {
    return result == null || result.isBlank();
}
```

`String.isBlank()` (Java 11+, available on Java 21) returns `true` for null-safe empty strings and strings composed entirely of Unicode whitespace, which covers the space, tab, and newline characters specified in the requirements.

> **Note:** `String.isBlank()` is not null-safe; the null check must precede it, as shown above.

### LocationExtractorHandler (modified)

The existing Lambda handler gains the guard call between the parsing sub-step and the geocoding sub-step:

```java
String extractionResult = textParsingStep.parse(warningPost);

if (ExtractionGuard.isBlankExtractionResult(extractionResult)) {
    String postId = resolvePostId(warningPost); // returns "UNKNOWN" if unavailable
    log.warn("Geocoding skipped — extraction produced no location string. " +
             "postId={} reason=EXTRACTION_FAILED", postId);
    return ExtractionOutcome.empty();
}

Coordinate coordinate = geocodingStep.resolve(extractionResult);
```

### FailureReason (enum)

A new enum (or string constant class) to make failure reasons first-class values and prevent typos:

```java
public enum FailureReason {
    EXTRACTION_FAILED,
    GEOCODING_FAILED
}
```

### ExtractionOutcome (result type)

The return type of the `Location_Extractor` pipeline. If one does not already exist, it should be introduced to carry either a resolved `Coordinate` or an explicit empty/failure state:

```java
public record ExtractionOutcome(
    Optional<Coordinate> coordinate,
    Optional<FailureReason> failureReason
) {
    public static ExtractionOutcome empty() {
        return new ExtractionOutcome(Optional.empty(), Optional.of(FailureReason.EXTRACTION_FAILED));
    }

    public static ExtractionOutcome geocodingFailure() {
        return new ExtractionOutcome(Optional.empty(), Optional.of(FailureReason.GEOCODING_FAILED));
    }

    public static ExtractionOutcome success(Coordinate coordinate) {
        return new ExtractionOutcome(Optional.of(coordinate), Optional.empty());
    }
}
```

### Logging Contract

All log entries emitted by the `Location_Extractor` on the failure path MUST follow this structured format:

| Field         | Value                                      |
|---------------|--------------------------------------------|
| `level`       | `WARN`                                     |
| `reason`      | `EXTRACTION_FAILED` or `GEOCODING_FAILED`  |
| `postId`      | Warning_Post ID, or `UNKNOWN` if absent    |
| `message`     | Human-readable description of the failure |

Example (SLF4J / structured logging):
```java
log.warn("Location extraction pipeline discarded post. postId={} reason={}", postId, FailureReason.EXTRACTION_FAILED);
```

---

## Data Models

No new DynamoDB tables or schema changes are required. This feature operates entirely within the in-process pipeline of the `Location_Extractor` Lambda.

### Extraction_Result (in-memory)

| Field  | Type             | Description                                                      |
|--------|------------------|------------------------------------------------------------------|
| value  | `String \| null` | Raw location string from text parsing; null if nothing was found |

Validity rule: a result is **valid** if and only if `value != null && !value.isBlank()`.

### ExtractionOutcome (in-memory record)

| Field          | Type                      | Description                                      |
|----------------|---------------------------|--------------------------------------------------|
| `coordinate`   | `Optional<Coordinate>`    | Present on success; empty on any failure         |
| `failureReason`| `Optional<FailureReason>` | Present on failure; empty on success             |

Invariant: exactly one of `coordinate` and `failureReason` is present at any time.

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Blank extraction result never reaches the geocoder

*For any* `Warning_Post` whose `Extraction_Result` is null, empty, or composed entirely of whitespace characters, the `Location_Extractor` SHALL produce an `ExtractionOutcome` with no `Coordinate` and SHALL NOT invoke the `Geocoding_Service`.

**Validates: Requirements 1.1, 3.1, 3.2, 3.3**

---

### Property 2: Non-blank extraction result always proceeds to geocoding

*For any* `Warning_Post` whose `Extraction_Result` is a non-null, non-empty, non-whitespace-only string, the `Location_Extractor` SHALL invoke the `Geocoding_Step` with that string.

**Validates: Requirements 1.4**

---

### Property 3: Extraction failure emits exactly EXTRACTION_FAILED — never GEOCODING_FAILED

*For any* `Warning_Post` whose `Extraction_Result` is blank, the log output produced by the `Location_Extractor` SHALL contain exactly one `EXTRACTION_FAILED` entry for that post ID and SHALL contain zero `GEOCODING_FAILED` entries for that same post ID.

**Validates: Requirements 2.1, 2.3**

---

### Property 4: Geocoding failure emits exactly GEOCODING_FAILED — never EXTRACTION_FAILED

*For any* `Warning_Post` whose `Extraction_Result` is non-blank but whose `Geocoding_Service` call fails (error, timeout, or no result), the log output SHALL contain exactly one `GEOCODING_FAILED` entry for that post ID and SHALL contain zero `EXTRACTION_FAILED` entries for that same post ID.

**Validates: Requirements 2.2, 2.3**

---

### Property 5: Unknown post ID is replaced by placeholder in log entries

*For any* `Warning_Post` whose ID is unavailable at logging time, every log entry emitted by the `Location_Extractor` for that post SHALL contain the literal string `UNKNOWN` in the post-ID field.

**Validates: Requirements 2.4**

---

### Property 6: Blank-check covers all specified whitespace variants

*For any* string composed exclusively of any combination of space (U+0020), tab (U+0009), and newline (U+000A) characters, the `ExtractionGuard` SHALL classify it as blank and the `Location_Extractor` SHALL skip the `Geocoding_Step`.

**Validates: Requirements 1.1, 3.1**

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| `Extraction_Result` is `null` | Guard catches it; logs `EXTRACTION_FAILED`; returns `ExtractionOutcome.empty()`. No geocoding call. |
| `Extraction_Result` is `""` (empty string) | Same as null. |
| `Extraction_Result` is whitespace-only (`" "`, `"\t"`, `"\n"`, combinations) | Same as null. |
| `Extraction_Result` is non-blank | Guard passes; geocoding proceeds normally. |
| `Geocoding_Service` returns error / timeout / no result | Existing behaviour retained; logs `GEOCODING_FAILED`; returns `ExtractionOutcome.geocodingFailure()`. |
| `Warning_Post` ID is null or missing | `resolvePostId()` returns the literal `"UNKNOWN"` string; logging proceeds. |
| Exception thrown inside `ExtractionGuard.isBlankExtractionResult()` | Cannot occur — the method is a pure null-safe boolean check with no I/O. |

The guard MUST be placed before any call to `geocodingStep.resolve()`. If the guard itself throws an unexpected runtime exception (e.g., due to a future refactor), the Lambda's top-level exception handler should catch it, log it, and discard the post — consistent with the existing pipeline's error policy.

---

## Testing Strategy

### Unit Tests (example-based)

These cover specific, concrete scenarios:

| Test | Input | Expected |
|---|---|---|
| `null` extraction result | `null` | `ExtractionOutcome.empty()`, geocoder not called, `EXTRACTION_FAILED` logged |
| Empty string extraction result | `""` | Same as above |
| Space-only extraction result | `" "` | Same as above |
| Tab-only extraction result | `"\t"` | Same as above |
| Newline-only extraction result | `"\n"` | Same as above |
| Mixed whitespace extraction result | `" \t\n"` | Same as above |
| Valid extraction result | `"Beirut"` | Geocoder called with `"Beirut"` |
| Geocoder failure on valid result | `"Beirut"` + geocoder throws | `ExtractionOutcome.geocodingFailure()`, `GEOCODING_FAILED` logged |
| Missing post ID | `null` post ID | Log entry contains `"UNKNOWN"` |

### Property-Based Tests

Property-based testing is applicable here because the core logic — the blank-check guard — is a pure function whose correctness must hold across a large and varied input space (all possible strings, including Unicode whitespace variants, mixed content, and edge cases). The Java property-based testing library **jqwik** is used (available on Maven Central, compatible with JUnit 5 and Java 21).

Each property test runs a minimum of **100 iterations** with randomly generated inputs.

#### Property Test 1 — Blank extraction result never reaches the geocoder
**Tag:** `Feature: skip-geocoding-on-extraction-failure, Property 1: blank extraction result never reaches the geocoder`

Generate: any string that is null, empty, or `isBlank() == true`.
Assert: `ExtractionGuard.isBlankExtractionResult(input) == true`; mock geocoder is never invoked; returned outcome has no coordinate and has `FailureReason.EXTRACTION_FAILED`.

#### Property Test 2 — Non-blank extraction result always proceeds to geocoding
**Tag:** `Feature: skip-geocoding-on-extraction-failure, Property 2: non-blank extraction result always proceeds to geocoding`

Generate: any non-null, non-blank string (at least one non-whitespace character).
Assert: `ExtractionGuard.isBlankExtractionResult(input) == false`; mock geocoder is invoked exactly once with that string.

#### Property Test 3 — Extraction failure emits exactly EXTRACTION_FAILED — never GEOCODING_FAILED
**Tag:** `Feature: skip-geocoding-on-extraction-failure, Property 3: extraction failure emits exactly EXTRACTION_FAILED — never GEOCODING_FAILED`

Generate: any blank extraction result.
Assert: captured log entries for the post contain exactly one entry with `reason=EXTRACTION_FAILED` and zero entries with `reason=GEOCODING_FAILED`.

#### Property Test 4 — Geocoding failure emits exactly GEOCODING_FAILED — never EXTRACTION_FAILED
**Tag:** `Feature: skip-geocoding-on-extraction-failure, Property 4: geocoding failure emits exactly GEOCODING_FAILED — never EXTRACTION_FAILED`

Generate: any non-blank extraction result; mock geocoder configured to fail.
Assert: captured log entries contain exactly one entry with `reason=GEOCODING_FAILED` and zero entries with `reason=EXTRACTION_FAILED`.

#### Property Test 5 — Unknown post ID is replaced by placeholder
**Tag:** `Feature: skip-geocoding-on-extraction-failure, Property 5: unknown post ID is replaced by placeholder`

Generate: any blank extraction result; post ID set to null or absent.
Assert: every log entry for that post contains the literal string `"UNKNOWN"` in the post-ID field.

#### Property Test 6 — Blank-check covers all specified whitespace variants
**Tag:** `Feature: skip-geocoding-on-extraction-failure, Property 6: blank-check covers all specified whitespace variants`

Generate: strings composed exclusively of any combination of `' '`, `'\t'`, `'\n'` characters (including the empty string).
Assert: `ExtractionGuard.isBlankExtractionResult(input) == true` for every generated value.

### Integration Tests

Not required for this feature. The guard is a pure in-process check with no new external dependencies. The existing integration test suite for the `Location_Extractor` Lambda (which exercises the geocoding path end-to-end) remains valid and unchanged.

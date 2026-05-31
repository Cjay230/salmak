# Implementation Plan: Skip Geocoding on Extraction Failure

## Overview

Introduce a guard clause inside the `Location_Extractor` Lambda that short-circuits the pipeline before the `Geocoding_Step` whenever the `Extraction_Result` is null, empty, or whitespace-only. The change adds `ExtractionGuard`, `FailureReason`, and `ExtractionOutcome` types, wires the guard into `LocationExtractorHandler`, and validates correctness with unit and property-based tests using jqwik.

## Tasks

- [ ] 1. Introduce core types: `FailureReason`, `ExtractionOutcome`, and `ExtractionGuard`
  - [ ] 1.1 Create the `FailureReason` enum with `EXTRACTION_FAILED` and `GEOCODING_FAILED` constants
    - Create `FailureReason.java` with the two enum values
    - _Requirements: 2.1, 2.2_

  - [ ] 1.2 Create the `ExtractionOutcome` record with `coordinate`, `failureReason`, and factory methods
    - Implement `ExtractionOutcome.empty()`, `ExtractionOutcome.geocodingFailure()`, and `ExtractionOutcome.success(Coordinate)`
    - Ensure exactly one of `coordinate` and `failureReason` is present in each factory result
    - _Requirements: 1.1, 1.3, 2.1, 2.2_

  - [ ] 1.3 Create `ExtractionGuard` with the `isBlankExtractionResult(String)` static method
    - Implement the null-then-`isBlank()` check as specified in the design
    - _Requirements: 1.1, 3.1, 3.2, 3.3_

  - [ ]* 1.4 Write property test for `ExtractionGuard` — Property 1: blank extraction result never reaches the geocoder
    - **Property 1: Blank extraction result never reaches the geocoder**
    - **Validates: Requirements 1.1, 3.1, 3.2, 3.3**
    - Generate null, empty, and `isBlank() == true` strings; assert `isBlankExtractionResult` returns `true`
    - Tag: `Feature: skip-geocoding-on-extraction-failure, Property 1: blank extraction result never reaches the geocoder`

  - [ ]* 1.5 Write property test for `ExtractionGuard` — Property 2: non-blank extraction result always proceeds to geocoding
    - **Property 2: Non-blank extraction result always proceeds to geocoding**
    - **Validates: Requirements 1.4**
    - Generate non-null, non-blank strings; assert `isBlankExtractionResult` returns `false`
    - Tag: `Feature: skip-geocoding-on-extraction-failure, Property 2: non-blank extraction result always proceeds to geocoding`

  - [ ]* 1.6 Write property test for `ExtractionGuard` — Property 6: blank-check covers all specified whitespace variants
    - **Property 6: Blank-check covers all specified whitespace variants**
    - **Validates: Requirements 1.1, 3.1**
    - Generate strings composed exclusively of `' '`, `'\t'`, `'\n'` characters (including empty string); assert `isBlankExtractionResult` returns `true` for every value
    - Tag: `Feature: skip-geocoding-on-extraction-failure, Property 6: blank-check covers all specified whitespace variants`

- [ ] 2. Wire the guard into `LocationExtractorHandler`
  - [ ] 2.1 Modify `LocationExtractorHandler` to call `ExtractionGuard.isBlankExtractionResult` between the text-parsing sub-step and the geocoding sub-step
    - Insert the guard call after `textParsingStep.parse(warningPost)` and before `geocodingStep.resolve(...)`
    - On blank result: call `resolvePostId(warningPost)` (returning `"UNKNOWN"` if ID is absent), emit a `WARN`-level log entry with `postId` and `reason=EXTRACTION_FAILED`, and return `ExtractionOutcome.empty()`
    - On non-blank result: proceed to `geocodingStep.resolve(extractionResult)` as before
    - Update the geocoding-failure path to return `ExtractionOutcome.geocodingFailure()` and log `reason=GEOCODING_FAILED`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3_

  - [ ]* 2.2 Write unit tests for `LocationExtractorHandler` guard integration
    - Cover: `null` result, `""`, `" "`, `"\t"`, `"\n"`, `" \t\n"` → `ExtractionOutcome.empty()`, geocoder not called, `EXTRACTION_FAILED` logged
    - Cover: valid string `"Beirut"` → geocoder called with `"Beirut"`
    - Cover: valid string + geocoder throws → `ExtractionOutcome.geocodingFailure()`, `GEOCODING_FAILED` logged
    - Cover: null post ID → log entry contains `"UNKNOWN"`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3_

- [ ] 3. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Validate logging contract with property-based tests
  - [ ] 4.1 Write property test for logging — Property 3: extraction failure emits exactly EXTRACTION_FAILED — never GEOCODING_FAILED
    - **Property 3: Extraction failure emits exactly EXTRACTION_FAILED — never GEOCODING_FAILED**
    - **Validates: Requirements 2.1, 2.3**
    - Generate any blank extraction result; assert captured log entries contain exactly one `EXTRACTION_FAILED` entry and zero `GEOCODING_FAILED` entries for the post
    - Tag: `Feature: skip-geocoding-on-extraction-failure, Property 3: extraction failure emits exactly EXTRACTION_FAILED — never GEOCODING_FAILED`

  - [ ] 4.2 Write property test for logging — Property 4: geocoding failure emits exactly GEOCODING_FAILED — never EXTRACTION_FAILED
    - **Property 4: Geocoding failure emits exactly GEOCODING_FAILED — never EXTRACTION_FAILED**
    - **Validates: Requirements 2.2, 2.3**
    - Generate any non-blank extraction result with mock geocoder configured to fail; assert captured log entries contain exactly one `GEOCODING_FAILED` entry and zero `EXTRACTION_FAILED` entries for the post
    - Tag: `Feature: skip-geocoding-on-extraction-failure, Property 4: geocoding failure emits exactly GEOCODING_FAILED — never EXTRACTION_FAILED`

  - [ ] 4.3 Write property test for logging — Property 5: unknown post ID is replaced by placeholder
    - **Property 5: Unknown post ID is replaced by placeholder**
    - **Validates: Requirements 2.4**
    - Generate any blank extraction result with post ID set to null or absent; assert every log entry for that post contains the literal string `"UNKNOWN"` in the post-ID field
    - Tag: `Feature: skip-geocoding-on-extraction-failure, Property 5: unknown post ID is replaced by placeholder`

- [ ] 5. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests use jqwik (JUnit 5 compatible, Java 21) with a minimum of 100 iterations each
- Unit tests cover the concrete examples from the design's testing strategy table
- `String.isBlank()` is null-unsafe; the null guard must always precede it (as shown in the design)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["1.4", "1.5", "1.6", "2.1"] },
    { "id": 3, "tasks": ["2.2", "4.1", "4.2", "4.3"] }
  ]
}
```

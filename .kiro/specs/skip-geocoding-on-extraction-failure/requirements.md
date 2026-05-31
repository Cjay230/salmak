# Requirements Document

## Introduction

This feature refines the location-extraction pipeline in Salmak. Currently, when the Location_Extractor fails to parse any location reference from a Warning_Post, the system may still attempt to invoke the geocoding service with an empty or null value. This feature ensures that the geocoding step is skipped entirely when extraction yields no location reference, preventing unnecessary external API calls, reducing latency, and producing a clean, observable failure path.

## Glossary

- **Location_Extractor**: The backend component that parses a Warning_Post and extracts a geographic coordinate or place name, resolving it to a latitude/longitude.
- **Extraction_Result**: The output of the parsing step within the Location_Extractor — either a non-empty location string (place name, landmark, or raw coordinates) or an explicit empty/null value indicating no location was found.
- **Geocoding_Step**: The sub-step within the Location_Extractor that calls an external geocoding service to resolve a place name or landmark to a latitude/longitude coordinate.
- **Warning_Post**: A post published on the monitored public X account that contains an emergency warning and a target location.
- **Geocoding_Service**: The external API used to resolve a place name or landmark to a latitude/longitude coordinate.

---

## Requirements

### Requirement 1: Guard Geocoding on Empty Extraction Result

**User Story:** As the system, I want to skip the geocoding step when no location string was extracted from a Warning_Post, so that the system does not make unnecessary external API calls with empty or null input.

#### Acceptance Criteria

1. WHEN the Location_Extractor parses a Warning_Post, IF the Extraction_Result is null, empty, or contains only whitespace characters (space, tab, or newline), THEN THE Location_Extractor SHALL skip the Geocoding_Step entirely.
2. WHEN the Location_Extractor skips the Geocoding_Step due to a null, empty, or whitespace-only Extraction_Result, THE Location_Extractor SHALL log a message indicating that geocoding was skipped, including the Warning_Post ID and the reason for skipping.
3. WHEN the Location_Extractor skips the Geocoding_Step due to a null, empty, or whitespace-only Extraction_Result, THE Location_Extractor SHALL perform no proximity query and no alert dispatch for that Warning_Post.
4. WHEN the Extraction_Result is a non-empty, non-whitespace location string, THE Location_Extractor SHALL proceed to the Geocoding_Step as normal.

---

### Requirement 2: Distinguish Extraction Failure from Geocoding Failure

**User Story:** As a system operator, I want extraction failures and geocoding failures to be logged distinctly, so that I can diagnose pipeline issues accurately.

#### Acceptance Criteria

1. WHEN the Location_Extractor discards a Warning_Post due to a null, empty, or whitespace-only Extraction_Result, THE Location_Extractor SHALL emit a WARNING-level log entry with a failure reason of `EXTRACTION_FAILED` and the Warning_Post ID before the Warning_Post is discarded.
2. WHEN the Location_Extractor discards a Warning_Post because the Geocoding_Service returned an error response, timed out, threw an exception, or returned no result, THE Location_Extractor SHALL emit a WARNING-level log entry with a failure reason of `GEOCODING_FAILED` and the Warning_Post ID before the Warning_Post is discarded.
3. WHEN a Warning_Post is discarded due to a null, empty, or whitespace-only Extraction_Result, THE Location_Extractor SHALL emit only the `EXTRACTION_FAILED` log entry and SHALL NOT emit a `GEOCODING_FAILED` log entry for that Warning_Post.
4. WHEN the Warning_Post ID is unavailable at the time of logging, THE Location_Extractor SHALL include a placeholder value (e.g., `UNKNOWN`) in the log entry in place of the Warning_Post ID.

---

### Requirement 3: No External Call on Extraction Failure

**User Story:** As the system, I want to guarantee that the geocoding service is never called with empty or null input, so that I avoid unnecessary costs, rate-limit consumption, and unpredictable third-party error responses.

#### Acceptance Criteria

1. WHEN the Extraction_Result is null, empty, or contains only whitespace, THE Location_Extractor SHALL NOT invoke the Geocoding_Service.
2. THE Location_Extractor SHALL evaluate the Extraction_Result and confirm it is a non-empty, non-whitespace string before initiating any network call to the Geocoding_Service.
3. IF the Extraction_Result fails the non-empty, non-whitespace check, THEN THE Location_Extractor SHALL return an empty result without invoking the Geocoding_Service.

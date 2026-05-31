# Requirements Document

## Introduction

Salmak (سلمك — "keep you safe") is a Lebanese emergency alert mobile application. The system monitors a designated public X (Twitter) account for warning posts, extracts the targeted location from each post, identifies registered users within 500 meters of that location, and delivers an immediate loud push notification that bypasses silent/do-not-disturb mode. Users register with their phone number and home location (latitude/longitude). The backend runs on Java 21, AWS Lambda, DynamoDB, and is deployed via AWS SAM.

## Glossary

- **Salmak**: The mobile application and overall system name.
- **User**: A registered individual who has provided a phone number and home location.
- **Home_Location**: A geographic coordinate (latitude/longitude) stored per user at registration time.
- **Alert_Zone**: The circular area of 500-meter radius centered on the location extracted from a warning post.
- **Warning_Post**: A post published on the monitored public X account that contains an emergency warning and a target location.
- **X_Monitor**: The backend component responsible for polling the designated public X account for new Warning_Posts.
- **Location_Extractor**: The backend component that parses a Warning_Post and extracts a geographic coordinate or place name, resolving it to a latitude/longitude.
- **Proximity_Engine**: The backend component that computes which registered Users fall within the Alert_Zone.
- **Notification_Service**: The backend component that dispatches push notifications to targeted Users.
- **Push_Notification**: A mobile OS-level notification delivered via FCM (Android) or APNs (iOS) configured with high-priority/critical-alert flags to bypass silent mode.
- **Registration_Service**: The backend component that handles user sign-up, phone verification, and location storage.
- **DynamoDB**: The AWS managed NoSQL database used to persist user records and processed post IDs.
- **SAM**: AWS Serverless Application Model, used to define and deploy all Lambda functions and infrastructure.

---

## Requirements

### Requirement 1: User Registration

**User Story:** As a Lebanese resident, I want to register with my phone number and home location, so that I can receive emergency alerts relevant to my area.

#### Acceptance Criteria

1. WHEN a user submits a phone number and home location (latitude, longitude), THE Registration_Service SHALL create a new user record in DynamoDB containing the phone number, latitude, longitude, device push token, and registration timestamp.
2. WHEN a user submits a phone number that is already registered, THE Registration_Service SHALL update the existing record with the new home location and device push token.
3. WHEN a user submits a phone number that does not conform to the Lebanese phone number format (e.g., +961 followed by 7 or 8 digits), THE Registration_Service SHALL reject the request and return a descriptive validation error.
4. WHEN a user submits a latitude value outside the range [-90, 90] or a longitude value outside the range [-180, 180], THE Registration_Service SHALL reject the request and return a descriptive validation error.
5. THE Registration_Service SHALL store the home location with a precision of at least 6 decimal places to support sub-meter geospatial accuracy.

---

### Requirement 2: X Account Monitoring

**User Story:** As the system operator, I want the system to continuously monitor a designated public X account, so that warning posts are detected promptly.

#### Acceptance Criteria

1. WHEN a new post is published on the monitored X account, THE X_Monitor SHALL detect the post within 60 seconds of publication.
2. THE X_Monitor SHALL poll the X API at a configurable interval of no less than 15 seconds and no more than 60 seconds.
3. WHEN a post has already been processed, THE X_Monitor SHALL skip it and not trigger duplicate alert processing.
4. IF the X API returns an error or rate-limit response, THEN THE X_Monitor SHALL log the error, apply exponential back-off, and retry without dropping unprocessed posts.
5. THE X_Monitor SHALL store the ID of each processed post in DynamoDB to prevent duplicate processing across Lambda invocations.

---

### Requirement 3: Location Extraction from Warning Posts

**User Story:** As the system, I want to extract a geographic coordinate from each warning post, so that I can determine the affected area.

#### Acceptance Criteria

1. WHEN a Warning_Post is received, THE Location_Extractor SHALL parse the post text and extract a location reference (place name, coordinates, or Lebanese landmark).
2. WHEN the extracted location reference is a place name or landmark, THE Location_Extractor SHALL resolve it to a latitude/longitude coordinate using a geocoding service.
3. WHEN the post text contains no recognizable location reference, THE Location_Extractor SHALL log the failure and discard the post without triggering an alert.
4. IF the geocoding service returns an error or no result, THEN THE Location_Extractor SHALL log the failure and discard the post without triggering an alert.
5. THE Location_Extractor SHALL complete location extraction within 5 seconds of receiving a Warning_Post.

---

### Requirement 4: Proximity Detection

**User Story:** As the system, I want to identify all registered users within 500 meters of the warned location, so that only affected users receive the alert.

#### Acceptance Criteria

1. WHEN a target coordinate is extracted from a Warning_Post, THE Proximity_Engine SHALL query DynamoDB and return all Users whose Home_Location is within 500 meters of the target coordinate.
2. THE Proximity_Engine SHALL use the Haversine formula to compute the great-circle distance between two geographic coordinates.
3. WHEN no registered users are found within the Alert_Zone, THE Proximity_Engine SHALL log the result and take no further action.
4. THE Proximity_Engine SHALL complete the proximity query within 3 seconds for a user base of up to 100,000 registered users.
5. THE Proximity_Engine SHALL include users located exactly 500 meters from the target coordinate in the result set (inclusive boundary).

---

### Requirement 5: Emergency Alert Delivery

**User Story:** As a user in an affected area, I want to receive an immediate loud alert on my phone, so that I am notified of danger even when my phone is on silent.

#### Acceptance Criteria

1. WHEN the Proximity_Engine returns a non-empty set of Users, THE Notification_Service SHALL dispatch a Push_Notification to each identified user within 10 seconds.
2. THE Notification_Service SHALL configure Push_Notifications with the highest available priority flag (FCM: `priority: high`; APNs: `apns-priority: 10` with `interruption-level: critical`) to bypass silent and do-not-disturb modes.
3. THE Notification_Service SHALL include the warning message text and the target location name in the Push_Notification payload.
4. IF a Push_Notification delivery fails for a specific user, THEN THE Notification_Service SHALL retry delivery up to 3 times with exponential back-off before logging the failure.
5. WHEN all notifications for a Warning_Post have been dispatched, THE Notification_Service SHALL record the alert event in DynamoDB including the post ID, target coordinate, number of users notified, and dispatch timestamp.

---

### Requirement 6: Device Token Management

**User Story:** As a user, I want my device push token to stay current, so that I always receive alerts on my active device.

#### Acceptance Criteria

1. WHEN a user opens the Salmak app, THE Registration_Service SHALL accept an updated device push token and store it against the user's existing record.
2. WHEN the Notification_Service receives a token-invalid response from FCM or APNs for a specific user, THE Notification_Service SHALL mark that user's push token as invalid in DynamoDB and log the event.
3. WHEN a user's push token is marked invalid, THE Notification_Service SHALL exclude that user from future alert dispatches until a valid token is re-registered.

---

### Requirement 7: Project Skeleton Initialization

**User Story:** As a developer, I want a deployable project skeleton, so that I can begin implementing features against a working structure.

#### Acceptance Criteria

1. THE SAM template SHALL define all Lambda functions (Registration, X_Monitor, Location_Extractor, Proximity_Engine, Notification_Service) with Java 21 runtime.
2. THE SAM template SHALL define the DynamoDB tables required for user records, processed post IDs, and alert event logs with appropriate partition keys and billing mode set to PAY_PER_REQUEST.
3. THE project skeleton SHALL include a Maven `pom.xml` (or Gradle build file) with dependencies for AWS Lambda Java runtime, DynamoDB Enhanced Client, and AWS SDK v2.
4. THE project skeleton SHALL include a `samconfig.toml` with a default deployment configuration targeting the `ap-southeast-1` region (or a configurable region parameter).
5. WHEN `sam build && sam deploy` is executed against the skeleton, THE SAM template SHALL deploy without errors to the target AWS account.

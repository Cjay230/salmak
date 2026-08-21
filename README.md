# Salmak — Real-Time Geospatial Alert Platform

Salmak is a full-stack, serverless geospatial alert platform. It detects a user's proximity to flagged high-risk zones and fires real-time alerts, with a bilingual (Arabic / English) interface built for speed under stress.

## What it does

- **Proximity-based alerting** — continuously checks a user's location against flagged high-risk zones and raises an alert when they fall inside a danger radius.
- **Real-time status polling** — the frontend polls live alert status every 30 seconds and auto-navigates to a full-screen alert view the moment a zone is triggered, so a user never has to go looking for the warning.
- **Bilingual by default** — the entire interface is available in Arabic and English, with Arabic treated as a first-class language, not an afterthought.

## Architecture

Salmak is fully serverless, split into a Java backend and a Next.js frontend.

**Backend — serverless REST API**
- Java 21 on AWS Lambda, defined and deployed with AWS SAM
- Amazon DynamoDB for alert and zone data
- Amazon API Gateway exposing 4 REST endpoints
- 33 passing unit tests covering the core alert and proximity logic

**Frontend**
- Next.js, deployed on AWS Amplify
- Bilingual (Arabic / English) UI
- Polls live alert status every 30s and switches to the alert screen automatically when a zone is triggered

```
Next.js (Amplify)  ──HTTP──▶  API Gateway  ──▶  Lambda (Java 21)  ──▶  DynamoDB
        ▲                                                                  │
        └──────────────── polls alert status every 30s ───────────────────┘
```

## Tech stack

| Layer     | Technology                       |
|-----------|----------------------------------|
| Backend   | Java 21, AWS Lambda, AWS SAM     |
| Data      | Amazon DynamoDB                  |
| API       | Amazon API Gateway (4 endpoints) |
| Frontend  | Next.js, AWS Amplify             |
| Testing   | 33 unit tests (JUnit)            |

## Running it locally

You'll need the SAM CLI, Java 21, Maven, and Docker.

Build and run the API locally:
```
sam build
sam local start-api
```

Run the backend tests:
```
cd <backend-function-folder>
mvn test
```

Deploy to AWS:
```
sam build
sam deploy --guided
```
The API Gateway endpoint URL is printed in the deploy output.

## Project status

Actively developed (May 2026 – present).

## Author

Carla Jaffal — github.com/Cjay230

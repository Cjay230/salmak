# Salmak — Emergency Alert System

Salmak is a full-stack, serverless emergency-alert platform for civilians in
conflict zones. It detects a user's proximity to targeted areas and fires
real-time evacuation alerts, with a bilingual (Arabic / English) interface built
for speed under stress.

## What it does

- **Proximity-based alerting** — continuously checks a user's location against
  flagged threat zones and raises an evacuation alert when they fall inside a
  danger radius.
- **Real-time status polling** — the frontend polls live alert status every 30
  seconds and auto-navigates to a full-screen emergency view the moment a threat
  is detected, so a user never has to go looking for the warning.
- **Bilingual by default** — the entire interface is available in Arabic and
  English, with Arabic treated as a first-class language, not an afterthought.

## Architecture

Salmak is fully serverless, split into a Java backend and a Next.js frontend.

**Backend — serverless REST API**
- **Java 21** on **AWS Lambda**, defined and deployed with **AWS SAM**
- **Amazon DynamoDB** for alert and zone data
- **Amazon API Gateway** exposing **4 REST endpoints**
- **33 passing unit tests** covering the core alert and proximity logic

**Frontend**
- **Next.js**, deployed on **AWS Amplify**
- Bilingual (Arabic / English) UI
- Polls live alert status every 30s and switches to the emergency screen
  automatically on a detected threat

```
Next.js (Amplify)  ──HTTP──▶  API Gateway  ──▶  Lambda (Java 21)  ──▶  DynamoDB
        ▲                                                                  │
        └──────────────── polls alert status every 30s ───────────────────┘
```

## Tech stack

| Layer      | Technology                                    |
|------------|-----------------------------------------------|
| Backend    | Java 21, AWS Lambda, AWS SAM                  |
| Data       | Amazon DynamoDB                               |
| API        | Amazon API Gateway (4 endpoints)             |
| Frontend   | Next.js, AWS Amplify                          |
| Testing    | 33 unit tests (JUnit)                         |

## Running it locally

You'll need the [SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html),
Java 21, Maven, and Docker.

Build and run the API locally:

```bash
sam build
sam local start-api
```

Run the backend tests:

```bash
cd <backend-function-folder>
mvn test
```

Deploy to AWS:

```bash
sam build
sam deploy --guided
```

The API Gateway endpoint URL is printed in the deploy output.

## Project status

Actively developed (May 2026 – present).

## Author

Carla Jaffal — [github.com/Cjay230](https://github.com/Cjay230)

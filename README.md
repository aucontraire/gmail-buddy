# Gmail Buddy

A Spring Boot application that connects to a user’s Gmail account, enabling you to:

- List messages (all or limited to 50)
- Search for messages from a specific sender
- Delete messages from a specific sender (first trashing them, then permanently deleting)

This project demonstrates:
- **Spring Boot** (v3.4.0)
- **Spring Security OAuth2 Client** for Google sign-in
- **Gmail API** for reading, searching, trashing, and deleting messages

---

## Table of Contents
- [Prerequisites](#prerequisites)
- [Setup and Configuration](#setup-and-configuration)
    - [1. Create or Configure OAuth Credentials in Google Cloud Console](#1-create-or-configure-oauth-credentials-in-google-cloud-console)
    - [2. Update application.properties](#2-update-applicationproperties)
- [Running the Application](#running-the-application)
- [Endpoints](#endpoints)
- [Testing with OAuth2](#testing-with-oauth2)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

- Java 17+
- Maven or Gradle (Maven is used in this project)
- A Google account
- A Google Cloud Console project with the Gmail API enabled

---

## Setup and Configuration

### 1. Create or Configure OAuth Credentials in Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Navigate to **APIs & Services** > **Credentials**.
3. Under **OAuth 2.0 Client IDs**, either create a new client or select your existing one.
4. In **Authorized redirect URIs**, add:
[http://localhost:8020/login/oauth2/code/google](http://localhost:8020/login/oauth2/code/google)
5. Make sure **Gmail API** is enabled under **APIs & Services** > **Library**.

> **Important:**  
> If your app is in testing mode, add your Google account as a **Test User** under the **OAuth consent screen** configuration. You’ll only be able to sign in with test user accounts until the app is published/verified.

### 2. Update `application.properties`

In `src/main/resources/application.properties` (or your equivalent config):

```properties
spring.application.name=gmail-buddy
server.port=8020

# OAuth logs (for debugging)
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.security.oauth2.client=DEBUG
logging.level.com.aucontraire.gmailbuddy=DEBUG

# Security config
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true

# OAuth Client Credentials (replace with your own)
spring.security.oauth2.client.registration.google.client-id=YOUR_GOOGLE_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_GOOGLE_CLIENT_SECRET

# Scopes for Gmail (plus userinfo + openid)
spring.security.oauth2.client.registration.google.scope=
 email,
 profile,
 https://www.googleapis.com/auth/gmail.readonly,
 https://www.googleapis.com/auth/gmail.modify

# Redirect URI uses Spring Security’s default pattern
spring.security.oauth2.client.registration.google.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}

# Google OAuth Endpoints
spring.security.oauth2.client.provider.google.token-uri=https://oauth2.googleapis.com/token
spring.security.oauth2.client.provider.google.authorization-uri=https://accounts.google.com/o/oauth2/auth
```

## Running the Application

1. **Clone or download** this repo.  
2. Open a terminal in the project root.
3. Run:

   ```bash
   mvn spring-boot:run
   ```

or

   ```bash
   java -jar target/gmail-buddy-0.0.1-SNAPSHOT.jar
   ```
4. Access the app at: [http://localhost:8020](http://localhost:8020)

When you first visit http://localhost:8020, Spring Security will redirect you to Google to sign in. Once you approve the scopes, you’ll be redirected back to the application and see:

```plaintext
Welcome to your dashboard!

```


## Endpoints

All routes require you to be authenticated via Google OAuth2. The primary endpoints:

| HTTP Method    | Path                                               | Description                                                                                                |
|----------------|----------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| **GET**        | `/dashboard`                                       | A simple test route that shows "Welcome to your dashboard!" once authenticated.                            |
| **GET**        | `/api/v1/gmail/messages`                           | Lists **all** Gmail messages for the authenticated user.                                                   |
| **GET**        | `/api/v1/gmail/messages/latest`                    | Lists the **latest 50** Gmail messages for the authenticated user.                                         |
| **GET**        | `/api/v1/gmail/messages/from/{email}`              | Lists all messages from a **specific sender** (e.g. `@airbnb.com`).                                        |
| **DELETE**     | `/api/vi/gmail/messages/{messageId}`               | Delete message                                                                                             |
| **DELETE**     | `/api/v1/gmail/messages/from/{email}`              | Deletes all messages from a specific sender (moves them to trash, then permanently deletes).               |
| **POST**       | `/api/v1/gmail/messages/from/{email}/modifyLabels` | Modify labels for all messages from an email                                                               |
| **GET**        | `/api/v1/gmail/messages/{messageId}/body`          | Get message body from an email                                                                             |
| **PUT**        | `/api/vi/gmail/messages/{messageId}/read`          | Marks message as read                                                                                      |
| **GET**        | `/api/v1/gmail/debug/token`                        | (Development-only) Debug endpoint to return your current OAuth access token. **Do not use in production.** |


## Testing with OAuth2

1. **New Users:** If you’ve never signed in with this app, open [http://localhost:8020](http://localhost:8020). You’ll be redirected to Google to grant permissions.

2. **Returning Users:** If you have a stale token, you may need to revoke access:
    - Go to [myaccount.google.com/permissions](https://myaccount.google.com/permissions).
    - Remove the app’s access.
    - Sign in again with the new scopes.

**Testing DELETE Endpoints:**

- Browsers can’t issue DELETE requests from the address bar.
- Use a tool like **Postman**, **cURL**, or a REST client extension:
  ```bash
  curl -X DELETE http://localhost:8020/api/v1/gmail/messages/from/someone@example.com
    ```


## Troubleshooting

1. **`redirect_uri_mismatch`**
    - Ensure `http://localhost:8020/login/oauth2/code/google` is in your Google Cloud Console’s **Authorized redirect URIs**.
    - Double-check **application.properties** matches exactly.

2. **`ACCESS_TOKEN_SCOPE_INSUFFICIENT`**
    - Confirm `gmail.modify` is present both in your application code **and** the actual token (check with `GET https://oauth2.googleapis.com/tokeninfo?access_token=ACTUAL_TOKEN`).
    - Move messages to trash first, then permanently delete if the direct delete call fails.

3. **`403 Forbidden (Insufficient Permission)`**
    - Make sure you are using the correct Google account.
    - Revoke permission and re-consent with your app to ensure updated scopes.

4. **`NullPointerException` for `OAuth2AuthorizedClientService`**
    - Ensure you inject (`@Autowired`) `OAuth2AuthorizedClientService` in the correct constructor or field in your controllers/services.
    - Confirm `spring-boot-starter-oauth2-client` is in your dependencies.


## Contributing
Feel free to open issues or submit pull requests if you want to contribute improvements or fixes.

## License
This project is licensed under the MIT License. You are free to modify or distribute under the terms of this license.


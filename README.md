# Gmail Buddy

[![Build Status](https://img.shields.io/github/actions/workflow/status/aucontraire/gmail-buddy/ci.yml?branch=master)](https://github.com/aucontraire/gmail-buddy/actions)
[![Code Coverage](https://img.shields.io/badge/coverage-85%25-brightgreen)](./target/site/jacoco/index.html)
[![Java Version](https://img.shields.io/badge/Java-17+-blue)](https://openjdk.java.net/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A robust, enterprise-ready Spring Boot application for Gmail management with comprehensive email operations, advanced security, and modern architecture patterns.

## ✨ Features

### Core Functionality
- **📧 Email Management**: List, search, delete, and modify Gmail messages
- **🔍 Advanced Filtering**: Filter messages by sender, recipient, subject, and custom Gmail queries
- **🏷️ Label Operations**: Bulk label modification and management
- **📖 Message Content**: Extract and display email body content
- **📚 Bulk Operations**: Mass delete and label operations with validation

### Architecture & Security
- **🔐 OAuth2 Integration**: Secure Google authentication with proper scope management
- **✅ Input Validation**: Comprehensive validation framework with custom validators
- **⚡ Exception Handling**: Structured error responses with correlation IDs
- **⚙️ Configuration Management**: Centralized properties with environment-specific configs
- **🛡️ Security Hardening**: CORS, security headers, and protection against common vulnerabilities

### Developer Experience
- **🧪 High Test Coverage**: 85%+ test coverage with unit and integration tests
- **📋 Structured Logging**: JSON-formatted logs with correlation tracking
- **🔄 Retry Logic**: Intelligent retry mechanisms for Gmail API interactions
- **📊 Health Checks**: Application and dependency health monitoring

---

## 🚀 Quick Start

### Prerequisites

- **Java 17+** (OpenJDK recommended)
- **Maven 3.6+**
- **Google Cloud Project** with Gmail API enabled
- **OAuth2 Credentials** configured in Google Cloud Console

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/aucontraire/gmail-buddy.git
   cd gmail-buddy
   ```

2. **Configure OAuth2 Credentials**
   
   Create a `.env` file in the project root:
   ```bash
   cp .env.example .env
   ```
   
   Update the `.env` file with your Google OAuth credentials:
   ```env
   GOOGLE_CLIENT_ID=your_google_client_id_here
   GOOGLE_CLIENT_SECRET=your_google_client_secret_here
   ```

3. **Set up Google Cloud Console**
   - Enable the Gmail API in your Google Cloud project
   - Configure OAuth2 consent screen
   - Add `http://localhost:8020/login/oauth2/code/google` to authorized redirect URIs

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

5. **Access the application**
   
   Open [http://localhost:8020](http://localhost:8020) and authenticate with Google.

---

## 📡 API Endpoints

### Authentication
All endpoints require OAuth2 authentication via Google. The application will redirect unauthenticated users to Google's sign-in page.

### Core Endpoints

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| `GET` | `/dashboard` | Dashboard page (web interface) | - |
| `GET` | `/api/v1/gmail/messages` | List all messages | - |
| `GET` | `/api/v1/gmail/messages/latest` | List latest 50 messages | - |
| `POST` | `/api/v1/gmail/messages/filter` | Filter messages by criteria | `FilterCriteriaDTO` |
| `GET` | `/api/v1/gmail/messages/{id}/body` | Get message body content | - |
| `DELETE` | `/api/v1/gmail/messages/{id}` | Delete specific message | - |
| `PUT` | `/api/v1/gmail/messages/{id}/read` | Mark message as read | - |
| `DELETE` | `/api/v1/gmail/messages/filter` | Bulk delete by filter criteria | `FilterCriteriaDTO` |
| `POST` | `/api/v1/gmail/messages/filter/modifyLabels` | Bulk modify labels | `FilterCriteriaWithLabelsDTO` |

### Request/Response Examples

**Filter Messages:**
```bash
curl -X POST http://localhost:8020/api/v1/gmail/messages/filter \
  -H "Content-Type: application/json" \
  -d '{
    "from": "notifications@example.com",
    "subject": "Weekly Report",
    "maxResults": 10
  }'
```

**Modify Labels:**
```bash
curl -X POST http://localhost:8020/api/v1/gmail/messages/filter/modifyLabels \
  -H "Content-Type: application/json" \
  -d '{
    "from": "newsletter@example.com",
    "labelsToAdd": ["Newsletter", "Archive"],
    "labelsToRemove": ["INBOX"]
  }'
```

### Error Responses

All errors follow a consistent format with correlation IDs for tracking:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed for input parameters",
  "category": "CLIENT_ERROR",
  "correlationId": "abc123-def456-ghi789",
  "timestamp": "2024-01-15T10:30:00Z",
  "details": {
    "from": "Must be a valid email address",
    "subject": "Subject must not exceed 255 characters"
  }
}
```

---

## 🏗️ Architecture

### Project Structure

```
src/
├── main/java/com/aucontraire/gmailbuddy/
│   ├── config/          # Configuration classes and properties
│   ├── controller/      # REST controllers
│   ├── dto/            # Data Transfer Objects with validation
│   ├── exception/      # Exception hierarchy and handlers
│   ├── service/        # Business logic layer
│   └── validation/     # Custom validators
├── main/resources/
│   ├── application.properties    # Main configuration
│   └── application-{env}.properties  # Environment-specific configs
└── test/               # Comprehensive test suite
```

### Key Components

#### Exception Hierarchy
- **`GmailBuddyException`**: Base exception with correlation IDs
- **`ValidationException`**: Input validation errors (400)
- **`ResourceNotFoundException`**: Missing resources (404)
- **`GmailApiException`**: Gmail API integration errors (502)
- **`AuthenticationException`**: OAuth2 authentication failures (401)

#### Validation Framework
- **Custom Email Validator**: Validates email format patterns
- **Gmail Query Validator**: Sanitizes and validates Gmail search queries
- **Label Validation**: Enforces Gmail label naming constraints
- **Size Limits**: Prevents oversized requests and bulk operations

#### Configuration Management
Centralized configuration using `@ConfigurationProperties`:
- Gmail API settings (rate limits, retry policies)
- OAuth2 configuration
- Security settings (CORS, headers)
- Validation rules and patterns

---

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Required | Default |
|----------|-------------|----------|---------|
| `GOOGLE_CLIENT_ID` | Google OAuth2 Client ID | ✅ | - |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 Client Secret | ✅ | - |
| `SERVER_PORT` | Application port | ❌ | 8020 |
| `LOG_LEVEL` | Logging level | ❌ | INFO |

### Application Properties

Key configuration sections in `application.properties`:

```properties
# Gmail API Configuration
gmail-buddy.gmail-api.default-max-results=50
gmail-buddy.gmail-api.rate-limit.default-retry-seconds=60

# Security Configuration
gmail-buddy.security.cors.allowed-origins=http://localhost:3000
gmail-buddy.security.permit-all-patterns=/actuator/health,/api/v1/auth/**

# Validation Configuration
gmail-buddy.validation.email.pattern=^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$
gmail-buddy.validation.gmail-query.dangerous-pattern=<script>|javascript:|vbscript:
```

---

## 🧪 Testing

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=GmailServiceTest

# Run tests with coverage report
./mvnw test jacoco:report
```

### Test Coverage

The project maintains high test coverage across all layers:
- **Unit Tests**: Service logic, validation, and utilities
- **Integration Tests**: Controller endpoints and OAuth2 flow
- **Validation Tests**: Input validation and error scenarios

View coverage reports at `target/site/jacoco/index.html` after running tests.

### Test Configuration

The test suite includes:
- **Mock OAuth2 Authentication**: Simulates Google authentication
- **Test Configuration Properties**: Isolated test configurations
- **Custom Test Utilities**: Builders and factories for test data

---

## 🔒 Security

### OAuth2 Implementation
- **Secure Token Storage**: Encrypted token persistence
- **Scope Validation**: Minimal required Gmail permissions
- **Session Management**: Secure session handling with HttpOnly cookies

### Input Validation
- **XSS Prevention**: HTML/script tag filtering in queries
- **SQL Injection Protection**: Parameterized Gmail API queries
- **Rate Limiting**: Per-user request throttling

### Security Headers
- **CORS Configuration**: Controlled cross-origin access
- **Security Headers**: CSP, X-Frame-Options, X-Content-Type-Options
- **Cookie Security**: Secure, HttpOnly session cookies

---

## 🚀 Development

### Prerequisites for Development
- Java 17+ (OpenJDK recommended)
- Maven 3.6+
- IDE with Spring Boot support (IntelliJ IDEA, VS Code)
- Git

### Setting Up Development Environment

1. **Clone and setup**
   ```bash
   git clone https://github.com/aucontraire/gmail-buddy.git
   cd gmail-buddy
   cp .env.example .env
   # Update .env with your credentials
   ```

2. **Import into IDE**
   - Import as Maven project
   - Ensure Java 17+ is configured
   - Install Spring Boot plugins if needed

3. **Run in development mode**
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

### Code Quality

The project uses several tools to maintain code quality:

```bash
# Run linting and formatting
./mvnw spotless:apply

# Run security scan
./mvnw org.owasp:dependency-check-maven:check

# Run all quality checks
./mvnw verify
```

---

## 📚 Documentation

### API Documentation
When running the application, interactive API documentation is available at:
- **Swagger UI**: `http://localhost:8020/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8020/v3/api-docs`

### Project Documentation
- **[Project Plan](PROJECT_PLAN.md)**: Detailed development roadmap
- **[Configuration Guide](docs/configuration.md)**: Complete configuration reference
- **[Deployment Guide](docs/deployment.md)**: Production deployment instructions

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Workflow
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes with tests
4. Run the test suite (`./mvnw test`)
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

### Code Standards
- Follow existing code style and patterns
- Include unit tests for new functionality
- Update documentation for API changes
- Ensure all tests pass before submitting PR

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🆘 Support

### Common Issues

**OAuth2 Redirect URI Mismatch**
- Ensure `http://localhost:8020/login/oauth2/code/google` is added to Google Cloud Console
- Verify the redirect URI matches exactly (including port)

**Insufficient Gmail Permissions**
- Check that Gmail API is enabled in Google Cloud Console
- Verify OAuth2 scopes include `gmail.readonly` and `gmail.modify`
- Re-authenticate to refresh token permissions

**Test User Restrictions**
- Add your Google account as a test user in OAuth consent screen
- Ensure app is not in production mode unless verified

### Getting Help

- **Issues**: [GitHub Issues](https://github.com/aucontraire/gmail-buddy/issues)
- **Discussions**: [GitHub Discussions](https://github.com/aucontraire/gmail-buddy/discussions)

---

## 🚀 What's Next

Check out our [Project Plan](PROJECT_PLAN.md) for upcoming features:
- **Async Operations**: Background processing for bulk operations
- **Caching Layer**: Improved performance with intelligent caching
- **Advanced Monitoring**: Comprehensive observability and metrics
- **API Rate Limiting**: Enhanced rate limiting and quota management

---

<div align="center">

**[⭐ Star this repo](https://github.com/aucontraire/gmail-buddy)** if you find it useful!

Made with ❤️ by the Gmail Buddy Team

</div>

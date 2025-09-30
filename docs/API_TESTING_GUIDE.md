# Gmail Buddy API Testing Guide

This guide covers how to test Gmail Buddy's API endpoints using various tools and authentication methods.

## 🔐 Authentication Overview

Gmail Buddy supports **dual authentication modes**:

- **Browser Users**: OAuth2 flow with automatic redirect to Google
- **API Clients**: Bearer token authentication for programmatic access

## 🚀 Getting Bearer Tokens

### Method 1: Google OAuth2 Playground (Recommended)

The easiest way to get a Bearer token for API testing:

1. **Visit OAuth2 Playground**
   ```
   https://developers.google.com/oauthplayground/
   ```

2. **Configure Scopes**
   - In the left panel, find "Input your own scopes"
   - Enter: `https://mail.google.com/`
   - Click "Authorize APIs"

3. **Complete OAuth Flow**
   - Sign in with your Google account
   - Grant permissions when prompted
   - You'll be redirected back to the playground

4. **Exchange for Access Token**
   - Click "Exchange authorization code for tokens"
   - Copy the `access_token` value (starts with `ya29.`)

5. **Use the Token**
   ```bash
   # Example usage
   Authorization: Bearer ya29.a0ARrdaM8Hy8j4K...
   ```

### Method 2: Extract from Browser Session

If you're already logged into Gmail Buddy via browser:

1. **Login via Browser**
   ```
   http://localhost:8020
   ```

2. **Open Developer Tools**
   - Press F12 or right-click → Inspect
   - Go to Network tab

3. **Make Any API Request**
   - Navigate around the dashboard
   - Look for API calls in the Network tab

4. **Extract Bearer Token**
   - Find a request to `/api/v1/gmail/`
   - Copy the `Authorization: Bearer` header value

### Method 3: Using curl with OAuth2 (Advanced)

For automated scripts, you can implement the full OAuth2 flow programmatically. This requires setting up OAuth2 client credentials and handling the authorization code flow.

## 📧 Required Gmail Scopes

For full functionality, ensure your token has these scopes:

```
https://mail.google.com/
```

This scope provides:
- Read access to Gmail messages
- Modify access (labels, delete, archive)
- Full Gmail management capabilities

**Note**: Lesser scopes like `gmail.readonly` or `gmail.modify` will limit functionality.

## 🔧 Postman Setup

### Step 1: Create New Request

1. Open Postman
2. Create a new request
3. Set the method and URL (see endpoints below)

### Step 2: Configure Authentication

**Do NOT use Postman's OAuth2 configuration.** Instead:

1. Go to the **Headers** tab
2. Add a new header:
   ```
   Key: Authorization
   Value: Bearer ya29.a0ARrdaM... (your token here)
   ```
3. Add content type:
   ```
   Key: Content-Type
   Value: application/json
   ```

### Step 3: Test Connection

Start with a simple GET request:
```
GET http://localhost:8020/api/v1/gmail/messages/latest
```

Expected response: JSON array of Gmail messages.

## 📡 API Endpoints Reference

### Basic Operations

**List Messages**
```bash
GET http://localhost:8020/api/v1/gmail/messages
# Returns all messages (paginated)

GET http://localhost:8020/api/v1/gmail/messages/latest
# Returns latest 50 messages
```

**Get Message Content**
```bash
GET http://localhost:8020/api/v1/gmail/messages/{messageId}/body
# Returns message body content
```

**Mark as Read**
```bash
PUT http://localhost:8020/api/v1/gmail/messages/{messageId}/read
# Marks message as read
```

### Filtering Operations

**Filter Messages**
```bash
POST http://localhost:8020/api/v1/gmail/messages/filter
Content-Type: application/json

{
  "from": "sender@example.com",
  "subject": "Newsletter",
  "maxResults": 20,
  "query": "label:Inbox"
}
```

### Bulk Operations

**Bulk Delete (Use with Caution!)**
```bash
DELETE http://localhost:8020/api/v1/gmail/messages/filter
Content-Type: application/json

{
  "from": "newsletter@example.com",
  "query": "older_than:30d"
}
```

**Bulk Label Modification**
```bash
POST http://localhost:8020/api/v1/gmail/messages/filter/modifyLabels
Content-Type: application/json

{
  "from": "notifications@example.com",
  "labelsToAdd": ["Processed"],
  "labelsToRemove": ["INBOX"]
}
```

### Single Message Operations

**Delete Single Message**
```bash
DELETE http://localhost:8020/api/v1/gmail/messages/{messageId}
# Permanently deletes the message
```

## 🛠️ Testing with curl

### Basic Authentication Test
```bash
curl -X GET http://localhost:8020/api/v1/gmail/messages/latest \
  -H "Authorization: Bearer ya29.a0ARrdaM..." \
  -H "Content-Type: application/json"
```

### Filter Messages
```bash
curl -X POST http://localhost:8020/api/v1/gmail/messages/filter \
  -H "Authorization: Bearer ya29.a0ARrdaM..." \
  -H "Content-Type: application/json" \
  -d '{
    "from": "promotional@example.com",
    "maxResults": 10
  }'
```

### Bulk Delete (Gmail Capacity Management)
```bash
curl -X DELETE http://localhost:8020/api/v1/gmail/messages/filter \
  -H "Authorization: Bearer ya29.a0ARrdaM..." \
  -H "Content-Type: application/json" \
  -d '{
    "from": "oldnewsletter@example.com",
    "query": "older_than:1y"
  }'
```

## 🚨 Troubleshooting

### Common Issues

**401 Unauthorized**
- Check that your Bearer token is valid and not expired
- Verify the token has sufficient Gmail scopes
- Ensure the `Authorization` header is properly formatted

**403 Forbidden**
- Your token may lack necessary Gmail permissions
- Re-generate token with `https://mail.google.com/` scope

**404 Not Found**
- Verify the endpoint URL is correct
- Check that Gmail Buddy is running on port 8020

**500 Internal Server Error**
- Check application logs for detailed error information
- Verify Gmail API is enabled in your Google Cloud project

### Token Expiration

Bearer tokens typically expire after 1 hour. Symptoms:
- Requests that worked before suddenly return 401 errors
- Error message mentioning "invalid token" or "expired"

**Solution**: Generate a new token using the OAuth2 Playground method.

### Rate Limiting

Gmail API has rate limits. If you're making many requests:
- Add delays between requests (1-2 seconds)
- Use bulk operations instead of individual requests
- Monitor Gmail Buddy logs for rate limit warnings

## 🔄 Automation Scripts

### Bash Script for Bulk Operations
```bash
#!/bin/bash
TOKEN="ya29.a0ARrdaM..."  # Your Bearer token
BASE_URL="http://localhost:8020/api/v1/gmail"

# Function to delete emails from specific sender
delete_sender_emails() {
    local sender=$1
    echo "Deleting emails from: $sender"

    curl -X DELETE "$BASE_URL/messages/filter" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"from\": \"$sender\"}" \
        -w "HTTP Status: %{http_code}\n"

    sleep 2  # Rate limiting
}

# Example usage
delete_sender_emails "promotions@retailer.com"
delete_sender_emails "newsletter@company.com"
```

### Python Script Example
```python
import requests
import time

class GmailBuddyClient:
    def __init__(self, token):
        self.token = token
        self.base_url = "http://localhost:8020/api/v1/gmail"
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }

    def delete_by_sender(self, sender_email):
        """Delete all emails from a specific sender"""
        url = f"{self.base_url}/messages/filter"
        data = {"from": sender_email}

        response = requests.delete(url, json=data, headers=self.headers)
        return response.status_code, response.json()

    def bulk_delete_old_emails(self, days=365):
        """Delete emails older than specified days"""
        url = f"{self.base_url}/messages/filter"
        data = {"query": f"older_than:{days}d"}

        response = requests.delete(url, json=data, headers=self.headers)
        return response.status_code, response.json()

# Usage
client = GmailBuddyClient("ya29.a0ARrdaM...")
status, result = client.delete_by_sender("oldnewsletter@company.com")
print(f"Status: {status}, Result: {result}")
```

## 📊 Batch Deletion Strategy

Gmail Buddy limits bulk operations to **500 messages per request** for safety. For large cleanups:

### Strategy 1: Repeated API Calls
```bash
# Repeat until no more messages are found
for i in {1..20}; do
    echo "Batch $i"
    curl -X DELETE http://localhost:8020/api/v1/gmail/messages/filter \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d '{"from": "sender@example.com"}'
    sleep 3
done
```

### Strategy 2: Monitor Response Count
```python
def complete_delete_by_sender(client, sender):
    """Keep deleting until no more messages remain"""
    total_deleted = 0
    batch = 1

    while True:
        print(f"Processing batch {batch}...")
        status, result = client.delete_by_sender(sender)

        if status != 200 or result.get('deleted', 0) == 0:
            break

        deleted = result.get('deleted', 0)
        total_deleted += deleted
        print(f"Batch {batch}: {deleted} messages deleted")

        if deleted < 500:  # Last batch (fewer than max)
            break

        batch += 1
        time.sleep(2)  # Rate limiting

    print(f"Total deleted: {total_deleted} messages")
```

## 🔒 Security Best Practices

### Token Security
- **Never commit tokens** to version control
- **Use environment variables** for automation scripts
- **Rotate tokens regularly** (at least monthly)
- **Limit token scope** to minimum required permissions

### Testing Safety
- **Test with non-production accounts** when possible
- **Start with small batches** for bulk operations
- **Use specific filters** to avoid accidental mass deletion
- **Keep backups** of important emails before bulk operations

### Rate Limiting
- **Add delays** between requests (1-2 seconds minimum)
- **Monitor application logs** for rate limit warnings
- **Use bulk endpoints** instead of individual message operations
- **Implement exponential backoff** for production scripts

---

## 📞 Support

If you encounter issues:

1. **Check Gmail Buddy logs** for detailed error information
2. **Verify your Google Cloud Console** settings
3. **Test with OAuth2 Playground** to isolate token issues
4. **Review Gmail API quotas** in Google Cloud Console

For additional help, refer to the main [README.md](README.md) or open an issue in the project repository.
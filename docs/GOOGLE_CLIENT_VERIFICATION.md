# 🔍 Google OAuth Client Verification Guide

## Error: "OAuth client was not found" (Error 401: invalid_client)

This error means Google cannot find your OAuth2 client ID. Here's how to fix it:

## Step 1: Verify Your Google Cloud Project Setup

### Check Current Project
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. **Look at the project selector** (top left, next to "Google Cloud")
3. **Verify you're in the right project** where you created Gmail Buddy credentials

### If Wrong Project
- Click project selector → Find the correct project → Switch to it

## Step 2: Verify OAuth2 Client Exists

### Navigate to Credentials
1. In Google Cloud Console, go to **APIs & Services → Credentials**
2. Look for **"OAuth 2.0 Client IDs"** section
3. You should see at least one client listed

### If No OAuth Client Exists
You need to create one:
1. Click **"+ CREATE CREDENTIALS"**
2. Select **"OAuth client ID"**
3. Choose **"Web application"**
4. Name it (e.g., "Gmail Buddy")
5. Add **Authorized redirect URIs**:
   ```
   http://localhost:8020/login/oauth2/code/google
   https://oauth.pstmn.io/v1/callback
   https://oauth.pstmn.io/v1/browser-callback
   ```
6. Add **Authorized JavaScript origins**:
   ```
   https://oauth.pstmn.io
   ```
7. Click **"CREATE"**

## Step 3: Get the Correct Credentials

### Copy Client ID and Secret
1. Find your OAuth client in the list
2. Click the **download icon** (⬇️) or **edit icon** (✏️)
3. Copy the **exact** Client ID and Client Secret

### Verify Format
**Client ID should look like**:
```
123456789012-abcdefghijklmnopqrstuvwxyz123456.apps.googleusercontent.com
```

**Client Secret should look like**:
```
GOCSPX-AbCdEfGhIjKlMnOpQrStUvWx
```

## Step 4: Update Your Configuration

### Option A: Update .env File
1. Edit your `.env` file in project root
2. Set these values:
   ```
   GOOGLE_CLIENT_ID=your-actual-client-id-here
   GOOGLE_CLIENT_SECRET=your-actual-client-secret-here
   ```

### Option B: Update Postman Variables
1. In Postman Collection → Variables tab
2. Set:
   - `GOOGLE_CLIENT_ID`: Your actual Client ID
   - `GOOGLE_CLIENT_SECRET`: Your actual Client Secret

## Step 5: Verify Gmail API is Enabled

### Check APIs
1. In Google Cloud Console, go to **APIs & Services → Enabled APIs**
2. Look for **Gmail API**
3. If not found, go to **APIs & Services → Library**
4. Search for "Gmail API" and enable it

## Step 6: Test Configuration

### Test with Browser Method
1. Start your app: `./mvnw spring-boot:run`
2. Go to: `http://localhost:8020/login`
3. Should redirect to Google login (not error page)

### If Still Getting Errors
Check the application logs for:
```
CLIENT_ID: your-actual-client-id-value
CLIENT_SECRET: GOCSPX-***
```

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| Wrong project selected | Switch to correct Google Cloud project |
| No OAuth client exists | Create new OAuth 2.0 client ID |
| Wrong client ID format | Verify it ends with `.apps.googleusercontent.com` |
| Gmail API not enabled | Enable Gmail API in APIs & Services |
| Old/deleted credentials | Create new OAuth client |

## Step 7: Verify Environment Variables

Create this test to verify your app loads the right credentials:

### Quick Test Script
```bash
# Start your app with debug info
export GOOGLE_CLIENT_ID="your-actual-client-id"
export GOOGLE_CLIENT_SECRET="your-actual-secret"
./mvnw spring-boot:run

# Look for these lines in the logs:
# "OAuth2 client registration for 'google' configured"
# "OAuth2 client ID: 123456789012-abc...googleusercontent.com"
```

## Debug Checklist

- [ ] Correct Google Cloud project selected
- [ ] OAuth 2.0 client ID exists in credentials
- [ ] Client ID format is correct (ends with .apps.googleusercontent.com)
- [ ] Client secret format is correct (starts with GOCSPX-)
- [ ] Gmail API is enabled
- [ ] Redirect URIs are configured correctly
- [ ] .env file has actual values (not placeholders)
- [ ] Postman variables have actual values (not placeholders)

## Success Indicators

✅ **Working correctly**:
- Google login page opens (not error page)
- App logs show correct client ID format
- Can complete OAuth flow without "client not found" error

❌ **Still broken**:
- "OAuth client was not found" error
- "invalid_client" error
- App logs show placeholder values

If you're still getting the error after these steps, the issue is likely that you're using credentials from the wrong Google Cloud project or the OAuth client was deleted.
# SMS Configuration-Based Forwarding - Implementation Summary

## Overview
This document explains how the SMS forwarding system works and the recent changes made to ensure SMS messages are only sent to user-configured servers.

## How It Works Now

### 1. **Configuration-Based Routing** ✅
When an SMS arrives, the system:
1. Saves the SMS to the history database
2. Retrieves **all active configurations** from the database
3. For each active configuration:
   - Checks if the SIM card matches (SIM 1, SIM 2, or Both)
   - Checks if the sender is in the whitelist (if configured)
   - Sends the SMS to the **configured server URL** (not hardcoded!)

### 2. **Server Types**
Configurations support two server types:
- **Type 1 (Autov)**: Uses the Autov server with authentication token
  - URL: `Const.AUTOV_SMS_UPLOAD` (currently `http://192.168.100.205:8001/api/method/autov.api.sms_upload`)
  - Requires: Authentication token
  
- **Type 3 (Other)**: Uses a custom server URL
  - URL: User-defined (e.g., `http://example.com/api/sms`)
  - No authentication token required

### 3. **No Configuration Alert** ✅ NEW!
When an SMS arrives and there are **no active configurations** that match:
- A **notification** is displayed to the user
- The notification shows:
  - Title: "No Configuration for SMS"
  - Content: Sender number and message preview
  - Action: Tap to open Dashboard and create a configuration
- The SMS is still saved to history but **NOT forwarded** anywhere

### 4. **Configuration Matching Logic**
An SMS is sent to a configuration if:
1. ✅ The configuration is **active** (toggle is ON)
2. ✅ The **SIM card matches**:
   - Config SIM = 0 (Both) → Accepts all SIM cards
   - Config SIM = 1 (SIM 1) → Only SIM 1 messages
   - Config SIM = 2 (SIM 2) → Only SIM 2 messages
3. ✅ The **sender is whitelisted** (if whitelist is configured):
   - Empty whitelist → Accepts all senders
   - Non-empty whitelist → Only accepts listed senders

## Code Changes Made

### File: `SmsReceiver.java`

#### Change 1: Added Notification Call
```java
if (matchCount == 0) {
    Log.d(TAG, "No active config matched for SMS from " + fromNumber);
    showNoConfigurationNotification(context, fromNumber, body); // NEW
}
```

#### Change 2: Added Notification Method
```java
private void showNoConfigurationNotification(Context context, String fromNumber, String body) {
    // Creates a system notification when no configuration matches
    // Tapping the notification opens DashboardActivity to create a config
}
```

#### Change 3: Added Import
```java
import android.os.Build; // For Android version checking
```

## Important Notes

### ⚠️ Hardcoded Server in `Const.java`
The constant `AUTOVSERVER` in `Const.java` (line 21) is currently:
```java
public static final String AUTOVSERVER = "http://192.168.100.205:8001";
```

**This is ONLY used when**:
- A configuration has **Server Type = 1 (Autov)**
- It's NOT a global default for all SMS

**To change this**:
- Update line 21 in `Const.java` to your production server
- Or create configurations with **Server Type = 3 (Other)** for custom URLs

### ✅ Multiple Configurations
The system supports **multiple active configurations**:
- One SMS can be sent to **multiple servers** if multiple configs match
- Example: 
  - Config A: SIM 1, Autov Server, Whitelist: "192"
  - Config B: SIM 1, Custom Server, No whitelist
  - Result: SMS from "192" on SIM 1 → Sent to BOTH servers

### 📊 SMS History
All incoming SMS are saved to the database with:
- Status: PENDING (0), SENT (1), or FAILED (2)
- Response data from the server
- Request URL used
- SIM card information

## Testing Checklist

- [ ] Send SMS with **no configurations** → Should show notification
- [ ] Send SMS with **inactive configuration** → Should show notification
- [ ] Send SMS with **active configuration** → Should forward to configured server
- [ ] Send SMS from **whitelisted sender** → Should forward
- [ ] Send SMS from **non-whitelisted sender** → Should NOT forward (show notification)
- [ ] Send SMS to **wrong SIM** → Should NOT forward (show notification)
- [ ] Check SMS History → All SMS should be saved regardless of forwarding

## User Guide

### Creating a Configuration
1. Open the app → Dashboard
2. Tap the **+** button
3. Follow the wizard:
   - **Step 1**: Enter configuration name
   - **Step 2**: Select SIM card (SIM 1, SIM 2, or Both)
   - **Step 3**: Choose server type and enter credentials
   - **Step 4**: (Optional) Enter whitelisted sender numbers

### Managing Configurations
- **Toggle ON/OFF**: Use the switch on each configuration
- **Edit**: Tap the 3-dot menu → Edit
- **Delete**: Tap the 3-dot menu → Delete

### Viewing SMS History
- Open the app → SMS History (from menu)
- Filter by date
- See status: Pending, Sent, or Failed
- Tap on SMS to see server response details

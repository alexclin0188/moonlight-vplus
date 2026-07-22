# MoonLink Privacy Policy

> **[中文版本](PRIVACY_POLICY.md)** — 查看中文版隐私政策

## Overview

This privacy policy applies to MoonLink application (hereinafter referred to as "the App"). The App is developed based on the [Moonlight](https://moonlight-stream.org/) project.

> **Note**: You can review [Moonlight's official privacy policy](https://moonlight-stream.org/privacy.html) for more information.

## Types of Data Collected

### Crash Reports

#### Firebase Crashlytics

The App integrates Firebase Crashlytics to collect crash reports for improving stability. When a crash occurs, the following information may be collected:

- **Device information**: Device manufacturer, model, Android version, OS version
- **Crash information**: Crash type, stack trace, app version
- **Usage state**: App's runtime state at the time of the crash

> **Note**: If the device lacks Google Mobile Services (GMS), Firebase Crashlytics will not initialize and no crash data will be collected.

#### Local Crash Reports

The App also includes a built-in local crash reporting mechanism. When a crash occurs, a crash log is written to the device's local storage directory, containing:
- Device model, Android version, system architecture
- App version and build type
- Crash time and stack trace

This data is **stored locally on the device only** and is never automatically uploaded. Users can manually share crash logs with the developer from the Help page.

### Usage Statistics

The App integrates Firebase Analytics to collect anonymous usage statistics, including:

#### App Usage
- **Launch count**: Records how many times the App is launched
- **Session duration**: Records the duration of each app session
- **Session information**: Records when the App enters foreground or background

#### Game Streaming
- **Streaming duration**: Records the duration of each game streaming session
- **PC name**: Records the connected computer name (for statistics only, no personal identity information)
- **App name**: Records the launched game application name (for statistics only, no personal identity information)

#### Device Information
- **Basic device info**: Device manufacturer, model, Android version (for compatibility analysis)
- **Processor info**: Processor manufacturer and model (for performance optimization)
- **Hardware info**: CPU cores, memory size, GPU type (for performance analysis)
- **Network info**: Network type and connection status (for network optimization)

### Data Anonymization

All collected statistics are completely anonymous:
- No personal identity information (name, email, phone number, etc.)
- No device unique identifiers
- No user behavior tracking
- Data is used for statistical analysis only

## Network Requests

### Version Update Check

The App automatically checks for new versions on startup, once every 4 hours. This request is only used to retrieve version information and **does not contain any personal identity information**. Users can also manually check for updates in Settings.

### STUN Service

When the user enables the "Obtain public IP" option (disabled by default), the App sends a request to a STUN server to obtain the device's public IP address. This feature is used for internet streaming scenarios to ensure remote streaming works properly. It can be enabled or disabled at any time in Connection Settings.

### Background Image Loading

Users can choose a background image source (Settings → UI Settings → Background Source), including built-in image APIs or a custom API URL. These requests are triggered by the user's explicit choice and are only used to load background images.

## Third-Party Services

### Firebase Analytics & Crashlytics
- **Service Provider**: Google LLC
- **Purpose**: App usage statistics and crash reporting
- **Data Storage**: Data is stored on Google's servers and may be transferred to countries such as the United States
- **Privacy Policy**: [Firebase Privacy Policy](https://firebase.google.com/support/privacy)

## Data Sharing Statement

The App **will not sell or share your data with third parties**, except in the following circumstances:
- Firebase/Google as necessary cloud service providers for crash reporting and analytics
- When required by law or requested by government authorities in accordance with legal procedures

## Device Permissions

### Microphone (RECORD_AUDIO)

The App provides a **microphone redirect** feature, allowing users to transmit device microphone audio to their host computer in real-time for in-game voice chat and communication.

- **Purpose**: Voice communication during game streaming only (requires manual enablement in Settings)
- **Data Processing**: Microphone audio is encrypted and **sent directly to the user's own host computer**, without passing through any third-party server
- **Data Storage**: Microphone audio data is **not stored** anywhere, transmitted as a real-time stream only
- **User Control**: This feature is disabled by default; users can enable or disable it in Settings, and toggle it via the microphone button during streaming
- **Requirement**: Requires host to run Sunshine-Foundation 2025.0720 or later

## Data Usage

Collected data is used only for:
- Understanding app usage patterns to continuously improve user experience
- Optimizing performance and identifying/fixing crashes
- Identifying the most popular features to guide development

## Data Protection

### Data Security
- All data transmission uses encryption protocols
- Stored data is protected by cloud service provider security measures
- Security measures are regularly reviewed

### Data Retention
- Analytics data retention is controlled by Firebase (default 26 months); users can manage and delete data via [Google Settings](https://myaccount.google.com/)
- Local crash logs retain the most recent 10 records; older ones are automatically cleaned up
- Local crash logs are deleted when the App is uninstalled

## User Rights

### Data Control
- Users can restrict the App's data collection permissions through Android system settings
- Users can opt out of analytics in the App's Settings
- Users can request deletion of their data

### Transparency
- This privacy policy publicly describes all data collection activities
- The App is based on an open-source project; the complete source code is available on [GitHub](https://github.com/alexclin0188/moonlink-android) for community review of data collection-related code
- Users can review this privacy policy at any time
- Users will be notified of any material changes

## Children's Privacy

The App is not designed for children under 13 and does not knowingly collect personal information from children under 13. If such information is discovered, it will be promptly deleted.

## International Data Transfer

Firebase services may transfer data to countries such as the United States for processing. We ensure all data transfers comply with applicable data protection laws.

## Privacy Policy Updates

We may update this privacy policy from time to time. Material changes will be communicated via:
- In-app notifications
- Release notes on the app store
- Updates posted on this page

## Contact Us

If you have any questions or suggestions regarding this privacy policy, please contact us through:

- **GitHub Issues**: [Project Issues Page](https://github.com/alexclin0188/moonlink-android/issues)

## Related Links

- **Moonlight Official Privacy Policy**: [https://moonlight-stream.org/privacy.html](https://moonlight-stream.org/privacy.html)
- **Firebase Privacy Policy**: [https://firebase.google.com/support/privacy](https://firebase.google.com/support/privacy)

## Legal Notice

This privacy policy is governed by the laws of the People's Republic of China. Any disputes shall be resolved through friendly negotiation.

---

**Last Updated**: July 22, 2026

**Version**: 1.2.0
# ProtocolTracker privacy policy

Last updated: 4 October 2026

ProtocolTracker is an Android app for logging doses, blood pressure, symptoms and lab results. It works fully offline: it doesn't ask for Android's internet permission, so it cannot send anything anywhere. There is no account, no ads, no analytics and no crash reporting. ApolloF receives no data from the app.

The same text is published at https://apps.apollof.nl/protocoltracker/privacy/.

## Who is responsible

ProtocolTracker is made and published by ApolloF, in the Netherlands. Contact: me@apollof.nl. Because the app never sends your data to ApolloF, ApolloF doesn't hold or process it; it stays on your phone and in files you export yourself. If you email ApolloF, ApolloF is the controller of that email (see "Your rights").

## What you log stays on your phone

- Your plans, doses, journal entries, blood pressure, symptoms, lab results and settings are stored in the app's private storage on your phone. Other apps can't read it. The app doesn't add its own encryption, so the data is protected by your phone's screen lock and Android's device encryption.
- Android's cloud backup and device-to-device transfer are turned off for this data, so it isn't copied to Google or to a new phone, and the app doesn't allow Android backups through other tools. Use the app's own backup file to move it.
- Reminders and the home-screen widget are handled on the phone. Reminder notifications and the widget show the names and doses of your scheduled items, so they can be visible on the lock screen or home screen depending on your Android notification and lock-screen settings.

## Permissions

- **Notifications, exact alarms and vibration:** for dose reminders at the times you set.
- **Run at startup:** to set your reminders again after the phone restarts or the app updates.
- **Keep awake, foreground service and view network connections:** added by WorkManager, the Android library the app uses to re-check reminders in the background about every 12 hours. The network permission only shows whether the phone is online; without the internet permission the app can't use the connection.
- No internet, location, contacts, camera, microphone or storage permission.

## Files you export

The JSON backup (which includes your app settings) and the reports (HTML, and Markdown for AI tools) are saved only where you choose in Android's file picker. Backup and import files are read only from the file you pick. These files are **not encrypted** and contain your health data, so keep them somewhere private. If you save them to a cloud folder, that service's privacy terms apply.

## Chatbot lab import and reports for AI tools

To import lab results, the app gives you a prompt to copy into a chatbot of your choice together with your lab report, and you paste the answer back. The prompt contains none of your data. The app copies it to Android's clipboard when you tap *Copy AI prompt* and reads the clipboard only when you tap *Paste answer*; other apps may be able to read the clipboard while it holds that text. The app itself sends nothing: whatever you paste into a chatbot, or upload a report to, goes to that service under its own privacy terms. Nothing is saved in the app before you tap Save.

## No selling, no sharing

ProtocolTracker collects no data, so there is nothing to sell or share. Your data is never sold, shared with advertisers or data brokers, or used for advertising.

## How long data is kept

Your data stays on your phone until you delete it. ApolloF keeps nothing, because nothing is received.

## Deleting your data

- Delete single entries in the app, or clear everything with *Android Settings → Apps → ProtocolTracker → Storage → Clear storage*.
- Uninstalling the app deletes all its data from the phone.
- Backup and report files you exported are yours to delete where you saved them.

## Your rights

Under the GDPR you can ask for access to, correction or deletion of personal data about you, restrict or object to its use, and get a copy of it. Since ProtocolTracker sends nothing to ApolloF, the only personal data ApolloF may hold is an email you send; it is used only to answer you and deleted when it is no longer needed. Write to me@apollof.nl. You can also complain to the Dutch data protection authority, the Autoriteit Persoonsgegevens (https://autoriteitpersoonsgegevens.nl/), or the authority where you live.

## Children

ProtocolTracker is meant for adults (18+) and isn't directed at children. It doesn't knowingly collect data from anyone.

## Changes

If this policy changes, the new version is published with a new date at the top.

## Contact

Questions about privacy: me@apollof.nl.

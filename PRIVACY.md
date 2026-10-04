# Privacy policy

Effective 4 October 2026. This policy covers the ProtocolTracker Android app.

## Summary
ProtocolTracker works offline. It collects no data and sends nothing anywhere: everything you enter stays on your
device. The developer never receives your data.

## What the app stores, and where
- Your plan, logged doses, journal entries (blood pressure, notes, symptoms, bloodwork) and settings are stored in the
  app's private storage on your phone. Other apps cannot read it.
- Nothing is sent over the network. The app does not request the `INTERNET` permission, has no account, and has no
  analytics, ads, crash reporting or tracking.
- Android cloud backup and device-to-device transfer are turned off for the app (`allowBackup="false"` and
  `data_extraction_rules.xml`). If you uninstall the app or lose the phone, the data is gone unless you saved a backup.
- The app uses notifications for dose reminders and runs on boot only to set those reminders again.

## Files you export
- JSON backups and the HTML and Markdown reports are saved where you choose through the system file picker, for example
  in Files or a cloud drive.
- These files are **not encrypted** and contain your health data. You are responsible for where you keep them and who
  you share them with. Once a file leaves the app, it is outside this policy.

## Chatbots
- The bloodwork import gives you a prompt to paste into a chatbot of your choice, together with your lab report. The
  "report for AI tools" (Markdown) is meant for the same use.
- The app never sends anything itself. Data reaches the chatbot only when you paste it there, and from then on that
  service's terms and privacy policy apply.

## Not a medical device
ProtocolTracker is a personal log. It does not recommend, prescribe or adjust doses, and it does not diagnose or
interpret results. Level curves are model estimates. It is not a medical device. Talk to a doctor about medicines and
lab results.

## Changes and questions
Changes to this policy are published in this file in the
[ProtocolTracker repository](https://github.com/ApolloF/ProtocolTracker). For questions, open an issue there.

# Remindly — Google Play Publishing Checklist

## 1. Google Play Console account

- [ ] Create account at https://play.google.com/console
- [ ] Pay one-time $25 registration fee
- [ ] Fill in developer profile (name, email, address)

---

## 2. App signing — release keystore

You already have `keystore.properties` and the signing config in `build.gradle.kts`.

```bash
# Build signed release AAB
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

**Important:** Keep your `.jks` keystore file safe.
If you lose it, you can never update the app.
Back it up to cloud storage (encrypted).

---

## 3. Play Billing — create subscription SKU

1. In Play Console → your app → **Monetize → Subscriptions**
2. Create product:
   - Product ID: `remindly_pro_yearly`
   - Name: "Remindly Pro"
   - Description: "Unlimited reminders, Google Calendar sync, location manager"
   - Price: $1.99/year
   - Free trial: 7 days
3. Publish the subscription (must be in Active state before billing works)

Once the SKU exists in Play Console, remove the mock subscribe flow
and uncomment `BillingManager.launchBillingFlow(activity)` calls.

---

## 4. Graphics assets (you must create these)

| Asset | Size | Where |
|---|---|---|
| App icon | 512×512 px PNG | Play Console → Store listing |
| Feature graphic | 1024×500 px PNG | Play Console → Store listing |
| Screenshots (phone) | Min 2, max 8 | At least 1080×1920 px |
| Screenshots (tablet) | Optional | 1200×1920 or larger |

**Tools:**
- Icon: https://icon.kitchen (free, generates adaptive icon + PNG)
- Feature graphic: Figma / Canva (dark background, app screenshot, tagline)
- Screenshots: take real screenshots on your device via `adb shell screencap`

---

## 5. Store listing — content to fill in Play Console

All texts are already written in `store-listing/` folder.

| Language | Short description | Full description |
|---|---|---|
| Ukrainian | `uk/short-description.md` | `uk/full-description.md` |
| English | `en/short-description.md` | `en/full-description.md` |
| Spanish | `es/short-description.md` | `es/full-description.md` |
| German | `de/short-description.md` | `de/full-description.md` |

Set **Ukrainian** as the default language (your primary audience).

---

## 6. Content rating

Play Console → **Policy → App content → Content rating**
- Complete the questionnaire (reminders app — straightforward)
- Expected rating: **Everyone**

---

## 7. Target audience & permissions

- Target age: Everyone (13+)
- Declare permissions in Play Console:
  - `POST_NOTIFICATIONS` — reminder notifications
  - `USE_EXACT_ALARM` — scheduled alarms
  - `ACCESS_FINE_LOCATION` — geofence reminders
  - `READ_CALENDAR / WRITE_CALENDAR` — Google Calendar export
  - `RECORD_AUDIO` — voice input (microphone)

---

## 8. Privacy policy

Google requires a Privacy Policy URL for apps that:
- Use microphone
- Access location
- Sync with Google Calendar

**Option A — Free:** Create a page on https://www.freeprivacypolicy.com
**Option B — Simple:** Put a `privacy-policy.md` on GitHub Pages

Minimum content:
- What data is collected (none sent to third parties)
- How microphone is used (only for voice input, not stored)
- How location is used (only for local geofence, not shared)

---

## 9. App release flow

```
Play Console
  └── Release → Production
        └── Create new release
              ├── Upload: app-release.aab
              ├── Release name: 1.0
              └── Release notes (per language):
                    uk: "Перший реліз Remindly — голосові нагадування"
                    en: "First release of Remindly — voice reminders"
```

**Tip:** First release goes through **review** (1–3 days for new accounts).
After approval, subsequent releases are typically faster (hours).

---

## 10. Before submitting — checklist

- [ ] Keystore backed up securely
- [ ] Release AAB built and tested on real device
- [ ] App icon 512×512 uploaded
- [ ] Feature graphic 1024×500 uploaded
- [ ] At least 2 phone screenshots uploaded
- [ ] Short description filled (all 4 languages)
- [ ] Full description filled (all 4 languages)
- [ ] Privacy Policy URL set
- [ ] Content rating completed
- [ ] Subscription SKU `remindly_pro_yearly` created and active in Play Console
- [ ] Billing integration switched from mock to real (`BillingManager.launchBillingFlow`)
- [ ] App tested: trial → paywall → subscribe flow works end-to-end

---

## 11. Switching from mock billing to real billing

In `VoiceActivity.kt` and `MainActivity.kt`, the current subscribe buttons call:
```kotlin
ProState.setMockState(this, ProState.MOCK_PRO)
```

Replace these with:
```kotlin
BillingManager.launchBillingFlow(this)
```

The `BillingManager` is already fully implemented and ready.
It handles purchase acknowledgement and `isSubscribed` state automatically.

---

## 12. After publishing

- Monitor **Ratings & Reviews** — respond to reviews (boosts ranking)
- Check **Android Vitals** — crash rate, ANR rate
- Add Firebase Crashlytics for crash reporting (optional but recommended)
- First subscription revenue appears in Play Console → **Payments** after 30 days

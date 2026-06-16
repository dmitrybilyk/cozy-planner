SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:55432/linkease_db?stringtype=unspecified" \
DB_USER=user DB_PASSWORD=password \
APP_OWNER_EMAIL=dmitry.bilyk@gmail.com \
TELEGRAM_ENABLED=false \
./gradlew :server:bootRun




# Linkease

Kotlin Multiplatform app (Android + Web) built with Compose Multiplatform.

## Run in browser (dev)

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

Opens automatically at `http://localhost:8080`.

## Build & install on Android

```bash
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

---

## Connect Android phone via Wi-Fi (ADB)

### 1. Enable Developer Mode (Xiaomi / MIUI / HyperOS)

1. Open **Налаштування** (Settings)
2. Tap **Про телефон** (About phone) at the very top
3. Find **Версія MIUI** (MIUI version) or **Версія ОС** (OS version)
4. Tap it **7 times in a row** — you'll see _"Ви стали розробником"_ (You are now a developer)

> **Xiaomi tip:** You may need to be logged into your Mi Account for developer settings to fully activate.

---

### 2. Access Developer Options

1. Go back to **Налаштування** (Settings)
2. Scroll down → tap **Розширені налаштування** (Additional settings)
3. Tap **Для розробників** (Developer options)

---

### 3. Enable Wireless Debugging

Inside **Для розробників**, scroll to the **Налагодження** (Debugging) section and enable:

- **Налагодження через USB** (USB debugging)
- **Бездротове налагодження** (Wireless debugging)

> Tapping the **text** "Бездротове налагодження" (not the toggle) opens a submenu with pairing options.

Make sure your phone and computer are on the **same Wi-Fi network**.

---

### 4. Pair the device (first time only)

1. In the **Бездротове налагодження** submenu, tap **Сполучити пристрій за допомогою коду** (Pair device with pairing code)
2. The phone shows an IP:PORT and a 6-digit code
3. On your computer:

```bash
adb pair <IP>:<PORT>
# enter the 6-digit code when prompted
```

---

### 5. Connect

After pairing, the **Бездротове налагодження** main screen shows a separate connection IP:PORT.

```bash
adb connect <IP>:<PORT>
```

Verify connection:

```bash
adb devices
```

You should see your device listed. Now you can install APKs wirelessly.

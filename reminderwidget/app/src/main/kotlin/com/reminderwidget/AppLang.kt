package com.reminderwidget

object AppLang {
    var code: String = "uk"

    // ── App bar ──────────────────────────────────────────────────────────────
    val appTitle get() = t("🎙  Remindly", "🎙  Remindly", "🎙  Remindly", "🎙  Remindly")
    val helpBtn  get() = "?"
    val langBtn  get() = when (code) { "en" -> "🇬🇧"; "es" -> "🇪🇸"; "de" -> "🇩🇪"; else -> "🇺🇦" }

    // ── Bottom nav ───────────────────────────────────────────────────────────
    val navEvents    get() = t("Події",     "Events",    "Eventos",  "Ereignisse")
    val navLocations get() = t("Локації",   "Locations", "Lugares",  "Orte")
    val navSettings  get() = t("Налаш.",    "Settings",  "Ajustes",  "Einst.")

    // ── Events sub-tabs ──────────────────────────────────────────────────────
    val tabPlans    get() = t("📅 Плани",    "📅 Plans",    "📅 Planes",    "📅 Pläne")
    val tabNoTime   get() = t("📝 Без часу", "📝 No time",  "📝 Sin hora",  "📝 Ohne Zeit")
    val tabFavs     get() = t("❤️ Обрані",  "❤️ Favorites","❤️ Favoritos","❤️ Favoriten")

    // ── List section headers ─────────────────────────────────────────────────
    val sectionDone   get() = t("✅ Виконані",    "✅ Done",       "✅ Completado",  "✅ Erledigt")
    val sectionNoTime get() = t("📍 Без часу",    "📍 No time",    "📍 Sin hora",    "📍 Ohne Zeit")
    val headerToday   get() = t("Сьогодні",       "Today",         "Hoy",            "Heute")
    val headerTomorrow get()= t("Завтра",          "Tomorrow",      "Mañana",         "Morgen")

    // ── Event card buttons ───────────────────────────────────────────────────
    val btnDelete     get() = t("Видалити",        "Delete",        "Eliminar",       "Löschen")
    val btnFavAdd     get() = t("В обране",        "Favorite",      "Favorito",       "Favorit")
    val btnFavRemove  get() = t("З обраних",       "Unfavorite",    "Quitar fav.",    "Entfernen")
    val btnPin        get() = t("Закріпити",       "Pin",           "Fijar",          "Anheften")
    val btnUnpin      get() = t("Відкріпити",      "Unpin",         "Desfijar",       "Lösen")
    val btnShare      get() = t("Надіслати",       "Share",         "Compartir",      "Teilen")
    val btnCalAdd     get() = t("До календаря",    "To Calendar",   "Al calendario",  "Zum Kalender")
    val btnCalDone    get() = t("В календарі ✓",   "In Calendar ✓", "En calendario ✓","Im Kalender ✓")
    val btnAddPlace   get() = t("Додати місце",    "Add place",     "Agregar lugar",  "Ort hinzufügen")

    // ── Dialogs ──────────────────────────────────────────────────────────────
    val dlgCancel   get() = t("Скасувати",   "Cancel",   "Cancelar",  "Abbrechen")
    val dlgDelete   get() = t("Видалити",    "Delete",   "Eliminar",  "Löschen")
    val dlgChange   get() = t("Змінити",     "Change",   "Cambiar",   "Ändern")
    val dlgClose    get() = t("Закрити",     "Close",    "Cerrar",    "Schließen")

    fun geofenceMsg(meters: Int) = when (code) {
        "en" -> "Reminder fires within ${meters}m of this location."
        "es" -> "El recordatorio se activa a ${meters}m de este lugar."
        "de" -> "Erinnerung wird in ${meters}m Entfernung ausgelöst."
        else -> "Нагадування спрацює при наближенні на ${meters}м."
    }
    fun noLocations() = t(
        "Немає збережених локацій",
        "No saved locations",
        "No hay lugares guardados",
        "Keine gespeicherten Orte"
    )
    fun openLocationManager() = t(
        "Відкрити Менеджер",
        "Open Manager",
        "Abrir gestor",
        "Manager öffnen"
    )
    fun addLocationFirst() = t(
        "Спочатку додайте локацію в менеджері локацій.",
        "First add a location in the location manager.",
        "Primero agrega un lugar en el gestor de lugares.",
        "Füge zuerst einen Ort im Ortsmanager hinzu."
    )
    fun pickLocationFor(title: String) = when (code) {
        "en" -> "📍 Location for «$title»"
        "es" -> "📍 Lugar para «$title»"
        "de" -> "📍 Ort für «$title»"
        else -> "📍 Локація для «$title»"
    }

    // ── Voice prompt ─────────────────────────────────────────────────────────
    val voicePrompt get() = t(
        "Що нагадати?",
        "What to remind?",
        "¿Qué recordar?",
        "Was erinnern?"
    )
    val voiceLang get() = when (code) {
        "en" -> "en-US"
        "es" -> "es-ES"
        "de" -> "de-DE"
        else -> "uk-UA"
    }

    // ── Empty states ─────────────────────────────────────────────────────────
    val emptyEvents   get() = t("Натисни 🎙 і скажи, що нагадати", "Tap 🎙 and say what to remind", "Toca 🎙 y di qué recordar", "Tippe 🎙 und sag, was erinnert werden soll")
    val emptyFavs     get() = t("Немає обраних нагадувань", "No favorite reminders", "Sin favoritos", "Keine Favoriten")
    val emptyNoTime   get() = t("Натисни 🎙 і скажи нагадування без часу", "Tap 🎙 for a no-time reminder", "Toca 🎙 sin hora", "Tippe 🎙 ohne Zeit")

    // ── Settings tabs ────────────────────────────────────────────────────────
    val settingsTabGeneral  get() = t("⚙️ Основні",   "⚙️ General",    "⚙️ General",    "⚙️ Allgemein")
    val settingsTabGCal     get() = t("📅 Google Cal", "📅 Google Cal", "📅 Google Cal", "📅 Google Kal.")
    val settingsTabPro      get() = t("✨ Pro",        "✨ Pro",         "✨ Pro",         "✨ Pro")

    // ── Settings — General sections ──────────────────────────────────────────
    val secMicWidget     get() = t("Мікрофон-віджет",                    "Mic Widget",                        "Widget de micrófono",              "Mikrofon-Widget")
    val secSound         get() = t("Звук сповіщення",                    "Notification sound",                "Sonido de notificación",           "Benachrichtigungston")
    val secSnoozeBtns    get() = t("Кнопки «Відкласти»",                 "Snooze Buttons",                    "Botones de aplazamiento",          "Schlummertasten")
    val secTimeOfDay     get() = t("Час дня (для «зранку/вдень/ввечері»)","Time of day (morning/afternoon/evening)","Hora del día (mañana/tarde/noche)","Tageszeit (morgens/mittags/abends)")
    val secBackup        get() = t("Резервна копія",                     "Backup",                            "Copia de seguridad",               "Datensicherung")

    val widgetAddBtn     get() = t("+ Додати мікрофон-віджет на екран",  "+ Add mic widget to screen",        "+ Agregar widget al escritorio",    "+ Mikrofon-Widget hinzufügen")
    val widgetAddHint    get() = t("Також довге натискання на іконку додатку, щоб побачити список івентів.", "Long-press the app icon to see the events list.", "Mantén presionado el ícono para ver eventos.", "Lange auf das App-Symbol tippen, um Ereignisse zu sehen.")
    val widgetAdded      get() = t("✅ Мікрофон-віджет вже додано на екран", "✅ Mic widget already on screen", "✅ Widget ya añadido",              "✅ Mikrofon-Widget bereits hinzugefügt")
    val widgetPinSuccess get() = t("✅ Мікрофон-віджет додано",           "✅ Mic widget added",               "✅ Widget agregado",                "✅ Mikrofon-Widget hinzugefügt")
    val widgetPinManual  get() = t("Додайте вручну через «Віджети»",      "Add manually via Widgets",          "Agrégalo manualmente en Widgets",  "Manuell über «Widgets» hinzufügen")

    val soundHint        get() = t("Відкриває системні налаштування каналу", "Opens system channel settings", "Abre la configuración del canal",  "Öffnet Systemkanaleinstellungen")
    val soundPrefix      get() = t("🔔  Звук: ",                         "🔔  Sound: ",                       "🔔  Sonido: ",                     "🔔  Ton: ")

    val snoozeBtnLabel1  get() = t("Кнопка 1", "Button 1", "Botón 1", "Taste 1")
    val snoozeBtnLabel2  get() = t("Кнопка 2", "Button 2", "Botón 2", "Taste 2")
    val snoozeUnitMin    get() = t("хв", "min", "min", "Min")
    val snoozeUnitDay    get() = t("дн", "d",   "d",   "T")

    val timeOfDayMorning   get() = t("Зранку",  "Morning",   "Mañana", "Morgens")
    val timeOfDayAfternoon get() = t("Вдень",   "Afternoon", "Tarde",  "Mittags")
    val timeOfDayEvening   get() = t("Ввечері", "Evening",   "Noche",  "Abends")

    val backupSave    get() = t("💾  Зберегти",   "💾  Save",    "💾  Guardar",     "💾  Speichern")
    val backupRestore get() = t("♻️  Відновити", "♻️  Restore", "♻️  Restaurar",   "♻️  Wiederherstellen")

    val persistLabel  get() = t("Показувати в статус-барі", "Show in status bar", "Mostrar en barra de estado", "In Statusleiste anzeigen")

    // ── Settings — GCal sections ─────────────────────────────────────────────
    val secAutoExport    get() = t("Авто-експорт",  "Auto-export",   "Auto-exportar",    "Auto-Export")
    val secEventColor    get() = t("Колір події",   "Event color",   "Color de evento",  "Ereignisfarbe")
    val secShareApp      get() = t("Поділитись",    "Share",         "Compartir",        "Teilen")

    val autoExportLabel  get() = t("Авто-додавати при створенні", "Auto-add on create", "Auto-añadir al crear", "Auto-hinzufügen beim Erstellen")
    val autoExportHint   get() = t("Кожне нове нагадування автоматично з'явиться в Google Calendar", "Every new reminder will be auto-added to Google Calendar", "Cada recordatorio nuevo se añadirá a Google Calendar", "Jede neue Erinnerung wird automatisch zu Google Kalender hinzugefügt")

    val shareAppBtn  get() = t("📤  Поділитись додатком Remindly", "📤  Share Remindly app", "📤  Compartir app Remindly", "📤  Remindly-App teilen")
    val shareAppHint get() = t("Надішліть друзям APK-файл Remindly.apk.", "Send friends the Remindly.apk file.", "Envía a tus amigos el archivo Remindly.apk.", "Sende Freunden die Remindly.apk-Datei.")
    val shareApkReady get() = t("📦 Remindly.apk готовий до відправки", "📦 Remindly.apk ready to share", "📦 Remindly.apk listo para compartir", "📦 Remindly.apk bereit zum Teilen")
    fun shareChooserTitle() = t("Поділитись Remindly.apk", "Share Remindly.apk", "Compartir Remindly.apk", "Remindly.apk teilen")

    fun gCalTipBox() = t(
        "💡 Google Calendar дозволяє:\n• Бачити нагадування на всіх пристроях\n• Ділитися подіями з іншими\n• Отримувати нагадування навіть без додатку\n\nНатисніть іконку ↑📅 у будь-якому івенті, щоб одразу додати його до Calendar.",
        "💡 Google Calendar lets you:\n• See reminders on all devices\n• Share events with others\n• Get reminders even without the app\n\nTap ↑📅 on any event to add it to Calendar.",
        "💡 Google Calendar te permite:\n• Ver recordatorios en todos los dispositivos\n• Compartir eventos con otros\n• Recibir recordatorios sin la app\n\nToca ↑📅 en cualquier evento para añadirlo.",
        "💡 Google Kalender ermöglicht:\n• Erinnerungen auf allen Geräten sehen\n• Ereignisse mit anderen teilen\n• Erinnerungen ohne App erhalten\n\nTippe ↑📅 auf ein Ereignis, um es hinzuzufügen."
    )

    // ── Settings — Locations ─────────────────────────────────────────────────
    val secSavedPlaces   get() = t("Збережені місця",                 "Saved places",                "Lugares guardados",               "Gespeicherte Orte")
    val openLocMgrBtn    get() = t("📍  Відкрити менеджер локацій",   "📍  Open location manager",   "📍  Abrir gestor de lugares",     "📍  Ortsmanager öffnen")
    val locSettingsHint  get() = t("Додайте адреси: дім, робота, магазин — і прив'язуйте до них нагадування.", "Add addresses: home, work, shop — and bind reminders to them.", "Agrega direcciones: casa, trabajo, tienda — y vincúlalas a recordatorios.", "Adressen hinzufügen: Zuhause, Büro, Laden — und mit Erinnerungen verknüpfen.")

    fun locSettingsTipBox() = t(
        "💡 Як працюють локаційні нагадування:\n\nЯкщо в івенті немає конкретного часу, ви можете прив'язати його до місця — нагадування спрацює, коли телефон опиниться поруч із цією локацією.\n\nНатисніть 📍 в будь-якому івенті без часу (він стоїть як закріплений), щоб вибрати збережене місце.",
        "💡 How location reminders work:\n\nIf an event has no specific time, bind it to a place — the reminder fires when the phone is near that location.\n\nTap 📍 on any no-time event (shown as pinned) to choose a saved place.",
        "💡 Cómo funcionan los recordatorios de ubicación:\n\nSi un evento no tiene hora, vincúlalo a un lugar — el recordatorio se activa cuando el teléfono esté cerca.\n\nToca 📍 en cualquier evento sin hora (aparece fijo) para elegir un lugar guardado.",
        "💡 Wie Ortserinnerungen funktionieren:\n\nWenn ein Ereignis keine Zeit hat, verknüpfe es mit einem Ort — die Erinnerung wird ausgelöst, wenn das Handy in der Nähe ist.\n\nTippe 📍 auf ein Ereignis ohne Zeit (als angeheftet angezeigt), um einen gespeicherten Ort zu wählen."
    )

    // ── Location list / manager ──────────────────────────────────────────────
    val locSearchHint  get() = t("Пошук…",    "Search…",    "Buscar…",  "Suchen…")
    val locListEmpty   get() = t("Немає збережених локацій.\nНатисніть «+» вгорі, щоб додати.", "No saved locations.\nTap «+» above to add one.", "Sin lugares guardados.\nToca «+» arriba para añadir.", "Keine gespeicherten Orte.\nTippe «+» oben, um einen hinzuzufügen.")
    val locMgrEmpty    get() = t("Немає збережених локацій.\nДодайте першу ↑", "No saved locations.\nAdd the first one ↑", "Sin lugares guardados.\nAgrega el primero ↑", "Keine gespeicherten Orte.\nFüge den ersten hinzu ↑")
    val locNotFound    get() = t("Нічого не знайдено", "Nothing found", "Nada encontrado", "Nichts gefunden")
    val locMgrTitle    get() = t("📍 Менеджер локацій", "📍 Location Manager", "📍 Gestor de lugares", "📍 Ortsmanager")
    fun locDeleteTitle(name: String) = when (code) {
        "en" -> "Delete «$name»?"; "es" -> "¿Eliminar «$name»?"; "de" -> "«$name» löschen?"; else -> "Видалити «$name»?"
    }

    // ── LocationPickerActivity ───────────────────────────────────────────────
    val locPickerSubtitle  get() = t("Оберіть точку на карті",                   "Select a point on the map",        "Selecciona un punto en el mapa",       "Punkt auf der Karte auswählen")
    val locPickerNameHint  get() = t("Назва місця",                               "Place name",                       "Nombre del lugar",                     "Ortsname")
    val locPickerMoveHint  get() = t("Перемістіть карту до потрібного місця",     "Move the map to the desired location", "Mueve el mapa al lugar deseado",   "Karte zum gewünschten Ort verschieben")
    val locPickerGpsBtn    get() = t("🎯  Використати поточне місце",             "🎯  Use current location",         "🎯  Usar ubicación actual",            "🎯  Aktuellen Ort verwenden")
    val locPickerSave      get() = t("Зберегти",  "Save",   "Guardar",   "Speichern")
    val locPickerCancel    get() = t("Скасувати", "Cancel", "Cancelar",  "Abbrechen")
    val locPickerNoGps     get() = t("GPS не знайдено. Спробуйте ще раз.", "GPS not found. Try again.", "GPS no encontrado. Intenta de nuevo.", "GPS nicht gefunden. Versuche es erneut.")

    // ── Pro / Subscription ───────────────────────────────────────────────────
    val secSubscription   get() = t("Підписка",  "Subscription", "Suscripción", "Abonnement")
    val proLabel          get() = t("✨ Pro",     "✨ Pro",        "✨ Pro",       "✨ Pro")
    val freeLabel         get() = t("🔒 Free",   "🔒 Free",      "🔒 Gratis",   "🔒 Kostenlos")

    val proCardTitle      get() = t("Remindly Pro", "Remindly Pro", "Remindly Pro", "Remindly Pro")
    val proCardSubtitle   get() = t("Розблокуй все одним натисканням", "Unlock everything with one tap", "Desbloquea todo con un toque", "Alles mit einem Tipp freischalten")
    val proFeature1       get() = t("⚡  Необмежена кількість нагадувань", "⚡  Unlimited reminders", "⚡  Recordatorios ilimitados", "⚡  Unbegrenzte Erinnerungen")
    val proFeature2       get() = t("📅  Синхронізація з Google Calendar",  "📅  Google Calendar sync", "📅  Sincronización con Google Calendar", "📅  Google Kalender-Synchronisation")
    val proFeature3       get() = t("📍  Нагадування за локацією — прийшов і спрацювало", "📍  Location reminders — fires when you arrive", "📍  Recordatorios por lugar — se activan al llegar", "📍  Standort-Erinnerungen — ausgelöst bei Ankunft")
    val proFreeLimit      get() = t("🔒  Безкоштовно: 2 активних, без Calendar та локацій",
                                     "🔒  Free: 2 active reminders, no Calendar, no locations",
                                     "🔒  Gratis: 2 activos, sin Calendar ni lugares",
                                     "🔒  Gratis: 2 aktive, kein Kalender, keine Orte")
    val proSubscribeBtn   get() = t("✨  Активувати Pro", "✨  Activate Pro", "✨  Activar Pro", "✨  Pro aktivieren")
    val proPriceHint      get() = t("7 днів безкоштовно · потім \$1.99/рік · скасування будь-коли",
                                     "7 days free · then \$1.99/year · cancel anytime",
                                     "7 días gratis · luego \$1.99/año · cancela cuando quieras",
                                     "7 Tage gratis · dann \$1,99/Jahr · jederzeit kündbar")
    val proActiveTitle    get() = t("✨  Pro активний", "✨  Pro active", "✨  Pro activo", "✨  Pro aktiv")
    val proActiveDesc     get() = t("Всі функції розблоковані", "All features unlocked", "Todas las funciones desbloqueadas", "Alle Funktionen freigeschaltet")
    val proCancelBtn      get() = t("Скасувати підписку", "Cancel subscription", "Cancelar suscripción", "Abonnement kündigen")
    val proWelcomeToast   get() = t("✨ Ласкаво просимо до Pro!", "✨ Welcome to Pro!", "✨ ¡Bienvenido a Pro!", "✨ Willkommen bei Pro!")
    val proCancelledToast get() = t("Підписку скасовано", "Subscription cancelled", "Suscripción cancelada", "Abonnement gekündigt")

    val proTrialTitle     get() = t("🎁  Пробний режим Pro",    "🎁  Pro Trial",               "🎁  Período de prueba Pro",    "🎁  Pro-Testphase")
    val proTrialKeep      get() = t("Підпишіться, щоб зберегти доступ після пробного",
                                     "Subscribe to keep access after the trial",
                                     "Suscríbete para mantener el acceso tras la prueba",
                                     "Abonniere, um nach der Testphase Zugang zu behalten")
    fun trialBadge(days: Int) = when (code) {
        "en" -> "🎁 $days d trial"
        "es" -> "🎁 Prueba $days d"
        "de" -> "🎁 $days T Test"
        else -> "🎁 Пробний $days дн"
    }
    fun trialDaysText(days: Int) = when (code) {
        "en" -> "⏳ $days day${if (days == 1) "" else "s"} left in trial"
        "es" -> "⏳ Quedan $days día${if (days == 1) "" else "s"} de prueba"
        "de" -> "⏳ Noch $days Tag${if (days == 1) "" else "e"} Testphase"
        else -> "⏳ Залишилось $days ${if (days == 1) "день" else if (days < 5) "дні" else "днів"}"
    }

    // Paywall
    val paywallFeatures   get() = t(
        "✅ Необмежені нагадування\n✅ Google Calendar синхронізація\n✅ Нагадування за локацією — прийшов і спрацювало\n\n🔒 Зараз: 2 нагадування, без Calendar і локацій",
        "✅ Unlimited reminders\n✅ Google Calendar sync\n✅ Location reminders — fires when you arrive\n\n🔒 Now: 2 reminders, no Calendar, no locations",
        "✅ Recordatorios ilimitados\n✅ Google Calendar\n✅ Recordatorios por lugar — se activan al llegar\n\n🔒 Ahora: 2 recordatorios, sin Calendar ni lugares",
        "✅ Unbegrenzte Erinnerungen\n✅ Google Kalender\n✅ Standort-Erinnerungen — ausgelöst bei Ankunft\n\n🔒 Jetzt: 2 Erinnerungen, kein Kalender, keine Orte"
    )
    val paywallLimitMsg   get() = t(
        "Ліміт безкоштовної версії:\n2 активних нагадування.",
        "Free plan limit:\n2 active reminders.",
        "Límite del plan gratuito:\n2 recordatorios activos.",
        "Gratis-Limit: 2 aktive Erinnerungen."
    )
    val paywallGCalMsg    get() = t(
        "Google Calendar синхронізація доступна у Pro.",
        "Google Calendar sync requires Pro.",
        "Google Calendar requiere Pro.",
        "Google Kalender erfordert Pro."
    )
    val paywallLocMsg     get() = t(
        "Нагадування за локацією та менеджер місць — лише у Pro.\n\nДодай місця і прив'язуй нагадування — вони спрацюють, коли ти прийдеш.",
        "Location reminders and place manager require Pro.\n\nSave places and attach reminders — they fire when you arrive.",
        "Recordatorios por lugar y gestor de lugares requieren Pro.\n\nGuarda lugares y adjunta recordatorios — se activan al llegar.",
        "Standort-Erinnerungen und Ortsmanager erfordern Pro.\n\nSpeichere Orte — Erinnerungen werden bei Ankunft ausgelöst."
    )

    // ── Help sections ────────────────────────────────────────────────────────
    data class HelpContent(
        val sections: List<HelpSection>
    )
    data class HelpSection(val emoji: String, val title: String, val color: Int, val items: List<HelpItem>)
    data class HelpItem(val title: String, val body: String)

    fun helpContent(): HelpContent = when (code) {
        "en" -> HelpContent(listOf(
            HelpSection("🎙", "Voice Commands", 0xFFFF5722.toInt(), listOf(
                HelpItem("Timed reminders",
                    "Say the time — the app understands:\n\n" +
                    "• «In 5 minutes lock the door»\n" +
                    "• «Tomorrow at 9 call the dentist»\n" +
                    "• «Every hour stand up»\n" +
                    "• «On Wednesday close the deal»"
                ),
                HelpItem("No-time reminders",
                    "If no time is detected the reminder is pinned immediately in the No-time tab.\n\n" +
                    "Attach a location: tap 📍 on the event and choose a place."
                ),
            )),
            HelpSection("📅", "Google Calendar", 0xFF4CAF50.toInt(), listOf(
                HelpItem("Export — manual or auto",
                    "Tap 📅 on any event to add it to Google Calendar manually.\n\n" +
                    "Or enable «Auto-add on create» in ⚙️ → Google Cal."
                ),
                HelpItem("Google commands",
                    "Start with «Google» — the event goes straight to Google Calendar:\n\n" +
                    "Daily: «Google every day at 9:30 take vitamins» → repeats daily, 9:30–23:59\n\n" +
                    "«daily» = «every day»\n\n" +
                    "One-time: «Google in 5 months visit the dentist» → 7-day event\n\n" +
                    "Manage these events from Google Calendar."
                ),
                HelpItem("Share a reminder",
                    "Tap 📤 on an event — the app generates a Google Calendar link. Send it in Telegram, WhatsApp, etc. The recipient taps it and saves the event to their calendar."
                ),
            )),
            HelpSection("🔔", "Notifications", 0xFFFFAB40.toInt(), listOf(
                HelpItem("Pin to status bar",
                    "Tap 📌 on any active event — the notification stays in the shade permanently, even after reboot.\n\n" +
                    "The «Show in status bar» toggle shows a countdown to the next reminder."
                ),
                HelpItem("Repeat every minute",
                    "Tap 🔁 in a notification — it repeats every minute until you dismiss or complete it."
                ),
                HelpItem("Snooze buttons",
                    "Two snooze buttons in each notification. Values and units (min or days) are set in ⚙️ → Snooze Buttons."
                ),
            )),
            HelpSection("📍", "Locations", 0xFF4FC3F7.toInt(), listOf(
                HelpItem("Saving places",
                    "In the 📍 tab save important places: home, work, shop.\n\n" +
                    "Attach to a no-time reminder — the phone reminds you when you arrive."
                ),
                HelpItem("Location in a voice command",
                    "If a saved location name starts the command, it's attached automatically:\n\n" +
                    "• «Home do a workout» → 📍 Home\n" +
                    "• «At work meeting» → 📍 Work\n\n" +
                    "Works with prepositions: «at», «in», «to»."
                ),
            )),
            HelpSection("⚙️", "Settings & data", 0xFF9E9E9E.toInt(), listOf(
                HelpItem("Backup",
                    "In ⚙️ → Backup you can save all events, tasks and locations to a file, and restore them later."
                ),
            )),
        ))
        "es" -> HelpContent(listOf(
            HelpSection("🎙", "Comandos de voz", 0xFFFF5722.toInt(), listOf(
                HelpItem("Recordatorios con hora",
                    "Di la hora — la app entiende:\n\n" +
                    "• «En 5 minutos cerrar la puerta»\n" +
                    "• «Mañana a las 9 llamar al dentista»\n" +
                    "• «Cada hora levantarse»\n" +
                    "• «El miércoles cerrar el trato»"
                ),
                HelpItem("Recordatorios sin hora",
                    "Si no se detecta hora, el recordatorio se ancla en la pestaña Sin hora.\n\n" +
                    "Asigna un lugar: toca 📍 en el evento y elige un lugar."
                ),
            )),
            HelpSection("📅", "Google Calendar", 0xFF4CAF50.toInt(), listOf(
                HelpItem("Exportar — manual o automático",
                    "Toca 📅 en cualquier evento para añadirlo manualmente.\n\n" +
                    "O activa «Auto-añadir al crear» en ⚙️ → Google Cal."
                ),
                HelpItem("Comandos Google",
                    "Empieza con «Google» — el evento va directamente a Google Calendar:\n\n" +
                    "Diario: «Google cada día a las 9:30 tomar vitaminas» → diario, 9:30–23:59\n\n" +
                    "Único: «Google en 5 meses ir al dentista» → evento de 7 días\n\n" +
                    "Gestiona estos eventos desde Google Calendar."
                ),
                HelpItem("Compartir recordatorio",
                    "Toca 📤 en un evento — se genera un enlace de Google Calendar. Envíalo por Telegram o WhatsApp. El destinatario lo toca y lo guarda en su calendario."
                ),
            )),
            HelpSection("🔔", "Notificaciones", 0xFFFFAB40.toInt(), listOf(
                HelpItem("Fijar en la barra de estado",
                    "Toca 📌 en cualquier evento activo — la notificación permanece fija, incluso tras reiniciar.\n\n" +
                    "El interruptor «Mostrar en barra de estado» muestra una cuenta atrás."
                ),
                HelpItem("Repetir cada minuto",
                    "Toca 🔁 en una notificación — se repite cada minuto hasta que actúes."
                ),
                HelpItem("Botones de aplazamiento",
                    "Dos botones en cada notificación. Configúralos en ⚙️ → Botones de aplazamiento."
                ),
            )),
            HelpSection("📍", "Lugares", 0xFF4FC3F7.toInt(), listOf(
                HelpItem("Guardar lugares",
                    "En la pestaña 📍 guarda lugares importantes: casa, trabajo, tienda.\n\n" +
                    "Asígnalos a recordatorios sin hora — el teléfono te avisa al llegar."
                ),
                HelpItem("Lugar en comando de voz",
                    "Si el nombre del lugar está al inicio del comando, se asigna automáticamente:\n\n" +
                    "• «Casa hacer ejercicio» → 📍 Casa\n" +
                    "• «En el trabajo reunión» → 📍 Trabajo\n\n" +
                    "Funciona con preposiciones: «en», «a», «al»."
                ),
            )),
            HelpSection("⚙️", "Ajustes y datos", 0xFF9E9E9E.toInt(), listOf(
                HelpItem("Copia de seguridad",
                    "En ⚙️ → Copia de seguridad puedes guardar todos los eventos, tareas y lugares en un archivo y restaurarlos más tarde."
                ),
            )),
        ))
        "de" -> HelpContent(listOf(
            HelpSection("🎙", "Sprachbefehle", 0xFFFF5722.toInt(), listOf(
                HelpItem("Erinnerungen mit Uhrzeit",
                    "Sage die Zeit — die App versteht:\n\n" +
                    "• «In 5 Minuten die Tür abschließen»\n" +
                    "• «Morgen um 9 den Zahnarzt anrufen»\n" +
                    "• «Jede Stunde aufstehen»\n" +
                    "• «Am Mittwoch den Deal abschließen»"
                ),
                HelpItem("Erinnerungen ohne Uhrzeit",
                    "Wenn keine Zeit erkannt wird, wird die Erinnerung im Tab Ohne Zeit angeheftet.\n\n" +
                    "Ort hinzufügen: tippe 📍 auf das Ereignis und wähle einen Ort."
                ),
            )),
            HelpSection("📅", "Google Kalender", 0xFF4CAF50.toInt(), listOf(
                HelpItem("Exportieren — manuell oder automatisch",
                    "Tippe 📅 auf ein Ereignis, um es manuell hinzuzufügen.\n\n" +
                    "Oder aktiviere «Auto-hinzufügen» in ⚙️ → Google Kal."
                ),
                HelpItem("Google-Befehle",
                    "Beginne mit «Google» — das Ereignis geht direkt in den Google Kalender:\n\n" +
                    "Täglich: «Google jeden Tag um 9:30 Vitamine nehmen» → täglich, 9:30–23:59\n\n" +
                    "Einmalig: «Google in 5 Monaten zum Zahnarzt» → 7-Tage-Ereignis\n\n" +
                    "Verwalte diese Ereignisse im Google Kalender."
                ),
                HelpItem("Erinnerung teilen",
                    "Tippe 📤 auf ein Ereignis — es wird ein Google Kalender-Link generiert. Sende ihn per Telegram oder WhatsApp. Der Empfänger tippt darauf und speichert es in seinem Kalender."
                ),
            )),
            HelpSection("🔔", "Benachrichtigungen", 0xFFFFAB40.toInt(), listOf(
                HelpItem("In der Statusleiste anheften",
                    "Tippe 📌 auf ein aktives Ereignis — die Benachrichtigung bleibt dauerhaft, auch nach Neustart.\n\n" +
                    "Der Schalter «In Statusleiste anzeigen» zeigt einen Countdown."
                ),
                HelpItem("Jede Minute wiederholen",
                    "Tippe 🔁 in einer Benachrichtigung — sie wiederholt sich jede Minute, bis du reagierst."
                ),
                HelpItem("Schlummertasten",
                    "Zwei Tasten in jeder Benachrichtigung. Konfiguriere sie in ⚙️ → Schlummertasten."
                ),
            )),
            HelpSection("📍", "Orte", 0xFF4FC3F7.toInt(), listOf(
                HelpItem("Orte speichern",
                    "Im Tab 📍 speichere wichtige Orte: Zuhause, Büro, Laden.\n\n" +
                    "Weise sie Erinnerungen ohne Uhrzeit zu — das Handy erinnert dich bei Ankunft."
                ),
                HelpItem("Ort im Sprachbefehl",
                    "Wenn ein gespeicherter Ortsname am Anfang des Befehls steht, wird er automatisch zugewiesen:\n\n" +
                    "• «Zuhause trainieren» → 📍 Zuhause\n" +
                    "• «Im Büro Meeting» → 📍 Büro\n\n" +
                    "Funktioniert mit Präpositionen: «im», «beim», «zum»."
                ),
            )),
            HelpSection("⚙️", "Einstellungen & Daten", 0xFF9E9E9E.toInt(), listOf(
                HelpItem("Datensicherung",
                    "In ⚙️ → Datensicherung kannst du alle Ereignisse, Aufgaben und Orte in einer Datei speichern und später wiederherstellen."
                ),
            )),
        ))
        else -> HelpContent(listOf(  // uk
            HelpSection("🎙", "Голосові команди", 0xFFFF5722.toInt(), listOf(
                HelpItem("Нагадування з часом",
                    "Скажи час — додаток розпізнає і поставить нагадування:\n\n" +
                    "• «Через 5 хвилин відкрити двері»\n" +
                    "• «Завтра о дев'ятій зателефонувати»\n" +
                    "• «In 4 minutes open the door»\n" +
                    "• «Every hour stand up»\n" +
                    "• «On Wednesday close the deal»"
                ),
                HelpItem("Нагадування без часу",
                    "Якщо час не розпізнано — нагадування з'являється одразу і залишається закріпленим у вкладці «Без часу».\n\n" +
                    "Прив'яжи до місця: натисни 📍 на івенті та вибери локацію."
                ),
            )),
            HelpSection("📅", "Google Calendar", 0xFF4CAF50.toInt(), listOf(
                HelpItem("Експорт — вручну або авто",
                    "Натисни 📅 на будь-якому івенті, щоб додати до Google Calendar вручну.\n\n" +
                    "Або увімкни «Авто-додавати при створенні» у ⚙️ → Google Cal."
                ),
                HelpItem("Google-команди",
                    "Почни зі слова «Google» — подія створюється прямо в Google Calendar:\n\n" +
                    "Щоденно: «Google кожного дня о 9:30 приймати вітаміни» → щодня, 9:30–23:59\n\n" +
                    "«щоденно» = «кожного дня»\n\n" +
                    "Одноразово: «Google через 5 місяців до стоматолога» → подія 7 днів\n\n" +
                    "Керування — через Google Календар."
                ),
                HelpItem("Поділитись нагадуванням",
                    "Натисни 📤 на івенті — додаток сформує посилання на Google Calendar. Надішли в Telegram або WhatsApp. Отримувач натискає і зберігає до свого календаря."
                ),
            )),
            HelpSection("🔔", "Нотифікації", 0xFFFFAB40.toInt(), listOf(
                HelpItem("Закріплення в статус-барі",
                    "Натисни 📌 на будь-якому активному івенті — нотифікація залишається у шторці постійно, навіть після перезавантаження.\n\n" +
                    "Перемикач «Показувати в статус-барі» вмикає відлік до наступного нагадування."
                ),
                HelpItem("Нагадування щохвилини",
                    "У нотифікації натисни 🔁 — нагадування повторюватиметься кожну хвилину, доки ти не відреагуєш."
                ),
                HelpItem("Кнопки «Відкласти»",
                    "У нотифікації є дві кнопки відкладення. Значення і одиниці (хв або дн) налаштовуються у ⚙️ → «Кнопки Відкласти»."
                ),
            )),
            HelpSection("📍", "Локації", 0xFF4FC3F7.toInt(), listOf(
                HelpItem("Збереження місць",
                    "У вкладці 📍 зберігай важливі місця: дім, робота, магазин.\n\n" +
                    "Прив'яжи до нагадування без часу — телефон нагадає, коли ти опинишся поруч."
                ),
                HelpItem("Локація у голосовій команді",
                    "Якщо на початку команди стоїть назва збереженої локації — додаток прив'яже її автоматично:\n\n" +
                    "• «Вдома зробити зарядку» → 📍 Вдома\n" +
                    "• «На роботі зустріч» → 📍 Робота\n\n" +
                    "Працює з прийменниками: «в», «у», «на», «до»."
                ),
            )),
            HelpSection("⚙️", "Налаштування і дані", 0xFF9E9E9E.toInt(), listOf(
                HelpItem("Резервна копія",
                    "У ⚙️ → «Резервна копія» можна зберегти всі події, задачі і локації у файл, та відновити їх пізніше."
                ),
            )),
        ))
    }

    private fun t(uk: String, en: String, es: String, de: String) = when (code) {
        "en" -> en; "es" -> es; "de" -> de; else -> uk
    }
}

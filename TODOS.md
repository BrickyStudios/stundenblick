# TODOS

Aus dem CEO-Review vom 15.11.2026 (Plan: `~/.gstack/projects/BrickyStudios-stundenblick/plans/2026-11-15-v1-plan.md`).

## Vor v1.0

- **Test mit echtem Konto.** QR-Code scannen, eigene Woche, Stunden-Details, Verbindungstest kopieren. Erst danach v1.0 taggen.

## Später

- **K13 REST-Fallback für den eigenen Plan.** Untis liefert bei manchen Konten den eigenen Plan nur noch über `/WebUntis/api/rest/view/v1/timetable/entries` (BetterUntis #503). Entscheidung nach den Verbindungstest-Daten echter Konten. Aufwand M.
- **K10 Freie Räume.** Nur auf Knopfdruck, höchstens ein Abruf pro Raum und Woche, Cache bis Wochenende, Fortschritt mit Abbrechen, nur Räume, deren Plan das Konto sehen darf. Bei 100–600 Räumen sonst zu viele Anfragen. Aufwand M.
- **K9 Infos-Listen.** Nachrichten des Tages, Klausuren, Hausaufgaben als eigene Listen. Aufwand S.
- **K12 Mehrere Profile.** Eltern mit zwei Kindern, zwei Schulen. Aufwand M.
- **K19 Gemeinsame Freistunden.** Zwei Pläne übereinander legen. Braucht K12. Aufwand M.
- **K18 Kalender-Export (ICS).** Aufwand S.
- **IzzyOnDroid-Antrag** (fastlane-Metadaten anlegen). Updater ist schon abschaltbar.
- **Signierschlüssel sichern:** `~/.config/stundenblick/` auf den USB-Stick, wie bei Monatskasse. Ohne Schlüssel keine Updates.
- **Wochenraster bei vielen parallelen Gruppen:** ab 4 Gruppen pro Stunde werden die Zellen sehr schmal. Idee: ab 3 Spuren nur Fach zeigen und Raum/Lehrkraft in die Tagesansicht verlagern.

## Gestrichen

Wear OS, Abwesenheiten melden, Nachrichten senden.

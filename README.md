<p align="center"><img src="docs/bilder/social-preview.png" width="860" alt="Stundenblick, Untis-Stundenplan-App für Android: Wochenraster mit Entfall, Vertretung, Raumänderung und Klausur farbig markiert"></p>

<h1 align="center">Stundenblick: Untis-Stundenplan-App für Android, quelloffener Nachfolger von BetterUntis</h1>

<p align="center"><strong>Alle Stundenpläne, die deine Schule in WebUntis für dich freigibt: deine Klasse, andere Klassen, Lehrkräfte, Räume. Ausfall, Vertretung, Raumänderung und Klausur sind farbig markiert, Oberstufenkurse lassen sich ausblenden, der Plan bleibt offline lesbar, und eine Meldung kommt, wenn sich etwas ändert. Ohne Tracking, ohne Google-Dienste, GPL-3.0.</strong></p>

<p align="center">
  <a href="https://kotlinlang.org/"><img alt="Made with Kotlin" src="https://img.shields.io/badge/made_with-Kotlin-14161D?style=for-the-badge&logo=kotlin&logoColor=8E99FF"></a>
  <br>
  <a href="#installieren"><img alt="Android 8.0 oder neuer" src="https://img.shields.io/badge/Android-8.0%2B-4352E0?logo=android&logoColor=8E99FF&labelColor=14161D"></a>
  <a href="#datenschutz"><img alt="Ohne Google-Dienste, läuft auf GrapheneOS" src="https://img.shields.io/badge/GrapheneOS-ohne_Google--Dienste-4352E0?labelColor=14161D"></a>
  <a href="https://developer.android.com/compose"><img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack_Compose-4352E0?logo=jetpackcompose&logoColor=8E99FF&labelColor=14161D"></a>
  <a href="https://github.com/BrickyStudios/stundenblick/actions/workflows/ci.yml"><img alt="Tests" src="https://img.shields.io/github/actions/workflow/status/BrickyStudios/stundenblick/ci.yml?label=tests&color=4352E0&labelColor=14161D"></a>
  <a href="LICENSE"><img alt="Lizenz GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-4352E0?labelColor=14161D"></a>
  <a href="https://github.com/BrickyStudios/stundenblick/commits/main"><img alt="Letzter Commit" src="https://img.shields.io/github/last-commit/BrickyStudios/stundenblick?color=4352E0&labelColor=14161D"></a>
</p>

<p align="center">
  <a href="https://github.com/BrickyStudios/stundenblick/releases/latest/download/Stundenblick.apk"><img alt="Stundenblick.apk herunterladen" src="https://img.shields.io/badge/Download-Stundenblick.apk-4352E0?style=for-the-badge&logo=android&logoColor=ECEDF2&labelColor=4352E0&color=14161D"></a>
  <a href="https://brickystudios.github.io/stundenblick/"><img alt="Website" src="https://img.shields.io/badge/Website-Deutsch-14161D?style=for-the-badge&labelColor=14161D&color=232A5C"></a>
</p>

<p align="center">
  <img src="docs/bilder/1-woche.png" width="200" alt="Die ganze Woche auf einen Blick: Wochenraster mit Entfall, Vertretung, Raumänderung und Klausur">
  <img src="docs/bilder/2-tag.png" width="200" alt="Tagesansicht mit vollen Namen, die vertretene Lehrkraft durchgestrichen">
  <img src="docs/bilder/3-details.png" width="200" alt="Stunden-Details mit Stundeninhalt und Hausaufgabe">
  <img src="docs/bilder/4-anmelden.png" width="200" alt="Anmeldung per QR-Code aus WebUntis, auch mit Zwei-Faktor">
</p>
<p align="center"><sub>Screenshots aus der eingebauten Demo-Schule. Alle Namen dort sind erfunden.</sub></p>

## Warum

[BetterUntis](https://github.com/SapuSeven/BetterUntis) hat seit November 2023 keine stabile Version mehr bekommen. Die Untis-Schnittstelle dahinter läuft weiter (geprüft am 15.11.2026), aber Untis baut um: Konten mit Zwei-Faktor-Anmeldung kommen über Benutzername und Passwort nicht mehr rein, und manche Konten sehen ihren eigenen Plan leer. Stundenblick ist ein neu geschriebener Client für dieselbe Schnittstelle, die auch die offizielle App nutzt.

## Was die App kann

- **Jeder freigegebene Plan:** eigene Klasse, andere Klassen, Lehrkräfte, Räume, Fächer, mit Suche und Favoriten.
- **Änderungen auf einen Blick:** Entfall rot und durchgestrichen, Vertretung und Raumänderung orange mit dem alten Namen daneben, Klausur blau, Zusatzstunde hellblau.
- **Woche oder Tag:** Wochenraster mit Wischen und Jetzt-Linie, Tagesliste mit vollen Namen.
- **Stunden-Details:** Stundeninhalt, Hausaufgaben und Infotext, soweit die Schule sie einträgt.
- **Kurse ausblenden:** für die Oberstufe pro Plan abwählen, welche Fächer du nicht hast.
- **Offline:** geladene Wochen bleiben gespeichert und zeigen „Stand: …“, wenn kein Netz da ist.
- **Meldungen:** einmal pro Stunde prüft die App die nächsten 7 Tage deines Plans und meldet neue Ausfälle, Vertretungen, Raumänderungen und Klausuren.
- **Widget:** heutige Stunden auf dem Startbildschirm, nach Schulschluss der nächste Schultag.
- **Verbindungstest:** zeigt Schritt für Schritt, was Untis für dein Konto liefert, zum Kopieren in ein Issue. Ohne Namen, Passwort oder Schlüssel.
- **Demo-Schule:** ausprobieren ohne Konto.

## Was sie nicht kann

- Sie zeigt nur, was deine Schule für dein Konto freigibt. Blendet die Schule Lehrkräfte oder fremde Klassen aus, fehlen sie auch hier.
- Abwesenheiten melden und Nachrichten schreiben fehlen bewusst.
- Nur Android. Eine iOS-Version bräuchte ein Apple-Entwicklerkonto und die App-Store-Prüfung.

## Installieren

1. [`Stundenblick.apk`](https://github.com/BrickyStudios/stundenblick/releases/latest/download/Stundenblick.apk) herunterladen und installieren (Android 8.0 oder neuer). Mit [Obtainium](https://github.com/ImranR98/Obtainium) bleibt sie aktuell, die App kann sich aber auch selbst aktualisieren.
2. Anmelden, am besten per QR-Code: WebUntis im Browser öffnen → Profil → Freigaben → „Anzeigen“. Den QR-Code mit der Kamera-App scannen, sie öffnet Stundenblick. Das klappt auch mit Zwei-Faktor-Anmeldung und mit IServ-Konten.
3. Alternativ die Schule suchen und mit Benutzername und Passwort anmelden, oder ohne Anmeldung, falls deine Schule öffentliche Pläne hat.

**Stand 0.9:** Demo, Schulsuche, anonymer Zugang und alle Ansichten sind auf dem Emulator gegen eine echte Schule getestet. Anmeldung mit echtem Konto (QR, Passwort, 2FA) ist noch nicht getestet. Wenn etwas hakt: Einstellungen → Verbindungstest → Kopieren und als [Issue](https://github.com/BrickyStudios/stundenblick/issues) einreichen.

## Datenschutz

- Kein eigener Server, kein Konto, kein Tracking.
- Die App spricht nur mit dem WebUntis-Server deiner Schule und, für Updates, mit GitHub. Die Update-Prüfung lässt sich abschalten.
- Dein Passwort wird nicht gespeichert, nur der Zugangsschlüssel, den Untis dafür ausgibt. Er liegt im privaten App-Speicher und ist von Backups ausgenommen.

## Selbst bauen

```bash
./gradlew testDebugUnitTest assembleDebug   # JDK 17
```

Die Tests prüfen den Einmal-Code (RFC-6238-Vektoren), den QR-Link inklusive fremder Server, die Fehlertexte, den Parser an der Demo-Schule, den Kursfilter, den Abgleich für Meldungen und dass der Verbindungstest keine Geheimnisse enthält.

## Rechtliches

Stundenblick ist nicht mit der Untis GmbH verbunden. „Untis“ und „WebUntis“ sind Marken der Untis GmbH. Die Schnittstellen-Kenntnisse stammen aus dem Quellcode von [BetterUntis](https://github.com/SapuSeven/BetterUntis) (GPL-3.0) und eigenen Abrufen.

Lizenz: [GPL-3.0](LICENSE)

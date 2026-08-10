# Psalmverzen met bladmuziek

**Psalmverzen met bladmuziek** is een Android-app voor muzikanten en iedereen die graag psalmen en gezangen zingt met tekst en noten in één rustige, leesbare weergave.

![App Icoon](app/src/main/ic_launcher-playstore.png)

## Belangrijkste Kenmerken

### 🎵 Bladmuziek & Tekst in één oogopslag
Lees de tekst en de noten tegelijkertijd. De app is geoptimaliseerd voor leesbaarheid op mobiele apparaten, zodat je de tekst en noten op 1 scherm kan zien.

### 🔍 Zoom & Schaalbaarheid
Dankzij de ondersteuning voor knijpgebaren (pinch-to-zoom) kun je zowel de bladmuziek als de tekst traploos vergroten of verkleinen. Of je nu een groot tablet of een kleine telefoon gebruikt, de weergave past zich altijd aan jouw voorkeur aan.

### 🎹 Transponeren
Moeite met een te hoge of lage toonsoort? Transponeer de bladmuziek eenvoudig een aantal halve tonen omhoog of omlaag. De noten en de bijbehorende tekst schalen automatisch mee.

### ⚙️ Uitgebreide Configuratie
Personaliseer de app volledig naar jouw wens via het instellingenmenu:
- **Weergavemodi:** kies uit tekst met noten, alleen tekst of alleen noten.
- **Psalmberijmingen:** wissel tussen 1773, Datheen en Revius; 1773 blijft de standaard.
- **Ritme:** toon de melodieën ritmisch of iso-ritmisch.
- **Donker Thema:** Ondersteuning voor een donker thema, inclusief een speciale 'donkere bladmuziek' modus voor gebruik in omgevingen met weinig licht.
- **Tekstinstellingen:** grote letters, regelafbreking toestaan, tekstuitlijning (links, gecentreerd, vullend), twee zinnen op één regel en optionele rusttekens.
- **Scherm aan laten:** Voorkom dat je scherm uitgaat terwijl je aan het zingen of spelen bent.

### 📱 Gebruiksgemak
- **Snel Navigeren:** Veeg horizontaal om direct naar het vorige of volgende vers te springen.
- **Vers-Matrix:** Een handig raster om razendsnel tussen verschillende verzen van een psalm te schakelen.
- **PDF delen:** Deel het huidige vers als A4-PDF met alleen notenbalk en tekst.
- **Volledig Scherm:** Dubbeltik op de muziek of tekst om alle balken te verbergen en de volledige focus op de muziek te leggen.

## Screenshots

| 1. Hoofdscherm | 2. Psalm selectie |
|:---:|:---:|
| ![Hoofdscherm](screenshots/main_screen.png) | ![Psalm selectie](screenshots/psalmselect_screen.png) |
| **3. Bladmuziek** | **4. Donkere modus** |
| ![Bladmuziek](screenshots/music_screen.png) | ![Donkere modus](screenshots/music_screen_darkandfullscreenmode.png) |

## Ontwikkeling
Dit project is gebouwd met de volgende Android-technieken:
- **Kotlin** voor app-logica, instellingen, navigatie en contentselectie.
- **View-gebaseerde Android UI** met AppCompat/Material-componenten.
- **Eigen SVG-renderer in een WebView** voor de dynamische bladmuziekweergave. De app gebruikt hiervoor compacte JSON-assets met melodie- en tekstdata.
- **Gebundelde content-assets** voor psalmen, gezangen, meerdere psalmberijmingen en gedeelde melodieën.

## Licentie
Dit project is bedoeld voor persoonlijk-  en gemeenschapsgebruik.

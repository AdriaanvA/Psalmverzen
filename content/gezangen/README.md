# Gezangen bronnen

Deze map bevat de bewerkbare bronnen voor de Enige Gezangen. De app-assets worden hieruit als JSON gegenereerd; MusicXML wordt in de app runtime uit tekst plus melodie opgebouwd.

## Tekst

Teksten staan in `texts/GezangNNN.json`.

- `title`: naam die in het gezangenoverzicht wordt getoond.
- `verses[].number`: versnummer.
- `verses[].lines`: tekstregels; elke regel wordt gekoppeld aan een melodieregel.

## Melodie

Melodieen staan in `melodies/GezangNNN.json`.

- `source`: herkomst van de melodie.
- `fifths`: voortekens voor de toonsoort.
- `lines`: melodieregels; elke noot heeft `duration`, `step`, `alter`, `octave` en `rest`.
- `alter`: `1` is kruis, `-1` is mol, `null` is geen toevallig teken.

## Woorddelen

Woorddelen staan in `lyrics/GezangNNN.json` en worden gegenereerd uit tekst plus melodie.

- `verses[].lines[].raw`: oorspronkelijke tekstregel.
- `verses[].lines[].display`: leestekst zonder eventuele `_` melisma-markeringen.
- `verses[].lines[].tokens[]`: woord of woorddeel per zangplek, met `syllabic` als `single`, `begin`, `middle` of `end`.

## Genereren

De JSON-bestanden onder `content/gezangen` zijn de beheerde bron. Na handmatig aanpassen van teksten, melodieen of woorddelen:

```powershell
python scripts\generate-content-bundles.py
```

Gebruik de Online Bijbel-importer alleen als de externe bron opnieuw opgehaald en de lokale JSON-bronnen overschreven moeten worden:

```powershell
python scripts\import-online-bijbel-gezangen.py --ids 1..12 --refresh-sources
python scripts\generate-content-bundles.py
```

De app gebruikt unieke melodie-JSON onder `app/src/main/assets/content/melodies` en aparte tekst-JSON onder `app/src/main/assets/content/psalms/texts` en `app/src/main/assets/content/gezangen/texts`; per-vers MusicXML is alleen nog een legacy export via `--write-musicxml`.

# Psalmen bronnen

Deze map bevat bewerkbare bronnen voor de psalmen. De app-assets worden hieruit als JSON gegenereerd; MusicXML wordt in de app runtime uit tekst plus melodie opgebouwd.

## Tekst

Teksten staan in `texts/PsalmNNN.json`.

- `title`: naam van de psalmbron.
- `verses[].number`: versnummer. Psalm 18 kan ook vers `0` bevatten voor de voorzang.
- `verses[].lines`: tekstregels; elke regel wordt gekoppeld aan een melodieregel.
- Gebruik `_` binnen een woord voor een lege nootplek waar dezelfde klank moet doorlopen. Voorbeeld: `betrou_wen` zet `rou` op een extra noot en blijft in alleen-tekst `betrouwen`.

## Melodie

Melodieen staan in `melodies/PsalmNNN.json`.

- `source`: herkomst van de melodie.
- `composer`: melodie-aanduiding uit `psalmen.abc`.
- `key`: ABC-toonsoort, bijvoorbeeld `D ionian`.
- `lines`: ABC-noten per regel. Deze regels zijn makkelijker handmatig te corrigeren dan MusicXML.

## Woorddelen

Woorddelen staan in `lyrics/PsalmNNN.json` en worden gegenereerd uit tekst plus melodie.

- `verses[].lines[].raw`: oorspronkelijke tekstregel, inclusief `_` voor lege melisma-plekken.
- `verses[].lines[].display`: tekstregel zonder `_`, bedoeld voor gewone leestekst.
- `verses[].lines[].tokens[]`: woord of woorddeel per zangplek, met `syllabic` als `single`, `begin`, `middle` of `end`.
- Lege `text` bij `middle`/`end` blijft bewaard; dat markeert een noot waarop dezelfde klank doorloopt.

## Genereren

Na handmatig aanpassen van `content/psalmen/lyrics` of bestaande tekst-assets:

```powershell
python scripts\generate-content-bundles.py
```

Als tekst- of melodiebronnen opnieuw naar woorddelen vertaald moeten worden:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\generate-psalm-assets.ps1 -Psalms 1..150 -AllVerses -ManifestOnly
python scripts\generate-content-bundles.py
```

Gebruik `-RefreshSources` alleen als de lokale JSON-bronnen opnieuw uit `psalmen.abc` en `oude.html` overschreven moeten worden:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\generate-psalm-assets.ps1 -Psalms 1..150 -AllVerses -ManifestOnly -RefreshSources
python scripts\generate-content-bundles.py
```

De bundlegenerator schrijft het app-manifest, unieke melodie-assets en aparte tekst-assets met `melodyFile`-verwijzing. Hij verwijdert ook legacy gecombineerde bestanden en MusicXML per vers.

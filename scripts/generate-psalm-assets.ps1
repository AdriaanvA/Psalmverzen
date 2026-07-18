param(
    [object[]]$Psalms = @(1, 2, 3, 4, 5),
    [int]$Verse = 1,
    [switch]$AllVerses,
    [switch]$WriteManifest,
    [switch]$ManifestOnly,
    [switch]$RefreshSources
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$AbcPath = Join-Path $Root 'psalmen.abc'
$HtmlPath = Join-Path $Root 'oude.html'
$OutDir = Join-Path $Root 'app\src\main\assets'
$PsalmContentDir = Join-Path $Root 'content\psalmen'
$PsalmTextSourceDir = Join-Path $PsalmContentDir 'texts'
$PsalmMelodySourceDir = Join-Path $PsalmContentDir 'melodies'
$PsalmLyricSourceDir = Join-Path $PsalmContentDir 'lyrics'
$Vowels = 'aeiouyAEIOUY' + [char]0x00E1 + [char]0x00E9 + [char]0x00ED + [char]0x00F3 + [char]0x00FA + [char]0x00C1 + [char]0x00C9 + [char]0x00CD + [char]0x00D3 + [char]0x00DA + [char]0x00E0 + [char]0x00E8 + [char]0x00EC + [char]0x00F2 + [char]0x00F9 + [char]0x00C0 + [char]0x00C8 + [char]0x00CC + [char]0x00D2 + [char]0x00D9 + [char]0x00E4 + [char]0x00EB + [char]0x00EF + [char]0x00F6 + [char]0x00FC + [char]0x00C4 + [char]0x00CB + [char]0x00CF + [char]0x00D6 + [char]0x00DC + [char]0x00E2 + [char]0x00EA + [char]0x00EE + [char]0x00F4 + [char]0x00FB + [char]0x00C2 + [char]0x00CA + [char]0x00CE + [char]0x00D4 + [char]0x00DB
$PitchClasses = @{ C = 0; D = 2; E = 4; F = 5; G = 7; A = 9; B = 11 }
$FifthsByMajorPitchClass = @(0, -5, 2, -3, 4, -1, 6, 1, -4, 3, -2, 5)
$ModeOffsets = @{ ionian = 0; dorian = 2; phrygian = 4; lydian = 5; 'mixo-lydian' = 7; aeolian = 9 }
$ManifestItems = New-Object System.Collections.Generic.List[object]
$PsalmTextSourceCache = @{}
$PsalmMelodySourceCache = @{}

$Psalms = @($Psalms | ForEach-Object {
    $Value = ([string]$_).Trim()
    if ($Value -match '^(\d+)\.\.(\d+)$') {
        [int]$Matches[1]..[int]$Matches[2]
    } elseif ($Value -match ',') {
        $Value -split ',' | ForEach-Object { [int]$_.Trim() }
    } else {
        [int]$Value
    }
})

function Escape-Xml([string]$Value) {
    return [System.Security.SecurityElement]::Escape($Value)
}

function Decode-Html([string]$Value) {
    return $Value.Replace('&nbsp;', ' ').Replace('&amp;', '&').Replace('&lt;', '<').Replace('&gt;', '>').Replace('&quot;', '"').Replace('&#39;', "'")
}

function Get-SourcePath([string]$Directory, [int]$Number, [string]$Prefix) {
    return Join-Path $Directory ('{0}{1:D3}.json' -f $Prefix, $Number)
}

function Read-JsonFile([string]$Path) {
    return Get-Content $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Write-JsonFile([string]$Path, $Value) {
    $Directory = Split-Path -Parent $Path
    if (!(Test-Path $Directory)) { New-Item -ItemType Directory -Path $Directory | Out-Null }
    $Json = $Value | ConvertTo-Json -Depth 20
    [System.IO.File]::WriteAllText($Path, $Json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

function Get-KeyParts([string]$Key) {
    $Parts = @($Key -split '\s+' | Where-Object { $_ })
    if ($Parts.Count -lt 2) { throw "Unsupported key: $Key" }
    $Tonic = $Parts[0].Substring(0, 1).ToUpperInvariant()
    $Mode = $Parts[1].ToLowerInvariant()
    if (!$PitchClasses.ContainsKey($Tonic) -or !$ModeOffsets.ContainsKey($Mode)) { throw "Unsupported key: $Key" }
    [pscustomobject]@{ Tonic = $Tonic; Mode = $Mode }
}

function Get-KeyFifths([string]$Key) {
    $Parts = Get-KeyParts $Key
    $MajorPitchClass = ($PitchClasses[$Parts.Tonic] - $ModeOffsets[$Parts.Mode] + 120) % 12
    return $FifthsByMajorPitchClass[$MajorPitchClass]
}

function Get-KeyAltersFromFifths([int]$Fifths) {
    $Map = @{}
    $SharpOrder = @('F', 'C', 'G', 'D', 'A', 'E', 'B')
    $FlatOrder = @('B', 'E', 'A', 'D', 'G', 'C', 'F')
    if ($Fifths -gt 0) {
        for ($Index = 0; $Index -lt $Fifths; $Index++) { $Map[$SharpOrder[$Index]] = 1 }
    } elseif ($Fifths -lt 0) {
        for ($Index = 0; $Index -lt -$Fifths; $Index++) { $Map[$FlatOrder[$Index]] = -1 }
    }
    return $Map
}

function Get-KeyAlters([string]$Key) {
    return Get-KeyAltersFromFifths (Get-KeyFifths $Key)
}

function Read-AbcPsalm([int]$Number) {
    $Lines = Get-Content $AbcPath -Encoding UTF8
    $Start = -1
    for ($Index = 0; $Index -lt $Lines.Count; $Index++) {
        if ($Lines[$Index] -eq "X: $Number") {
            $Start = $Index
            break
        }
    }
    if ($Start -lt 0) { throw "Psalm $Number not found in psalmen.abc" }

    $End = $Lines.Count
    for ($Index = $Start + 1; $Index -lt $Lines.Count; $Index++) {
        if ($Lines[$Index] -match '^X: ') {
            $End = $Index
            break
        }
    }

    $Block = @($Lines[$Start..($End - 1)])
    $Composer = (($Block | Where-Object { $_ -match '^C: ' } | Select-Object -First 1) -replace '^C: ', '').Trim()
    $Key = (($Block | Where-Object { $_ -match '^K: ' } | Select-Object -First 1) -replace '^K: ', '').Trim()
    $MelodyLines = @($Block | Where-Object { $_ -and $_ -notmatch '^[A-Z]: ' } | ForEach-Object { ($_ -replace '\|', '' -replace '-', ' ').Trim() } | Where-Object { $_ })

    [pscustomobject]@{
        Composer = $Composer
        Key = $Key
        Lines = $MelodyLines
    }
}

function Get-AbcPsalm([int]$Number) {
    if ($PsalmMelodySourceCache.ContainsKey($Number)) { return $PsalmMelodySourceCache[$Number] }

    $Path = Get-SourcePath $PsalmMelodySourceDir $Number 'Psalm'
    if ((Test-Path $Path) -and !$RefreshSources) {
        $Source = Read-JsonFile $Path
        $Result = [pscustomobject]@{
            Composer = [string]$Source.composer
            Key = [string]$Source.key
            Lines = @($Source.lines | ForEach-Object { [string]$_ })
        }
    } else {
        $Parsed = Read-AbcPsalm $Number
        $Result = [pscustomobject]@{
            Composer = $Parsed.Composer
            Key = $Parsed.Key
            Lines = @($Parsed.Lines)
        }
        Write-JsonFile $Path ([pscustomobject]@{
            number = $Number
            source = 'psalmen.abc'
            composer = $Result.Composer
            key = $Result.Key
            lines = @($Result.Lines)
        })
    }

    $PsalmMelodySourceCache[$Number] = $Result
    return $Result
}

function Read-HtmlPsalmSource([int]$Psalm) {
    $Html = Get-Content $HtmlPath -Raw -Encoding UTF8
    $Pattern = "<div class=verse id=`"$Psalm`:(\d+)`"><p><strong class=verseno>[\s\S]*?</strong>([\s\S]*?)</div>"
    $Verses = @([regex]::Matches($Html, $Pattern) | ForEach-Object {
        $VerseNumber = [int]$_.Groups[1].Value
        if ($VerseNumber -gt 0 -or ($Psalm -eq 18 -and $VerseNumber -eq 0)) {
            [pscustomobject]@{
                number = $VerseNumber
                lines = @($_.Groups[2].Value.Replace('<br>', "`n") -replace '<[^>]+>', '' -split "`n" | ForEach-Object {
                    (Decode-Html $_).Replace([char]0x00A0, ' ') -replace '\s+', ' '
                } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
            }
        }
    } | Where-Object { $_ } | Sort-Object number)

    if ($Verses.Count -eq 0) { throw "Psalm $Psalm not found in oude.html" }
    return [pscustomobject]@{
        number = $Psalm
        title = "Psalm $Psalm"
        verses = $Verses
    }
}

function Get-PsalmTextSource([int]$Psalm) {
    if ($PsalmTextSourceCache.ContainsKey($Psalm)) { return $PsalmTextSourceCache[$Psalm] }

    $Path = Get-SourcePath $PsalmTextSourceDir $Psalm 'Psalm'
    if ((Test-Path $Path) -and !$RefreshSources) {
        $Source = Read-JsonFile $Path
    } else {
        $Source = Read-HtmlPsalmSource $Psalm
        Write-JsonFile $Path $Source
    }

    $PsalmTextSourceCache[$Psalm] = $Source
    return $Source
}

function Get-HtmlVerseLines([int]$Psalm, [int]$Verse) {
    $Source = Get-PsalmTextSource $Psalm
    $VerseSource = @($Source.verses | Where-Object { [int]$_.number -eq $Verse } | Select-Object -First 1)
    if (!$VerseSource) { throw "Psalm $Psalm`:$Verse not found in psalm text source" }
    return @($VerseSource.lines | ForEach-Object { [string]$_ })
}

function Get-HtmlVerseNumbers([int]$Psalm) {
    $Source = Get-PsalmTextSource $Psalm
    return @($Source.verses | ForEach-Object { [int]$_.number } | Sort-Object -Unique)
}

function Parse-AbcToken([string]$Token, [hashtable]$KeyAlters) {
    $Match = [regex]::Match($Token, '^([\^_=]?)([A-Ga-gz])(\d*)$')
    if (!$Match.Success) { throw "Unsupported ABC token: $Token" }

    $Accidental = $Match.Groups[1].Value
    $RawNote = $Match.Groups[2].Value
    $Length = if ($Match.Groups[3].Value) { [int]$Match.Groups[3].Value } else { 1 }
    if ($RawNote -eq 'z') {
        return [pscustomobject]@{ Rest = $true; Length = $Length; Step = $null; Alter = $null; Octave = $null }
    }

    $Step = $RawNote.ToUpperInvariant()
    $Alter = $null
    if ($Accidental -eq '^') { $Alter = 1 }
    elseif ($Accidental -eq '_') { $Alter = -1 }
    elseif ($Accidental -eq '=') { $Alter = 0 }
    elseif ($KeyAlters.ContainsKey($Step)) { $Alter = [int]$KeyAlters[$Step] }

    [pscustomobject]@{
        Rest = $false
        Length = $Length
        Step = $Step
        Alter = $Alter
        Octave = if ($RawNote -cmatch '[a-g]') { 5 } else { 4 }
    }
}

function Test-VowelAt([string]$Word, [int]$Index) {
    if ($Index + 1 -lt $Word.Length -and $Word.Substring($Index, 2).ToLowerInvariant() -eq 'ij') { return $true }
    return $Vowels.Contains($Word[$Index])
}

function Get-VowelGroupEnd([string]$Word, [int]$Index) {
    if ($Index + 1 -lt $Word.Length -and $Word.Substring($Index, 2).ToLowerInvariant() -eq 'ij') { return $Index + 2 }
    $Cursor = $Index + 1
    while ($Cursor -lt $Word.Length -and $Vowels.Contains($Word[$Cursor])) { $Cursor++ }
    return $Cursor
}

function Split-WordIntoSyllables([string]$Word) {
    $Punctuation = ''
    $PunctuationMatch = [regex]::Match($Word, '[,.!?;:''"\)\]]+$')
    if ($PunctuationMatch.Success) { $Punctuation = $PunctuationMatch.Value }
    $Base = if ($Punctuation) { $Word.Substring(0, $Word.Length - $Punctuation.Length) } else { $Word }
    if ($Base.Contains('_')) {
        $Syllables = New-Object System.Collections.Generic.List[string]
        $Parts = $Base.Split([char[]]@('_'), [System.StringSplitOptions]::None)
        for ($PartIndex = 0; $PartIndex -lt $Parts.Count; $PartIndex++) {
            if ($Parts[$PartIndex]) {
                foreach ($Syllable in @(Split-WordIntoSyllables $Parts[$PartIndex])) { $Syllables.Add($Syllable) }
            }
            if ($PartIndex -lt $Parts.Count - 1) { $Syllables.Add('') }
        }
        if ($Punctuation) {
            for ($Index = $Syllables.Count - 1; $Index -ge 0; $Index--) {
                if ($Syllables[$Index]) {
                    $Syllables[$Index] = $Syllables[$Index] + $Punctuation
                    break
                }
            }
        }
        return @($Syllables)
    }
    if ($Base.ToLowerInvariant() -eq 'godsdienst') { return @($Base.Substring(0, 4), $Base.Substring(4) + $Punctuation) }
    if (!$Base -or $Base.Length -le 3 -or $Base.ToLowerInvariant() -match "^['']?[kst]$") { return @($Word) }

    $Groups = New-Object System.Collections.Generic.List[object]
    $Index = 0
    while ($Index -lt $Base.Length) {
        if (Test-VowelAt $Base $Index) {
            $End = Get-VowelGroupEnd $Base $Index
            $Groups.Add([pscustomobject]@{ Start = $Index; End = $End })
            $Index = $End
        } else {
            $Index++
        }
    }
    if ($Groups.Count -le 1) { return @($Word) }

    $Boundaries = New-Object System.Collections.Generic.List[int]
    $Boundaries.Add(0)
    for ($Index = 0; $Index -lt $Groups.Count - 1; $Index++) {
        $CurrentEnd = $Groups[$Index].End
        $NextStart = $Groups[$Index + 1].Start
        $ClusterLength = $NextStart - $CurrentEnd
        $Boundary = if ($ClusterLength -le 1) { $CurrentEnd } else { $CurrentEnd + 1 }
        if ($Boundary -gt 0 -and $Boundary -lt $Base.Length -and $Base.Substring($Boundary - 1, 2).ToLowerInvariant() -eq 'ch') {
            $Cluster = $Base.Substring($CurrentEnd, $NextStart - $CurrentEnd).ToLowerInvariant()
            $Boundary = if ($Cluster -eq 'ch') { $CurrentEnd } else { $Boundary + 1 }
        }
        $Boundaries.Add($Boundary)
    }
    $Boundaries.Add($Base.Length)

    $Syllables = New-Object System.Collections.Generic.List[string]
    for ($Index = 0; $Index -lt $Boundaries.Count - 1; $Index++) {
        $Text = $Base.Substring($Boundaries[$Index], $Boundaries[$Index + 1] - $Boundaries[$Index])
        if ($Text) { $Syllables.Add($Text) }
    }
    Repair-SyllableBoundaries $Syllables
    if ($Punctuation -and $Syllables.Count -gt 0) { $Syllables[$Syllables.Count - 1] = $Syllables[$Syllables.Count - 1] + $Punctuation }
    return @($Syllables)
}

function Repair-SyllableBoundaries($Syllables) {
    for ($Index = 0; $Index -lt $Syllables.Count - 1; $Index++) {
        $Left = [string]$Syllables[$Index]
        $Right = [string]$Syllables[$Index + 1]
        $LeftLower = $Left.ToLowerInvariant()
        $RightLower = $Right.ToLowerInvariant()

        if ($LeftLower.EndsWith('s') -and $RightLower.StartsWith('ch')) {
            $Syllables[$Index] = $Left.Substring(0, $Left.Length - 1)
            $Syllables[$Index + 1] = $Left.Substring($Left.Length - 1, 1) + $Right
        } elseif ($LeftLower.EndsWith('d') -and $RightLower.StartsWith('sv')) {
            $Syllables[$Index] = $Left + $Right.Substring(0, 1)
            $Syllables[$Index + 1] = $Right.Substring(1)
        } elseif ($LeftLower -eq 'god' -and $RightLower.StartsWith('s')) {
            $Syllables[$Index] = $Left + $Right.Substring(0, 1)
            $Syllables[$Index + 1] = $Right.Substring(1)
        } elseif ($LeftLower -eq 'dood' -and $RightLower.StartsWith('sge')) {
            $Syllables[$Index] = $Left + $Right.Substring(0, 1)
            $Syllables[$Index + 1] = $Right.Substring(1)
        } elseif ($LeftLower.EndsWith('d') -and $RightLower.StartsWith('stge')) {
            $Syllables[$Index] = $Left + $Right.Substring(0, 2)
            $Syllables[$Index + 1] = $Right.Substring(2)
        } elseif (($LeftLower -eq 'leid' -or $LeftLower -eq 'een') -and $RightLower.StartsWith('sg')) {
            $Syllables[$Index] = $Left + $Right.Substring(0, 1)
            $Syllables[$Index + 1] = $Right.Substring(1)
        } elseif ($LeftLower -eq 'geg' -and $RightLower.StartsWith('r')) {
            $Syllables[$Index] = $Left.Substring(0, $Left.Length - 1)
            $Syllables[$Index + 1] = $Left.Substring($Left.Length - 1, 1) + $Right
        } elseif ($LeftLower.EndsWith('von') -and $RightLower.StartsWith('dst')) {
            $Syllables[$Index] = $Left + $Right.Substring(0, 1)
            $Syllables[$Index + 1] = $Right.Substring(1)
        } elseif (($LeftLower -eq 'trot' -or $LeftLower -eq 'groot') -and $RightLower.StartsWith('sheid')) {
            $Syllables[$Index] = $Left + $Right.Substring(0, 1)
            $Syllables[$Index + 1] = $Right.Substring(1)
        } elseif ($LeftLower.EndsWith('t') -and ($RightLower.StartsWith('saard') -or $RightLower.StartsWith('ssteen') -or $RightLower.StartsWith('sheer'))) {
            $Syllables[$Index] = $Left + $Right.Substring(0, 1)
            $Syllables[$Index + 1] = $Right.Substring(1)
        } elseif ($LeftLower.EndsWith('on') -and $RightLower.StartsWith('t') -and $Right.Length -gt 1) {
            $Syllables[$Index] = $Left + $Right.Substring(0, 1)
            $Syllables[$Index + 1] = $Right.Substring(1)
        }
    }
}

function Test-LyricPrefix([string]$Word) {
    $Trimmed = $Word.Trim().TrimEnd(',', '.', ';', ':', '!', '?')
    if ($Trimmed.Length -ne 2) { return $false }

    $Prefix = $Trimmed.Substring(0, 1)
    $Letter = $Trimmed.Substring(1, 1)
    return @("'", [string][char]0x2018, [string][char]0x2019, '`') -contains $Prefix -and $Letter -match '^[A-Za-z]$'
}

function Get-LyricTokens([string]$Line, [int]$TargetCount) {
    $Tokens = New-Object System.Collections.ArrayList
    $PendingPrefix = ''
    foreach ($Word in @($Line -split '\s+' | Where-Object { $_ })) {
        $TrimmedWord = $Word.Trim()
        if (Test-LyricPrefix $TrimmedWord) {
            $PendingPrefix = $(if ($PendingPrefix) { "$PendingPrefix $TrimmedWord" } else { $TrimmedWord })
            continue
        }

        $Syllables = @(Split-WordIntoSyllables $Word)
        if ($PendingPrefix -and $Syllables.Count -gt 0) {
            $Syllables[0] = "$PendingPrefix $($Syllables[0])"
            $PendingPrefix = ''
        }

        for ($Index = 0; $Index -lt $Syllables.Count; $Index++) {
            $Syllabic = if ($Syllables.Count -eq 1) { 'single' } elseif ($Index -eq 0) { 'begin' } elseif ($Index -eq $Syllables.Count - 1) { 'end' } else { 'middle' }
            [void]$Tokens.Add([pscustomobject]@{ Text = $Syllables[$Index]; Syllabic = $Syllabic })
        }
    }

    if ($PendingPrefix) {
        [void]$Tokens.Add([pscustomobject]@{ Text = $PendingPrefix; Syllabic = 'single' })
    }

    while ($Tokens.Count -gt $TargetCount -and $Tokens.Count -gt 1) {
        $Last = $Tokens[$Tokens.Count - 1]
        $Tokens.RemoveAt($Tokens.Count - 1)
        $Tokens[$Tokens.Count - 1] = [pscustomobject]@{ Text = $Tokens[$Tokens.Count - 1].Text + ' ' + $Last.Text; Syllabic = 'single' }
    }
    return @($Tokens)
}

function Get-PitchXml($Note) {
    if ($Note.Rest) { return '<rest />' }
    $AlterXml = if ($null -ne $Note.Alter -and $Note.Alter -ne 0) { "<alter>$($Note.Alter)</alter>" } else { '' }
    return "<pitch><step>$($Note.Step)</step>$AlterXml<octave>$($Note.Octave)</octave></pitch>"
}

function Get-NoteXml($Note, [int]$Duration, [string]$Type, $Lyric, [string]$Tie) {
    $TieTag = if ($Tie) { "<tie type=`"$Tie`"/>" } else { '' }
    $TiedTag = if ($Tie) { "<notations><tied type=`"$Tie`"/></notations>" } else { '' }
    $LyricTag = if ($Lyric) {
        "<lyric number=`"1`"><syllabic>$($Lyric.Syllabic)</syllabic><text>$(Escape-Xml $Lyric.Text)</text></lyric>"
    } else { '' }
    return "      <note>$(Get-PitchXml $Note)<duration>$Duration</duration>$TieTag<voice>1</voice><type>$Type</type>$TiedTag$LyricTag</note>"
}

function Get-NoteXmlParts($Note, [int]$BaseLength, $Lyric) {
    $QuarterUnits = [int][Math]::Round($Note.Length / $BaseLength)
    if ($QuarterUnits -eq 4) {
        return @((Get-NoteXml $Note 8 'whole' $Lyric $null))
    }

    $Result = New-Object System.Collections.Generic.List[string]
    $Remaining = $QuarterUnits
    $First = $true
    while ($Remaining -gt 0) {
        $Unit = if ($Remaining -ge 2) { 2 } else { 1 }
        $Duration = $Unit * 2
        $Type = if ($Unit -eq 2) { 'half' } else { 'quarter' }
        $Tie = $null
        if (!$Note.Rest -and $QuarterUnits -gt 2) {
            if ($First) { $Tie = 'start' }
            elseif ($Remaining -eq $Unit) { $Tie = 'stop' }
            else { $Tie = 'continue' }
        }
        $Result.Add((Get-NoteXml $Note $Duration $Type $(if ($First) { $Lyric } else { $null }) $Tie))
        $Remaining -= $Unit
        $First = $false
    }
    return @($Result)
}

function Get-DisplayTextLine([string]$Line) {
    return ($Line -replace '_+', '')
}

function Get-PsalmLineLyricSlotCount($Notes) {
    $LyricNoteCount = @($Notes | Where-Object { !$_.Rest }).Count
    $LastVocalNoteIndex = -1
    for ($NoteIndex = 0; $NoteIndex -lt $Notes.Count; $NoteIndex++) {
        if (!$Notes[$NoteIndex].Rest) { $LastVocalNoteIndex = $NoteIndex }
    }
    $TrailingRestCount = @($Notes | Select-Object -Skip ($LastVocalNoteIndex + 1) | Where-Object { $_.Rest }).Count
    return $LyricNoteCount + $TrailingRestCount
}

function Get-PsalmActiveNotesByLine([string[]]$TextLines, $NotesByLine, [string]$Label) {
    if ($TextLines.Count -gt 0 -and $TextLines.Count -lt $NotesByLine.Count) {
        $ActiveNotesByLine = New-Object System.Collections.ArrayList
        for ($LineIndex = 0; $LineIndex -lt $TextLines.Count; $LineIndex++) {
            [void]$ActiveNotesByLine.Add($NotesByLine[$LineIndex])
        }
        return $ActiveNotesByLine
    }

    if ($TextLines.Count -gt $NotesByLine.Count) {
        Write-Warning "$Label line mismatch: text=$($TextLines.Count), abc=$($NotesByLine.Count)"
    }

    return $NotesByLine
}

function Get-PsalmLyricLineSource([string]$Line, $Notes) {
    $LyricSlotCount = Get-PsalmLineLyricSlotCount $Notes
    $Tokens = @(Get-LyricTokens $Line $LyricSlotCount)
    return [pscustomobject]@{
        raw = $Line
        display = Get-DisplayTextLine $Line
        tokens = @($Tokens | ForEach-Object {
            [pscustomobject]@{
                text = [string]$_.Text
                syllabic = [string]$_.Syllabic
            }
        })
    }
}

function Write-PsalmLyricSource([int]$Psalm, $NotesByLine) {
    $TextSource = Get-PsalmTextSource $Psalm
    $Verses = @($TextSource.verses | Sort-Object number | ForEach-Object {
        $VerseNumber = [int]$_.number
        $TextLines = @($_.lines | ForEach-Object { [string]$_ })
        $ActiveNotesByLine = Get-PsalmActiveNotesByLine $TextLines $NotesByLine "Psalm $Psalm`:$VerseNumber"
        [pscustomobject]@{
            number = $VerseNumber
            lines = @(for ($LineIndex = 0; $LineIndex -lt $ActiveNotesByLine.Count; $LineIndex++) {
                Get-PsalmLyricLineSource $(if ($LineIndex -lt $TextLines.Count) { $TextLines[$LineIndex] } else { '' }) $ActiveNotesByLine[$LineIndex]
            })
        }
    })

    Write-JsonFile (Get-SourcePath $PsalmLyricSourceDir $Psalm 'Psalm') ([pscustomobject]@{
        number = $Psalm
        title = [string]$TextSource.title
        sourceText = "content/psalmen/texts/Psalm$($Psalm.ToString('000')).json"
        sourceMelody = "content/psalmen/melodies/Psalm$($Psalm.ToString('000')).json"
        verses = $Verses
    })
}

function Add-ManifestItem([int]$Psalm, [int]$CurrentVerse, [string[]]$TextLines) {
    $FileName = "Psalm$($Psalm)_v$($CurrentVerse).xml"
    [void]$ManifestItems.Add([pscustomobject]@{
        book = 'psalms'
        number = $Psalm
        verse = $CurrentVerse
        fileName = $FileName
        firstLine = $(if ($TextLines.Count -gt 0) { Get-DisplayTextLine $TextLines[0] } else { "Psalm $Psalm vers $CurrentVerse" })
    })
}

function Write-BundledManifest() {
    $ManifestJson = [pscustomobject]@{ items = $ManifestItems } | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText((Join-Path $OutDir 'bundled-content-manifest.json'), $ManifestJson, [System.Text.UTF8Encoding]::new($false))
    Write-Output "Wrote bundled-content-manifest.json with $($ManifestItems.Count) items."
}

if ($ManifestOnly) {
    foreach ($Psalm in $Psalms) {
        $Abc = Get-AbcPsalm $Psalm
        $KeyAlters = Get-KeyAlters $Abc.Key
        $NotesByLine = New-Object System.Collections.ArrayList
        foreach ($Line in $Abc.Lines) {
            $ParsedLine = @($Line -split '\s+' | Where-Object { $_ } | ForEach-Object { Parse-AbcToken $_ $KeyAlters })
            [void]$NotesByLine.Add($ParsedLine)
        }
        Write-PsalmLyricSource $Psalm $NotesByLine

        $Verses = if ($AllVerses) { @(Get-HtmlVerseNumbers $Psalm) } else { @($Verse) }
        foreach ($CurrentVerse in $Verses) {
            Add-ManifestItem $Psalm $CurrentVerse @(Get-HtmlVerseLines $Psalm $CurrentVerse)
        }
    }
    Write-BundledManifest
    return
}

foreach ($Psalm in $Psalms) {
    $Abc = Get-AbcPsalm $Psalm
    $KeyAlters = Get-KeyAlters $Abc.Key
    $NotesByLine = New-Object System.Collections.ArrayList
    foreach ($Line in $Abc.Lines) {
        $ParsedLine = @($Line -split '\s+' | Where-Object { $_ } | ForEach-Object { Parse-AbcToken $_ $KeyAlters })
        [void]$NotesByLine.Add($ParsedLine)
    }
    $BaseLength = ($NotesByLine | ForEach-Object { $_ } | ForEach-Object { $_.Length } | Measure-Object -Minimum).Minimum
    Write-PsalmLyricSource $Psalm $NotesByLine
    $Verses = if ($AllVerses) { @(Get-HtmlVerseNumbers $Psalm) } else { @($Verse) }
    foreach ($CurrentVerse in $Verses) {
        $TextLines = @(Get-HtmlVerseLines $Psalm $CurrentVerse)
        $ActiveNotesByLine = Get-PsalmActiveNotesByLine $TextLines $NotesByLine "Psalm $Psalm`:$CurrentVerse"

        $Xml = New-Object System.Collections.Generic.List[string]
        $Xml.Add('<?xml version="1.0" encoding="UTF-8"?>')
        $Xml.Add('<score-partwise version="4.0">')
        $Xml.Add("  <work><work-title>Psalm $Psalm vers $CurrentVerse</work-title></work>")
        $Xml.Add("  <identification><creator type=`"composer`">$(Escape-Xml $Abc.Composer)</creator></identification>")
        $Xml.Add('  <part-list>')
        $Xml.Add('    <score-part id="P1"><part-name>Psalm</part-name></score-part>')
        $Xml.Add('  </part-list>')
        $Xml.Add('  <part id="P1">')

        for ($LineIndex = 0; $LineIndex -lt $ActiveNotesByLine.Count; $LineIndex++) {
            $Notes = @($ActiveNotesByLine[$LineIndex])
            $LyricNoteCount = @($Notes | Where-Object { !$_.Rest }).Count
            $LastVocalNoteIndex = -1
            for ($NoteIndex = 0; $NoteIndex -lt $Notes.Count; $NoteIndex++) {
                if (!$Notes[$NoteIndex].Rest) { $LastVocalNoteIndex = $NoteIndex }
            }
            $TrailingRestCount = @($Notes | Select-Object -Skip ($LastVocalNoteIndex + 1) | Where-Object { $_.Rest }).Count
            $LyricSlotCount = $LyricNoteCount + $TrailingRestCount
            $Lyrics = @(Get-LyricTokens $(if ($LineIndex -lt $TextLines.Count) { $TextLines[$LineIndex] } else { '' }) $LyricSlotCount)
            $QuarterUnits = 0
            foreach ($Note in $Notes) { $QuarterUnits += [int][Math]::Round($Note.Length / $BaseLength) }

            $Xml.Add("    <measure number=`"$($LineIndex + 1)`">")
            $TimeXml = "<time print-object=`"no`"><beats>$QuarterUnits</beats><beat-type>4</beat-type></time>"
            if ($LineIndex -eq 0) {
                $Xml.Add("      <attributes><divisions>2</divisions><key><fifths>$(Get-KeyFifths $Abc.Key)</fifths></key>$TimeXml<clef><sign>G</sign><line>2</line></clef></attributes>")
            } else {
                $Xml.Add("      <attributes>$TimeXml</attributes>")
            }

            $LyricIndex = 0
            for ($NoteIndex = 0; $NoteIndex -lt $Notes.Count; $NoteIndex++) {
                $Note = $Notes[$NoteIndex]
                $Lyric = $null
                if ((!$Note.Rest -or $NoteIndex -gt $LastVocalNoteIndex) -and $LyricIndex -lt $Lyrics.Count) {
                    $Lyric = $Lyrics[$LyricIndex]
                    $LyricIndex++
                }
                foreach ($NoteXml in @(Get-NoteXmlParts $Note $BaseLength $Lyric)) { $Xml.Add($NoteXml) }
            }
            $Xml.Add('    </measure>')
        }

        $Xml.Add('  </part>')
        $Xml.Add('</score-partwise>')
        $FileName = "Psalm$($Psalm)_v$($CurrentVerse).xml"
        [System.IO.File]::WriteAllLines((Join-Path $OutDir $FileName), $Xml, [System.Text.UTF8Encoding]::new($false))
        Add-ManifestItem $Psalm $CurrentVerse $TextLines
    }
    Write-Output "Psalm $Psalm`: shortest ABC length $BaseLength mapped to quarter note; generated $($Verses.Count) verse(s)."
}

if ($WriteManifest) {
    Write-BundledManifest
}

package nl.psalmbladmuziek.app

data class PsalmCategory(val name: String, val psalms: Set<Int>, val description: String)

/** Categorie 0: geen filter actief; beschrijving van het Psalmenboek als geheel. */
val PSALM_CATEGORY_DEFAULT = PsalmCategory(
    "Het Boek der Psalmen",
    (1..150).toSet(),
    "Het boek Psalmen is het lied- en gebedenboek van Isra\u00ebl en van de oudtestamentische kerk. Het bevat 150 psalmen, waarin het geestelijke leven voor Gods aangezicht breed doorklinkt: lofprijzing, dankzegging, gebed, schuldbelijdenis, strijd, aanvechting, vertrouwen en verwachting.\n\nIn de gereformeerde traditie nemen de psalmen een bijzondere plaats in. De Geneefse psalmen werden in de tijd van de Reformatie berijmd voor de gemeentezang en zijn in de gereformeerde gezindte nog steeds zeer bekend. Zij vormen een vertrouwde taal voor gebed en lofzang.\n\nBinnen de gebruikelijke christelijke indeling van het Oude Testament behoort Psalmen tot de dichterlijke of po\u00ebtische boeken: Job, Psalmen, Spreuken, Prediker en Hooglied. Het Psalmenboek staat daarbij ongeveer in het midden van de Bijbel.\n\nDe gebruikelijke protestantse nummering volgt de Hebreeuwse telling. In de Septuaginta (LXX) en de daarop gebaseerde tradities wijkt de nummering op enkele plaatsen af, doordat bepaalde psalmen daar anders zijn samengevoegd of gesplitst.\n\nIn de Hebreeuwse Bijbel (TeNaCh) behoort Psalmen tot de Ketuvim (Geschriften) en vormt het daar het eerste boek van dit derde hoofddeel. Jezus verwijst naar deze driedeling in Lukas 24:44 wanneer Hij spreekt over \u201cde Wet van Mozes, de Profeten en de Psalmen\u201d. De naam \u201cPsalmen\u201d wordt daarbij vaak gezien als aanduiding van het gehele derde deel van de Hebreeuwse Bijbel.\n\nHet Psalmenboek is opgebouwd uit vijf boeken. Deze vijfdeling wordt vaak in verband gebracht met de vijf boeken van Mozes. Elk boek van de Psalmen eindigt met een lofprijzing, waardoor het geheel toewerkt naar de grote afsluitende lofzang van Psalm 146-150.\n\nNaast de 150 psalmen bevatten veel uitgaven van de psalmberijming ook de Enige Gezangen. Deze bestaan onder meer uit berijmingen van de Tien Geboden, de lofzangen van Maria, Zacharias en Simeon, het Gebed des HEEREN, de Twaalf Artikelen des Geloofs en enkele gezangen voor dagelijks gebruik.\nSommige uitgaven bevatten daarnaast ook Psalm 151, het zogenoemde Eigen Geschrift Davids."
)

val PSALM_CATEGORIES: List<PsalmCategory> = listOf(

    PsalmCategory(
        "Boek 1",
        (1..41).toSet(),
        "Het eerste boek opent het Psalmenboek met twee psalmen die vaak worden gezien als een inleiding op het geheel. Daarin komen onder andere de weg van de rechtvaardige en de goddeloze, de vreze des HEEREN, Gods onderwijs en de door God aangestelde Koning naar voren. Veel psalmen in dit eerste boek zijn verbonden met David.\n\nDe toon is vaak persoonlijk: gebed in nood, strijd met vijanden, vertrouwen op Gods bescherming, schuldbesef en verlangen naar oprechte dienst aan God. Tegelijk klinkt steeds de zekerheid dat de HEERE Zijn volk niet verlaat. Dit eerste boek eindigt met een lofprijzing: Geloofd zij de HEERE, de God Isra\u00ebls, van eeuwigheid en tot eeuwigheid. Amen, ja, amen."
    ),

    PsalmCategory(
        "Boek 2",
        (42..72).toSet(),
        "Het tweede boek begint met diep verlangen naar God: Gelijk een hert schreeuwt naar de waterstromen. Naast psalmen van David bevat dit boek ook psalmen van de zonen van Korach. De toon is breder dan alleen persoonlijk; ook de nood en verwachting van Gods volk komen sterk naar voren.\n\nIn dit boek vinden we gebeden om verlossing, strijd, schuldbelijdenis, lof en koninklijke verwachting. Psalm 72 sluit af met het gebed om een rechtvaardige regering en met een lofprijzing op Gods heerlijke Naam. Daarmee eindigt ook dit tweede boek in lof."
    ),

    PsalmCategory(
        "Boek 3",
        (73..89).toSet(),
        "Het derde boek bevat veel psalmen van Asaf en behandelt regelmatig de nood van Gods volk, de verwoesting van het heiligdom, de voorspoed van de goddelozen en vragen rond Gods verbond.\n\nToch wordt de lezer niet bij de duisternis gelaten. In het heiligdom leert de dichter opnieuw zien dat God rechtvaardig is en Zijn verbond niet vergeet. Ook wanneer de koninklijke verwachting in Psalm 89 onder grote spanning staat, eindigt het boek met lof: Geloofd zij de HEERE in eeuwigheid. Amen, ja, amen."
    ),

    PsalmCategory(
        "Boek 4",
        (90..106).toSet(),
        "Het vierde boek opent met Psalm 90, het gebed van Mozes. Tegenover de kortheid en vergankelijkheid van het mensenleven staat de eeuwigheid van God: Van eeuwigheid tot eeuwigheid zijt Gij God. Dit boek richt de blik sterk op Gods regering.\n\nMeerdere psalmen verkondigen dat de HEERE regeert. Dat is een antwoord op de nood en vragen van het voorgaande boek. Niet de mens, niet de volken en niet aardse machten hebben het laatste woord, maar de HEERE. Het boek eindigt opnieuw met lofprijzing: Geloofd zij de HEERE, de God Isra\u00ebls, van eeuwigheid tot eeuwigheid. En al het volk zegge: Amen. Halleluja."
    ),

    PsalmCategory(
        "Boek 5",
        (107..150).toSet(),
        "Het vijfde boek staat sterk in het teken van verlossing, dankbaarheid, Gods Woord, de tocht naar Sion en de lof op de HEERE. Hier vinden we onder andere Psalm 119 over Gods wet, de bedevaartspsalmen en verschillende psalmen van David.\n\nHet laatste deel van het Psalmenboek eindigt met Psalm 146-150, waarin de oproep tot lofprijzing centraal staat."
    ),

    PsalmCategory(
        "Psalmen van David",
        setOf(3,4,5,6,7,8,9,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,
              30,31,32,34,35,36,37,38,39,40,41,51,52,53,54,55,56,57,58,59,60,61,62,63,
              64,65,68,69,70,86,101,103,108,109,110,122,124,131,133,138,139,140,141,142,
              143,144,145),
        "David was de tweede koning van Isra\u00ebl en wordt in de Bijbel genoemd een man naar Gods hart. David was de herdersjongen uit Bethlehem die door de HEERE tot koning over Isra\u00ebl werd geroepen. Hij was de zoon van Isa\u00ef, de overwinnaar van Goliath, de vervolgde vluchteling voor Saul en later de koning aan wie de HEERE grote beloften gaf.\n\nDe psalmen van David laten veel zien van zijn omgang met God. Er klinkt gebed in nood, roep om bewaring, schuldbelijdenis, verwondering over genade, lof na uitredding en vertrouwen op Gods verbond. Daardoor nemen de Davidische psalmen een centrale plaats in het Psalmenboek in."
    ),

    PsalmCategory(
        "Psalmen van Asaf",
        setOf(50,73,74,75,76,77,78,79,80,81,82,83),
        "Asaf was een zanger en zangleider in de dienst van de HEERE. Zijn psalmen hebben vaak een ernstige, onderwijzende en gemeenschappelijke toon. Niet alleen het persoonlijke geloofsleven, maar ook de toestand van Gods volk staat centraal.\n\nIn deze psalmen wordt stilgestaan bij Gods rechtvaardigheid, Zijn oordeel, de voorspoed van de goddelozen, de geschiedenis van Isra\u00ebl en de nood van het heiligdom."
    ),

    PsalmCategory(
        "Psalmen van de zonen van Korach",
        setOf(42,44,45,46,47,48,49,84,85,87,88),
        "De zonen van Korach waren verbonden met de tempelzang. Hun psalmen hebben vaak een rijke dichterlijke toon en spreken veel over verlangen naar God, het huis des HEEREN, Sion, Gods nabijheid en vertrouwen in nood.\n\nSommige van deze psalmen zijn zeer bekend, zoals Psalm 42 over het dorsten naar God en Psalm 84 over het verlangen naar Gods woningen. Ook diepe nood ontbreekt niet, zoals in Psalm 88. Daardoor vormen deze psalmen een eigen, herkenbare groep."
    ),

    PsalmCategory(
        "Psalm van Mozes",
        setOf(90),
        "Psalm 90 wordt in het opschrift verbonden met Mozes, de man Gods. Deze psalm plaatst de kortheid en vergankelijkheid van het mensenleven tegenover de eeuwigheid van God. Hij is ernstig, bezinnend en vol gebed om wijsheid.\n\nPsalm 90 wordt vaak gebruikt bij jaarwisseling, rouw, ouderdom en momenten waarop de vergankelijkheid van het leven nadrukkelijk gevoeld wordt."
    ),

    PsalmCategory(
        "Psalmen van Salomo",
        setOf(72,127),
        "Salomo was de zoon van David en koning van Isra\u00ebl. Twee psalmen worden met hem verbonden. Psalm 72 ziet op het karakter van een rechtvaardige koning: vrede, recht, bescherming van armen en wereldwijde zegen. Psalm 127 spreekt over Gods zegen over huis, arbeid en gezin.\n\nDeze categorie verbindt koninklijke verwachting met de afhankelijkheid van Gods zegen in het gewone leven."
    ),

    PsalmCategory(
        "Psalm van Heman de Ezrahiet",
        setOf(88),
        "Psalm 88 is in het opschrift verbonden met Heman de Ezrahiet. Het is een van de donkerste psalmen van het Psalmenboek. De dichter klaagt onder diepe benauwdheid en ervaart weinig licht of uitkomst."
    ),

    PsalmCategory(
        "Psalm van Ethan de Ezrahiet",
        setOf(89),
        "Psalm 89 is in het opschrift verbonden met Ethan de Ezrahiet. De psalm begint met Gods goedertierenheid en trouw, en herinnert aan de beloften aan David. Daarna volgt de pijnlijke klacht dat de werkelijkheid daarmee in spanning lijkt te staan."
    ),

    PsalmCategory(
        "Psalmen zonder genoemd auteur",
        setOf(1,2,10,33,43,66,67,71,91,92,93,94,95,96,97,98,99,100,102,104,105,106,107,
              111,112,113,114,115,116,117,118,119,120,121,123,125,126,128,129,130,132,134,
              135,136,137,146,147,148,149,150),
        "Een aantal psalmen heeft geen genoemde auteur in het opschrift. Dat betekent niet dat zij minder belangrijk zijn. Ook deze psalmen behoren volledig tot het Psalmenboek."
    ),

    PsalmCategory(
        "Boetpsalmen",
        setOf(6,32,38,51,102,130,143),
        "De boetpsalmen zijn psalmen van schuldbelijdenis, berouw en roep om genade. Zij laten zien dat zonde niet alleen verdriet geeft vanwege de gevolgen, maar vooral omdat zij tegen God bedreven is."
    ),

    PsalmCategory(
        "Klaagpsalmen",
        setOf(3,4,5,6,7,10,12,13,17,22,25,26,28,31,35,38,39,42,43,44,51,54,55,56,57,
              59,60,61,64,69,70,71,74,77,79,80,83,86,88,90,102,109,120,130,140,141,142,143),
        "Klaagpsalmen brengen nood, verdriet, vervolging, eenzaamheid of aanvechting voor Gods aangezicht. Zij spreken eerlijk over moeite, maar doen dat biddend tot de HEERE."
    ),

    PsalmCategory(
        "Dankpsalmen",
        setOf(18,30,32,34,40,65,66,67,75,92,103,107,108,116,118,124,126,129,136,138,145),
        "Dankpsalmen bezingen Gods goedheid na ervaren redding, verhoring of bewaring. De dichter ziet terug op nood waaruit de HEERE heeft verlost en roept zichzelf of de gemeente op om God te danken."
    ),

    PsalmCategory(
        "Lofpsalmen",
        setOf(8,19,29,33,47,48,65,66,67,68,95,96,97,98,99,100,103,104,105,111,113,114,
              115,116,117,118,135,136,145,146,147,148,149,150),
        "Lofpsalmen hebben als hoofdtoon de verheerlijking van God. Zij prijzen Zijn Naam, grootheid, goedheid, macht, trouw, scheppingswerk en regering over alle dingen.\nDe psalmen 146-150 vormen de grote lofprijzende finale van het Psalmenboek."
    ),

    PsalmCategory(
        "Vertrouwenspsalmen",
        setOf(3,4,11,16,23,27,31,46,56,62,63,91,121,125,131),
        "Vertrouwenspsalmen spreken van rust in God te midden van dreiging, onzekerheid of gevaar. De dichter ziet niet alleen op de omstandigheden, maar vooral op de HEERE als Herder, Schuilplaats, Bewaarder en Rots."
    ),

    PsalmCategory(
        "Wijsheids- en onderwijzingspsalmen",
        setOf(1,14,15,19,25,32,34,36,37,49,50,52,53,73,78,82,94,111,112,119,127,128,133),
        "Deze psalmen onderwijzen over de weg van de rechtvaardige en de weg van de goddeloze. Zij spreken over de vreze des HEEREN, Gods wet, de vergankelijkheid van rijkdom, het gezin, arbeid en de juiste levenswandel."
    ),

    PsalmCategory(
        "Psalmen over Gods Woord",
        setOf(1,19,119),
        "Deze psalmen hebben bijzondere aandacht voor Gods wet, getuigenissen, inzettingen, geboden en onderwijs. Zij laten zien dat Gods Woord niet alleen regel is, maar ook licht, vreugde, wijsheid en troost."
    ),

    PsalmCategory(
        "Messiaanse psalmen",
        setOf(2,8,16,22,24,31,40,41,45,68,69,72,89,102,110,118,129),
        "Deze psalmen zien uit naar de door God gezalfde Koning en spreken over Zijn koningschap, lijden, overwinning, verhoging en heerschappij. In het Nieuwe Testament worden verschillende passages uit deze psalmen rechtstreeks op Jezus Christus toegepast.\n\nRechtstreeks op Christus toegepast in het Nieuwe Testament:\nPsalm 2 \u2014 Handelingen 4:25-28; 13:33; Hebre\u00ebn 1:5; 5:5; Openbaring 2:27; 12:5; 19:15\nPsalm 8 \u2014 Matthe\u00fcus 21:16; 1 Korinthe 15:25-28; Hebre\u00ebn 2:6-10\nPsalm 16 \u2014 Handelingen 2:25-32; 13:35-37\nPsalm 22 \u2014 Matthe\u00fcus 27:35, 39, 43, 46; Markus 15:24, 29, 34; Lukas 23:34-35; Johannes 19:23-24; Hebre\u00ebn 2:12\nPsalm 31 \u2014 Lukas 23:46\nPsalm 34 \u2014 Johannes 19:36\nPsalm 35 \u2014 Johannes 15:25\nPsalm 40 \u2014 Hebre\u00ebn 10:5-10\nPsalm 41 \u2014 Johannes 13:18\nPsalm 45 \u2014 Hebre\u00ebn 1:8-9\nPsalm 68 \u2014 Efeze 4:8\nPsalm 69 \u2014 Johannes 2:17; 15:25; Romeinen 15:3\nPsalm 97 \u2014 Hebre\u00ebn 1:6\nPsalm 102 \u2014 Hebre\u00ebn 1:10-12\nPsalm 110 \u2014 Matthe\u00fcus 22:44; Markus 12:36; Lukas 20:42-43; Handelingen 2:34-35; Hebre\u00ebn 1:13; 5:6; 6:20; 7:17,21\nPsalm 118 \u2014 Matthe\u00fcus 21:42; Markus 12:10-11; Lukas 20:17; Handelingen 4:11; 1 Petrus 2:7\n\nAndere psalmen worden in de christelijke traditie eveneens messiaans verstaan, omdat zij spreken over de koning uit het huis van David, zijn regering, Gods verbond met hem of de toekomstige verlossing van Gods volk. Niet iedere dergelijke psalm wordt echter in het Nieuwe Testament rechtstreeks op Christus toegepast."
    ),

    PsalmCategory(
        "Koninklijke psalmen",
        setOf(2,18,20,21,45,47,72,89,93,101,110,132,144),
        "Koninklijke psalmen spreken over de koning, zijn roeping, regering, overwinning, recht en de beloften aan Davids huis. Zij hebben een historische plaats in Isra\u00ebl, maar wijzen ook boven aardse koningen uit."
    ),

    PsalmCategory(
        "Liederen Hamma\u00e4loth",
        setOf(120,121,122,123,124,125,126,127,128,129,130,131,132,133,134),
        "Hebr. \u05d4\u05b7\u05de\u05bc\u05b7\u05e2\u05b2\u05dc\u05d5\u05b9\u05ea (*hamma\u02bfalot*), letterlijk: \u2018de opgangen\u2019.\n\nDe vijftien bedevaartspsalmen (120-134) dragen in hun opschrift de aanduiding Shir Hamma\u00e4loth, \u2018Lied van de opgangen\u2019. Ze worden vanouds verbonden met het opgaan naar Jeruzalem en de dienst van de HEERE.\n\nDe psalmen spreken onder andere over vreemdelingschap, bewaring, vrede, Sion, Gods huis, huisgezin, arbeid, afhankelijkheid en zegen."
    ),

    PsalmCategory(
        "Klein Hallel",
        setOf(113,114,115,116,117,118),
        "Het Klein Hallel bestaat uit Psalm 113 tot en met 118. Deze psalmen bezingen Gods verlossing, trouw, hulp en goedertierenheid. In de Joodse traditie worden zij in het bijzonder verbonden met de grote feesten, waaronder Pesach.\n\nBinnen deze reeks behoort Psalm 118 tot de bekendste psalmen. De psalmen vormen samen een duidelijke eenheid van lofprijzing en dankzegging voor Gods verlossend handelen."
    ),

    PsalmCategory(
        "Halleluja-slot",
        setOf(146,147,148,149,150),
        "Het Hebreeuwse Halleluja (\u05d4\u05b7\u05dc\u05b0\u05dc\u05d5\u05bc\u05be\u05d9\u05b8\u05d4\u05bc) betekent: \u2018Looft de HEERE\u2019 of \u2018Prijs de HEERE\u2019. Deze vijf psalmen sluiten het hele Psalmenboek af met herhaalde oproepen om de HEERE te loven."
    ),

    PsalmCategory(
        "Historische psalmen",
        setOf(78,105,106,114,135,136),
        "Historische psalmen zien terug op Gods daden in de geschiedenis van Isra\u00ebl. Zij spreken over leiding, uitredding, oordeel, ongeloof en ongehoorzaamheid van het volk en de blijvende trouw van de HEERE."
    ),

    PsalmCategory(
        "Sion, Jeruzalem en Gods woning",
        setOf(46,48,76,84,87,122,125,126,129,132,137),
        "Deze psalmen spreken over Sion, Jeruzalem, het huis des HEEREN en het verlangen naar Gods woonplaats. Zij bezingen de plaats waar de HEERE Zijn Naam deed wonen en waar Zijn volk samenkomt voor de dienst aan Hem."
    ),

    PsalmCategory(
        "Scheppingspsalmen",
        setOf(8,19,24,29,33,65,95,104,136,148),
        "Scheppingspsalmen bezingen Gods majesteit in hemel en aarde. Zon, maan, sterren, dieren, zee, storm, de vruchtbaarheid van de aarde en de orde van de schepping spreken van de macht en wijsheid van de Schepper."
    ),

    PsalmCategory(
        "Acrostichonpsalmen",
        setOf(9,10,25,34,37,111,112,119,145),
        "Acrostichonpsalmen zijn psalmen met een alfabetische structuur in het Hebreeuws. De opeenvolgende verzen, regels of strofen volgen de letters van het Hebreeuwse alfabet.\nBekende voorbeelden zijn Psalm 25, 34, 119 en 145. Deze vorm geeft de psalm een duidelijk geordende opbouw."
    ),

    PsalmCategory(
        "Psalmen om Gods oordeel",
        setOf(5,10,17,35,55,58,59,69,79,83,109,129,137,139,140),
        "Deze psalmen bevatten gebeden om Gods oordeel over vijanden en goddelozen. De dichter brengt het onrecht voor Gods aangezicht en roept God aan om recht te doen. Daarmee spreken deze psalmen vanuit het verlangen dat Gods recht en gerechtigheid zullen zegevieren."
    ),

    PsalmCategory(
        "Voor troost en moeilijke tijden",
        setOf(23,27,39,42,43,46,61,62,71,90,91,103,116,121,130,139),
        "De psalmen geven woorden bij kwetsbaarheid, ouderdom, ziekte, verlies, sterfelijkheid en het verlangen naar Gods nabijheid. Zij bieden taal voor vertrouwen, gebed en hoop wanneer het leven moeilijk is."
    ),

    PsalmCategory(
        "Psalmen voor huisgezin en dagelijks leven",
        setOf(127,128,131,133),
        "Deze psalmen spreken over huis, arbeid, kinderen, eenvoud, vrede en broederlijke samenwoning. Ze laten zien hoe het vertrouwen op God ook het gewone dagelijkse leven omvat."
    ),

    PsalmCategory(
        "Michtam-psalmen",
        setOf(16,56,57,58,59,60),
        "Hebr. \u05de\u05b4\u05db\u05b0\u05ea\u05bc\u05b8\u05dd (*michtam*).\n\nDeze psalmen dragen in het opschrift het Hebreeuwse woord michtam. De precieze betekenis van dit woord is niet zeker. De Statenvertaling vertaalt het traditioneel als \u2018gouden kleinood\u2019."
    ),

    PsalmCategory(
        "Onderwijzingspsalmen",
        setOf(32,42,44,45,52,53,54,55,74,78,88,89,142),
        "Hebr. \u05de\u05b7\u05e9\u05c2\u05b0\u05db\u05b4\u05bc\u05d9\u05dc (*maskil*).\n\nDeze psalmen dragen in het opschrift het Hebreeuwse woord maskil. Het woord hangt samen met inzicht en verstandig handelen en wordt traditioneel opgevat als \u2018een onderwijzing\u2019. De maskil-psalmen hebben vaak een onderwijzend of beschouwend karakter."
    ),

    PsalmCategory(
        "Gebedspsalmen",
        setOf(17,86,90,102,142),
        "Psalmen waarvan het opschrift of de aanhef expliciet aangeeft dat het om een gebed gaat."
    )
)

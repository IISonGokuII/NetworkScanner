package com.mycompany.synapse.game.logic

import kotlin.random.Random

// ---------------------------------------------------------------- Wortsalat

data class AnagramTask(val word: String, val letters: List<Char>)

object AnagramGen {
    /** Offline-Wortliste. Mehrdeutige Anagramme (z. B. INSEL/LINSE) stehen bewusst beide drin. */
    private val raw = """
        BAUM HAUS BALL BOOT BROT BURG DACH DORF ECKE ENTE ERDE REDE FELD FILM GELD GRAS HAHN HALS HAND
        HEMD HERZ HOSE HUND HUHN KIND KOPF LAMM LAND LUFT MAUS MEER MOND NASE NEST OBST OFEN PARK POST
        ROSE SAFT SAND SOFA SALZ TANZ TOPF TURM VASE WALD WAND WELT WIND WOLF ZAHN ZELT GANS GOLD BERG
        BAHN BILD BUCH DOSE HOLZ IGEL JAHR LÖWE PILZ RING TEIG TIER UFER WURM ZAUN ZIEL ESEL KORB MOOS
        LIED LEID BART BIER BLUT DAME GURT KNIE LACK MAST
        KATZE KERZE LICHT SEIFE STEIN WOLKE BLATT FARBE GABEL MÜHLE SEGEL ADLER LAMPE APFEL BIRNE BLUME
        BRIEF ENGEL FEDER FEUER FLUSS HONIG INSEL LINSE JACKE KARTE KISTE KLEID KRONE LAGER REGAL MAUER
        NADEL NEBEL NUDEL PFERD PUPPE RADIO SCHAF SCHUH SONNE SPIEL STERN STUHL TASSE TISCH TRAUM VOGEL
        WAGEN BIENE DECKE FISCH FLÖTE GEIGE HAFEN HÜGEL KAMEL KREIS MÜTZE NABEL PALME QUARK SALAT SOCKE
        TIGER TULPE WAAGE ZANGE BESEN BODEN ANKER EIMER GURKE TAFEL KÄFIG ABEND JUWEL
        GARTEN GLOCKE MESSER PINSEL ZUCKER BRÜCKE LÖFFEL RAKETE DAUMEN DRACHE KUCHEN FROSCH BÄCKER BANANE
        BUTTER DOKTOR HAMMER KAFFEE KARTON KIRCHE KOFFER LEITER MANTEL MUSKEL ORANGE PAPIER SCHULE TOMATE
        WINTER SOMMER HERBST ZIMMER FALTER FABRIK HIMMEL INSEKT KAKTUS KRAKEN LAWINE NORDEN OSTERN RITTER
        SCHERE SCHAUM URLAUB WASSER WECKER BALKON DUSCHE FREUND HUNGER LEHRER MAGNET PFANNE SCHACH WÜRFEL
        AMEISE GEMÜSE MONTAG VULKAN WETTER PALAST QUALLE
        FLASCHE PFLAUME SPIEGEL TEPPICH GITARRE KOMPASS VORHANG ZWIEBEL FENSTER ELEFANT KLAVIER LEUCHTE
        MUSCHEL PAPAGEI PINGUIN SCHRANK TELEFON ZITRONE BRUNNEN DIAMANT FAHRRAD GESICHT KAPITÄN MÄRCHEN
        NACHBAR SCHLOSS TAUCHER KÄNGURU LEOPARD SCHWEIN STIEFEL TORWART GEBIRGE NASHORN ZEITUNG HAMSTER
        PFEFFER GEMÄLDE
        TROMPETE ERDBEERE HANDTUCH RUCKSACK ZAUBERER PYRAMIDE KROKODIL KALENDER BUMERANG ERFINDER GESCHENK
        KASTANIE MATRATZE SANDBURG SCHNECKE APOTHEKE BAUMHAUS EICHHORN GEWITTER LIMONADE SCHAUKEL
        KARTOFFEL BLEISTIFT SCHLÜSSEL FLUGHAFEN MARMELADE ABENTEUER FAHRSTUHL LANDKARTE PANTOFFEL
        UNIVERSUM VOGELNEST FEUERWEHR MANDARINE NOTIZBUCH
    """.trimIndent()

    val words: List<String> by lazy { raw.split(Regex("\\s+")).filter { it.isNotBlank() }.distinct() }
    private val dictionary: Set<String> by lazy { words.toSet() }
    private val bySignature: Map<String, List<String>> by lazy { words.groupBy { signature(it) } }

    fun signature(w: String) = w.toCharArray().sorted().joinToString("")

    fun wordLength(level: Int) = when {
        level <= 8 -> 4
        level <= 16 -> 5
        level <= 26 -> 6
        level <= 36 -> 7
        level <= 44 -> 8
        else -> 9
    }

    /** Jede Lösung aus der Wortliste mit denselben Buchstaben zählt. */
    fun accepts(task: AnagramTask, input: String) =
        input == task.word || (input in dictionary && signature(input) == signature(task.word))

    fun alternatives(word: String) = bySignature[signature(word)].orEmpty()

    fun generate(level: Int, rng: Random): AnagramTask {
        val len = wordLength(level.coerceIn(1, 50))
        val pool = words.filter { it.length == len }.ifEmpty { words }
        val word = pool[rng.nextInt(pool.size)]
        var letters: List<Char>
        var tries = 0
        do {
            letters = word.toList().shuffled(rng)
            tries++
        } while (tries < 50 && letters.joinToString("") in dictionary)
        return AnagramTask(word, letters)
    }

    fun label(level: Int) = "${wordLength(level)} Buchstaben"
}

// ---------------------------------------------------------------- Was passt nicht?

data class WordCategory(val plural: String, val singular: String, val group: String, val words: List<String>)

data class OddOneTask(val words: List<String>, val oddIndex: Int, val explanation: String)

object OddOneGen {
    private fun c(plural: String, singular: String, group: String, words: String) =
        WordCategory(plural, singular, group, words.split(",").map { it.trim() })

    val categories = listOf(
        c("Obst", "Obst", "Essen", "Apfel, Birne, Kirsche, Banane, Pflaume, Mango, Ananas, Erdbeere, Traube, Melone, Pfirsich, Zitrone"),
        c("Gemüse", "Gemüse", "Essen", "Karotte, Brokkoli, Spinat, Lauch, Sellerie, Blumenkohl, Spargel, Kartoffel, Zwiebel, Radieschen, Kohlrabi, Erbse"),
        c("Getränke", "ein Getränk", "Essen", "Kaffee, Tee, Saft, Limonade, Milch, Wasser, Kakao, Sprudel, Eistee, Smoothie"),
        c("Gewürze", "ein Gewürz", "Essen", "Pfeffer, Zimt, Muskat, Kümmel, Oregano, Basilikum, Thymian, Curry, Anis, Safran"),
        c("Säugetiere", "ein Säugetier", "Tiere", "Hund, Katze, Pferd, Elefant, Löwe, Maus, Hase, Bär, Wal, Fuchs, Affe, Kuh, Fledermaus"),
        c("Vögel", "ein Vogel", "Tiere", "Adler, Spatz, Eule, Taube, Amsel, Papagei, Storch, Pinguin, Möwe, Rabe, Specht, Schwan"),
        c("Fische", "ein Fisch", "Tiere", "Forelle, Hecht, Lachs, Karpfen, Hering, Thunfisch, Barsch, Aal, Dorsch, Sardine"),
        c("Insekten", "ein Insekt", "Tiere", "Biene, Ameise, Käfer, Fliege, Mücke, Libelle, Wespe, Hummel, Schmetterling, Grille"),
        c("Möbel", "ein Möbelstück", "Haus", "Stuhl, Tisch, Sofa, Schrank, Regal, Bett, Kommode, Sessel, Hocker, Vitrine"),
        c("Werkzeuge", "ein Werkzeug", "Haus", "Hammer, Zange, Säge, Bohrer, Schraubenzieher, Feile, Meißel, Hobel, Schaufel, Spachtel"),
        c("Küchengeräte", "ein Küchengerät", "Haus", "Toaster, Mixer, Herd, Kühlschrank, Wasserkocher, Backofen, Mikrowelle, Pfanne, Topf, Schneebesen"),
        c("Musikinstrumente", "ein Musikinstrument", "Freizeit", "Geige, Gitarre, Klavier, Trompete, Flöte, Trommel, Harfe, Cello, Saxofon, Orgel"),
        c("Sportarten", "eine Sportart", "Freizeit", "Fußball, Tennis, Schwimmen, Rudern, Boxen, Golf, Handball, Judo, Skifahren, Turnen"),
        c("Berufe", "ein Beruf", "Mensch", "Arzt, Lehrer, Bäcker, Maler, Pilot, Koch, Tischler, Gärtner, Polizist, Friseur"),
        c("Kleidungsstücke", "ein Kleidungsstück", "Mensch", "Hose, Hemd, Jacke, Mantel, Pullover, Rock, Schal, Mütze, Socke, Kleid"),
        c("Körperteile", "ein Körperteil", "Mensch", "Arm, Bein, Knie, Schulter, Ellbogen, Nase, Ohr, Hand, Fuß, Kinn"),
        c("Landfahrzeuge", "ein Landfahrzeug", "Verkehr", "Auto, Bus, Fahrrad, Zug, Motorrad, Roller, Lastwagen, Straßenbahn, Traktor"),
        c("Wasserfahrzeuge", "ein Wasserfahrzeug", "Verkehr", "Boot, Schiff, Kanu, Fähre, Jacht, Floß, Kajak, Segelboot"),
        c("Luftfahrzeuge", "ein Luftfahrzeug", "Verkehr", "Flugzeug, Hubschrauber, Ballon, Zeppelin, Segelflieger, Drohne, Gleitschirm"),
        c("Länder", "ein Land", "Welt", "Frankreich, Spanien, Italien, Polen, Schweden, Kanada, Brasilien, Japan, Indien, Ägypten"),
        c("Hauptstädte", "eine Hauptstadt", "Welt", "Paris, Rom, Madrid, Berlin, Wien, Lissabon, Oslo, Prag, Athen, Warschau"),
        c("Planeten", "ein Planet", "Welt", "Merkur, Venus, Erde, Mars, Jupiter, Saturn, Uranus, Neptun"),
        c("Bäume", "ein Baum", "Natur", "Eiche, Buche, Birke, Tanne, Fichte, Ahorn, Linde, Kastanie, Weide, Pappel"),
        c("Blumen", "eine Blume", "Natur", "Rose, Tulpe, Nelke, Lilie, Sonnenblume, Veilchen, Orchidee, Narzisse, Gänseblümchen, Dahlie"),
        c("Wetterarten", "eine Wetterart", "Natur", "Regen, Schnee, Hagel, Nebel, Gewitter, Sturm, Frost, Tau, Sonnenschein"),
        c("Farben", "eine Farbe", "Stoffe", "Rot, Blau, Grün, Gelb, Violett, Türkis, Beige, Grau, Weiß, Schwarz"),
        c("Metalle", "ein Metall", "Stoffe", "Eisen, Kupfer, Gold, Silber, Zinn, Blei, Zink, Aluminium, Nickel"),
    )

    fun wordCount(level: Int) = when {
        level <= 10 -> 4
        level <= 25 -> 5
        else -> 6
    }

    fun generate(level: Int, rng: Random): OddOneTask {
        val l = level.coerceIn(1, 50)
        val k = wordCount(l)
        val main = categories[rng.nextInt(categories.size)]
        val sameGroupChance = when {
            l <= 14 -> 0.0
            l <= 30 -> 0.5
            else -> 0.8
        }
        val sameGroup = categories.filter { it !== main && it.group == main.group }
        val others = categories.filter { it.group != main.group }
        val oddCat = if (sameGroup.isNotEmpty() && rng.nextDouble() < sameGroupChance) sameGroup[rng.nextInt(sameGroup.size)]
        else others[rng.nextInt(others.size)]
        val picks = main.words.shuffled(rng).take(k - 1)
        val odd = oddCat.words.filter { it !in main.words }.let { it[rng.nextInt(it.size)] }
        val words = (picks + odd).shuffled(rng)
        val explanation = "„$odd“ ist ${oddCat.singular} – die anderen sind ${main.plural}."
        return OddOneTask(words, words.indexOf(odd), explanation)
    }

    fun label(level: Int) = "${wordCount(level)} Wörter" + if (level > 14) " · ähnliche Kategorien" else ""
}

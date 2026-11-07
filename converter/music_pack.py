"""
Builds a Middle-earth music resource pack from your own soundtrack files, on your own computer. Nothing it makes
belongs in this repo (the output goes outside it, and *.ogg packs are git-ignored).

    python3 converter/music_pack.py <folder with your tracks> [--map my_map.json] [--out <resourcepacks folder>]

- Needs ffmpeg on PATH for anything that isn't already .ogg (mp3, flac, m4a, wav...). Tracks come out small
  (mono Vorbis q1, ~0.4 MB a minute); --quality N and --stereo trade size for fidelity.
- Each track goes on one or more playlists (the names are LOTR's music regions, listed in
  lotr/src/generated/resources/assets/lotr_port/music/playlists.json). Which ones: the map file, a JSON object
  {"word in the file name": ["playlist", ...]}, tried in order, first match wins. Without --map the built-in
  guesses below are used; tracks that match nothing go to "unsorted" and are listed so you can add a rule.
  Run with --write-map to get the built-in map as a file to edit.
- Writes a folder pack "Middle-earth Music" (or a .zip with --zip) into --out (default: Minecraft's
  resourcepacks folder for this OS). Enable it in Options > Resource Packs.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import unicodedata
import zipfile
from concurrent.futures import ThreadPoolExecutor

QUALITY, STEREO, LOUDNESS, LOUDNESS_RANGE = 1, False, -20, 9
REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
AUDIO = (".ogg", ".mp3", ".flac", ".m4a", ".wav", ".aac", ".opus", ".wma")

# file-name words -> playlists (music regions); edit with --write-map / --map.
# Whole words only ("ent" doesn't match "Lament"; a trailing s/es is allowed), accents and punctuation ignored.
# Tried top to bottom, first match wins, so specific names come first and catch-alls ("hobbit", "fellowship",
# "main theme") last: album names often sit in the file name too. A track on several lists keeps playing when you
# walk between biomes that use them.
DEFAULT_MAP = {
    # --- exact track titles whose words don't name a place: placed by where the scene happens ---
    # An Unexpected Journey
    "axe or sword": ["shire"], "the adventure begins": ["shire", "eriador"], "the world is ahead": ["eriador"],
    "an ancient enemy": ["dwarven"], "moon runes": ["rivendell"], "the defiler": ["grey_mountains"],
    # The Desolation of Smaug
    "wilderland": ["rhovanion"], "the forest river": ["woodland_realm"], "the nature of evil": ["woodland_realm"],
    "protector of the common folk": ["dale"], "thrice welcome": ["dale"], "kingsfoil": ["dale"],
    "a liar and a thief": ["dale"], "in the shadow of the mountain": ["dwarven", "dale"],
    "on the doorstep": ["dwarven"], "my armor is iron": ["dwarven", "dale"], "the hunters": ["rhovanion"],
    # The Battle of the Five Armies
    "shores of the long lake": ["dale"], "beyond sorrow and grief": ["dale"], "guardians of the three": ["mirkwood"],
    "the gathering of the clouds": ["dale"], "mithril": ["dwarven"], "bred for war": ["grey_mountains"],
    "a thief in the night": ["dale"], "the darkest hour": ["dale"], "the fallen": ["dale"], "to the death": ["dale"],
    "courage and wisdom": ["dale"], "the return journey": ["eriador", "rhovanion"], "ironfoot": ["dwarven"],
    # The Two Towers
    "glamdring": ["misty_mountains"], "elven rope": ["brown_lands"], "my precious": ["brown_lands"],
    "the three hunters": ["rohan"], "night camp": ["rohan"], "wraiths on wings": ["dead_marshes"],
    "gandalf the white": ["fangorn"], "the dreams of trees": ["fangorn"], "the heir of numenor": ["fangorn"],
    "the entmoot decides": ["fangorn"], "master peregrin's plan": ["fangorn"], "the king's decision": ["rohan"],
    "the story foretold": ["rivendell"], "sons of the steward": ["gondor"], "rock and pool": ["gondor"],
    "faramir's good council": ["gondor"], "faramir's good counsel": ["gondor"], "the forbidden pool": ["gondor"],
    "aragorn's return": ["rohan"], "war is upon us": ["rohan"], "the host of the eldar": ["rohan"],
    "breach of the deeping wall": ["rohan"], "haldir's lament": ["rohan"],
    "the tales that really matter": ["gondor"],
    # The Return of the King
    "roots and beginnings": ["rhovanion"], "the chalice passed": ["rohan"], "the green dragon": ["shire"],
    "the palantir": ["rohan"], "grace of undomiel": ["rivendell"], "eyes of the white tower": ["gondor"],
    "a coronal of silver and gold": ["rivendell"], "the passing of the grey company": ["rohan", "gondor"],
    "dwimorberg": ["gondor"], "master meriadoc": ["rohan"], "merry's simple courage": ["rohan"],
    "grond": ["gondor"], "tomb of the stewards": ["gondor"], "the mumakil": ["gondor", "near_harad"],
    "dernhelm in battle": ["gondor"], "a far green country": ["gondor"], "houses of healing": ["gondor"],
    "the last debate": ["gondor"], "the land of shadow": ["mordor"], "the crack of doom": ["mordor"],
    "the eagles": ["mordor"], "elanor": ["shire"], "the parting of sam and frodo": ["mordor"],
    "for frodo": ["mordor"], "the fellowship reunited": ["gondor"],
    # --- The Hobbit films (before the LOTR places, so the "Misty Mountains" song and "Erebor" land right) ---
    "unexpected party": ["shire"], "old friends": ["shire"], "my dear frodo": ["shire"],
    "dreaming of bag end": ["shire"], "roast mutton": ["eriador"], "troll hoard": ["eriador"], "trolls": ["eriador"],
    "warg scout": ["eriador"], "radagast": ["mirkwood"], "hidden valley": ["rivendell"],
    "white council": ["rivendell"], "over hill": ["misty_mountains"], "thunder battle": ["misty_mountains"],
    "under hill": ["misty_mountains"], "goblin": ["misty_mountains"], "riddles in the dark": ["misty_mountains"],
    "brass buttons": ["misty_mountains"], "out of the frying pan": ["misty_mountains"],
    "good omen": ["rhovanion"], "beorn": ["rhovanion"], "carrock": ["rhovanion"],
    "flies and spiders": ["mirkwood"], "spider": ["mirkwood"], "necromancer": ["mirkwood"],
    "dol guldur": ["mirkwood"], "hill of sorcery": ["mirkwood"], "beyond the forest": ["mirkwood"],
    "mirkwood": ["mirkwood"],
    "woodland realm": ["woodland_realm"], "thranduil": ["woodland_realm"], "tauriel": ["woodland_realm"],
    "wood elves": ["woodland_realm"], "barrels out of bond": ["woodland_realm"], "elvenking": ["woodland_realm"],
    "feast of starlight": ["woodland_realm"], "legolas": ["woodland_realm", "lothlorien"],
    "song of the lonely mountain": ["dale", "dwarven"], "i see fire": ["dale"],
    "lake town": ["dale"], "laketown": ["dale"], "esgaroth": ["dale"], "bard": ["dale"], "girion": ["dale"],
    "smaug": ["dale"], "fire and water": ["dale"], "courage of hobbits": ["dale"], "ravenhill": ["dale"],
    "five armies": ["dale"], "clouds burst": ["dale"], "battle for the mountain": ["dale"], "dale": ["dale"],
    "erebor": ["dwarven", "dale"], "lonely mountain": ["dwarven", "dale"], "arkenstone": ["dwarven"],
    "thorin": ["dwarven"], "oakenshield": ["dwarven"], "dain": ["dwarven"], "iron hills": ["dwarven"],
    "durin": ["dwarven"], "dragon sickness": ["dwarven"], "inside information": ["dwarven"],
    "azog": ["grey_mountains"], "bolg": ["grey_mountains"], "gundabad": ["grey_mountains"],
    "last goodbye": ["shire", "lindon"], "there and back again": ["shire"], "edge of the wild": ["eriador"],
    # --- The Fellowship of the Ring ---
    "the prophecy": ["mordor", "gondor"], "the shadow of the past": ["shire"], "the black rider": ["shire"],
    "keep it secret": ["shire"], "flaming red hair": ["shire"], "three is company": ["shire"],
    "a conspiracy unmasked": ["shire"], "passing of the elves": ["shire", "lindon"], "a knife in the dark": ["eriador"],
    "give up the halfling": ["eriador", "rivendell"], "may it be": ["rivendell", "lindon"],
    "gilraens memorial": ["rivendell"], "balins tomb": ["misty_mountains", "dwarven"],
    "caras galadhon": ["lothlorien"], "the great eye": ["lothlorien"], "parth galen": ["rhovanion"],
    "departure of boromir": ["rhovanion"], "road goes ever on": ["shire"],
    "prologue": ["mordor", "gondor"], "introduction": ["eriador", "rhovanion"],
    "concerning hobbits": ["shire"], "bag end": ["shire"], "maggot": ["shire"], "very old friend": ["shire"],
    "shortcut to mushrooms": ["shire"], "buckleberry": ["shire"], "the shire": ["shire"],
    "old forest": ["old_forest"], "goldberry": ["old_forest"], "bombadil": ["old_forest"],
    "barrow": ["barrow_downs"], "bree": ["bree"], "prancing": ["bree"], "strider": ["bree", "eriador"],
    "weathertop": ["eriador"], "amon sul": ["eriador"], "flight to the ford": ["rivendell"], "ford": ["rivendell"],
    "glorfindel": ["rivendell"], "rivendell": ["rivendell"], "elrond": ["rivendell"], "arwen": ["rivendell"],
    "evenstar": ["rivendell"], "council": ["rivendell"], "many meetings": ["rivendell"],
    "ring goes south": ["eriador", "eregion"], "eregion": ["eregion"], "crebain": ["dunland", "eregion"],
    "caradhras": ["misty_mountains"], "journey in the dark": ["misty_mountains", "dwarven"],
    "moria": ["misty_mountains", "dwarven"], "khazad": ["misty_mountains", "dwarven"],
    "balrog": ["misty_mountains"], "bridge of": ["misty_mountains"], "foundations of stone": ["misty_mountains"],
    "misty mountains": ["misty_mountains", "dwarven"], "misty": ["misty_mountains"],
    "lament for gandalf": ["lothlorien"], "lothlorien": ["lothlorien"], "lorien": ["lothlorien"],
    "galadriel": ["lothlorien"], "the mirror": ["lothlorien"],
    "great river": ["rhovanion"], "anduin": ["rhovanion"], "argonath": ["rhovanion"], "amon hen": ["rhovanion"],
    "breaking of the fellowship": ["rhovanion"],
    # --- The Two Towers ---
    "taming of smeagol": ["brown_lands"], "smeagol": ["brown_lands"], "gollum": ["brown_lands", "dead_marshes"],
    "emyn muil": ["brown_lands"], "dead marshes": ["dead_marshes"], "passage of the marshes": ["dead_marshes"],
    "last march of the ents": ["isengard", "fangorn"], "isengard": ["isengard"], "saruman": ["isengard"],
    "orthanc": ["isengard"], "uruk": ["isengard", "rohan"],
    "treebeard": ["fangorn"], "white rider": ["fangorn"], "fangorn": ["fangorn"], "ent": ["fangorn"],
    "entwash": ["fangorn"],
    "golden hall": ["rohan"], "edoras": ["rohan"], "meduseld": ["rohan"], "rohan": ["rohan"],
    "rohirrim": ["rohan"], "eorlingas": ["rohan"], "theoden": ["rohan"], "eowyn": ["rohan"], "eomer": ["rohan"],
    "helm": ["rohan"], "hornburg": ["rohan"], "riders of": ["rohan"], "breath of life": ["rohan"],
    "leave taking": ["rohan", "lothlorien"], "hope and memory": ["rohan"], "warg": ["rohan"],
    "dunland": ["dunland"],
    "ithilien": ["gondor"], "faramir": ["gondor"], "osgiliath": ["gondor"], "window on the west": ["gondor"],
    "samwise the brave": ["gondor", "mordor"],
    # --- The Return of the King ---
    "minas morgul": ["mordor"], "minas tirith": ["gondor"], "white tree": ["gondor"], "steward of gondor": ["gondor"],
    "lighting of the beacons": ["gondor", "rohan"], "beacons": ["gondor", "rohan"], "pelennor": ["gondor"],
    "anduril": ["gondor"], "return of the king": ["gondor"], "dunharrow": ["gondor"],
    "paths of the dead": ["gondor"], "twilight and shadow": ["gondor"], "denethor": ["gondor"],
    "gondor": ["gondor"],
    "morgul": ["mordor"], "cirith ungol": ["mordor"], "shelob": ["mordor"], "ash and smoke": ["mordor"],
    "hope fails": ["mordor"], "black gate": ["mordor"], "end of all things": ["mordor"], "mount doom": ["mordor"],
    "mordor": ["mordor"], "nazgul": ["mordor"], "ringwraith": ["mordor"], "witch king": ["mordor", "angmar"],
    "dark lord": ["mordor"], "sauron": ["mordor"], "one ring": ["mordor"],
    "grey havens": ["lindon"], "havens": ["lindon"], "into the west": ["lindon"], "lindon": ["lindon"],
    "harad": ["near_harad"], "umbar": ["near_harad"], "corsair": ["near_harad"],
    "mumak": ["near_harad", "far_harad"], "oliphaunt": ["near_harad", "far_harad"], "angmar": ["angmar"],
    "dwarf": ["dwarven"], "dwarves": ["dwarven"],
    # --- catch-alls: last, because album names put these words in many file names ---
    "shire": ["shire"], "frodo": ["shire"], "bilbo": ["shire"], "hobbit": ["shire"],
    "ending theme": ["shire", "lindon"], "end credits": ["shire", "lindon"], "ending": ["shire", "lindon"],
    "main theme": ["eriador", "rhovanion", "rohan", "gondor"], "fellowship": ["eriador", "rhovanion"],
}


def minecraft_dir():
    home = os.path.expanduser("~")
    if sys.platform.startswith("win"):
        return os.path.join(os.environ.get("APPDATA", home), ".minecraft")
    if sys.platform == "darwin":
        return os.path.join(home, "Library", "Application Support", "minecraft")
    return os.path.join(home, ".minecraft")


# Extra places for tracks, on top of the rule above that placed them: playlist -> track-title words (whole words,
# like the rules). Not canon, just music that suits the land, so every list has a few tracks; a track on several
# lists also plays on as you cross between them.
EXTRA = {
    # places, not lands (client/LotrMusic): on one of the map's roads, and underground (mines, caves, delvings)
    "road": ["the world is ahead", "the adventure begins", "the ring goes south", "the three hunters",
             "the plains of rohan", "over hill", "the return journey", "misty mountains", "the road to isengard",
             "the journey to the grey havens", "exodus from edoras", "elven rope", "main theme", "on to weathertop",
             "the hunters", "a troll hoard", "the quest for erebor", "warg scouts", "night camp", "roast mutton",
             "the wolves of isengard", "concerning hobbits", "the road goes ever on", "far away"],
    "underground": ["misty mountains", "song of the lonely mountain", "durins folk", "mithril", "a journey in the dark",
                    "in the shadow of the mountain", "inside information", "on the doorstep", "an ancient enemy",
                    "sons of durin", "ironfoot", "my armor is iron", "shelob the great", "khazad dum",
                    "the bridge of khazad dum", "the great eye", "riddles in the dark", "the dwarrowdelf",
                    "moria", "the mines"],
    "angmar": ["the nazgul attack", "wraiths on wings", "the hill of sorcery", "the defiler"],
    "barrow_downs": ["the paths of the dead", "dwimorberg", "wraiths on wings"],
    "bree": ["the green dragon", "thrice welcome", "night camp"],
    "dorwinion": ["feast of starlight", "barrels out of bond", "the green dragon"],
    "dunland": ["the wolves of isengard", "the voice of saruman", "exodus from edoras", "war is upon us"],
    "enedwaith": ["night camp", "the three hunters", "the world is ahead", "the plains of rohan"],
    "eregion": ["moon runes", "glamdring", "a coronal of silver and gold"],
    "far_harad": ["the mumakil", "the plains of rohan", "a far green country", "the land of shadow"],
    "far_harad_jungle": ["flies and spiders", "mirkwood", "radagast the brown", "the dreams of trees"],
    "forodwaith": ["ravenhill", "the gathering of the clouds", "beyond sorrow and grief", "a thunder battle"],
    "grey_mountains": ["the hunters", "under hill", "the darkest hour"],
    "lothlorien": ["the host of the eldar", "haldirs lament", "the grace of undomiel", "feast of starlight"],
    "near_harad": ["the forests of ithilien", "war is upon us", "the land of shadow"],
    "old_forest": ["the dreams of trees", "radagast the brown"],
    "perdorogwaith": ["roast mutton", "a troll hoard", "the hunters", "bred for war"],
    "pukel": ["the dreams of trees", "marshalling at dunharrow", "the passing of the grey company"],
    "rhun": ["shores of the long lake", "the land of shadow", "to the death", "bred for war"],
    "sea": ["the journey to the grey havens", "days of the ring", "the last goodbye", "the forest river"],
}
MIN_TRACKS = 3  # per mood, per playlist
MOODS = ("calm", "suspense", "fight")

# Each track's mood(s), by title words (first match wins), from the scene it scores. Tracks not listed get a guess
# from their title and loudness (guess_mood). calm: peace, travel, homes, songs. suspense: danger, darkness, dread,
# a chase about to begin. fight: battle and pursuit.
MOOD_RULES = {
    # An Unexpected Journey
    "my dear frodo": ["calm"], "old friends": ["calm"], "an unexpected party": ["calm"], "axe or sword": ["calm"],
    "misty mountains": ["calm"], "the adventure begins": ["calm"], "the world is ahead": ["calm"],
    "an ancient enemy": ["fight"], "radagast the brown": ["suspense"], "roast mutton": ["fight"],
    "a troll hoard": ["suspense"], "the hill of sorcery": ["suspense"], "warg scouts": ["fight"],
    "the hidden valley": ["calm"], "moon runes": ["calm"], "the defiler": ["suspense"],
    "the white council": ["suspense"], "over hill": ["calm"], "a thunder battle": ["fight"], "under hill": ["fight"],
    "riddles in the dark": ["suspense"], "brass buttons": ["fight"], "out of the frying pan": ["fight"],
    "a good omen": ["calm"], "song of the lonely mountain": ["calm"], "dreaming of bag end": ["calm"],
    # The Desolation of Smaug
    "the quest for erebor": ["suspense"], "wilderland": ["suspense"], "the house of beorn": ["suspense"],
    "mirkwood": ["suspense"], "flies and spiders": ["fight"], "the woodland realm": ["calm"],
    "feast of starlight": ["calm"], "barrels out of bond": ["fight"], "the forest river": ["fight"],
    "bard a man of lake town": ["calm"], "the nature of evil": ["suspense"],
    "protector of the common folk": ["calm"], "thrice welcome": ["calm"], "girion lord of dale": ["fight"],
    "durins folk": ["suspense"], "in the shadow of the mountain": ["suspense"], "on the doorstep": ["suspense"],
    "the courage of hobbits": ["suspense"], "inside information": ["suspense"], "kingsfoil": ["fight"],
    "a liar and a thief": ["fight"], "the hunters": ["fight"], "smaug": ["suspense"], "my armor is iron": ["fight"],
    "i see fire": ["calm"], "beyond the forest": ["calm"],
    # The Battle of the Five Armies
    "fire and water": ["fight"], "shores of the long lake": ["calm"], "beyond sorrow and grief": ["calm"],
    "guardians of the three": ["fight"], "the ruins of dale": ["suspense"], "the gathering of the clouds": ["suspense"],
    "mithril": ["suspense"], "bred for war": ["suspense"], "a thief in the night": ["suspense"],
    "the clouds burst": ["fight"], "battle for the mountain": ["fight"], "the darkest hour": ["fight"],
    "sons of durin": ["fight"], "the fallen": ["calm"], "ravenhill": ["fight"], "to the death": ["fight"],
    "courage and wisdom": ["calm"], "the return journey": ["calm"], "there and back again": ["calm"],
    "the last goodbye": ["calm"], "ironfoot": ["fight"],
    # The Fellowship of the Ring (the 16-track album in "New folder")
    "introduction": ["calm"], "the shire": ["calm"], "bag end to maggots farm": ["calm"], "the old forest": ["suspense"],
    "tom and goldberry": ["calm"], "the barrow downs": ["suspense"], "village of bree": ["calm"],
    "on to weathertop": ["suspense"], "rivendell": ["calm"], "the ring goes south": ["calm"],
    "a journey in the dark": ["suspense"], "lothlorien": ["calm"], "the great river anduin": ["calm"],
    "glorfindels theme": ["fight"], "main theme": ["calm"], "ending theme": ["calm"],
    # The Fellowship of the Ring (original soundtrack and Complete Recordings), for when they're added
    "the prophecy": ["suspense"], "one ring to rule them all": ["suspense"], "concerning hobbits": ["calm"],
    "bag end": ["calm"], "very old friends": ["calm"], "flaming red hair": ["calm"], "farewell dear bilbo": ["calm"],
    "old bilbo": ["calm"], "the shadow of the past": ["suspense"], "keep it secret": ["suspense"],
    "a conspiracy unmasked": ["calm"], "three is company": ["calm"], "the passing of the elves": ["calm"],
    "the treason of isengard": ["suspense"], "saruman the white": ["suspense"], "the black rider": ["suspense"],
    "a shortcut to mushrooms": ["fight"], "bucklebury ferry": ["fight"], "at the sign of the prancing pony": ["calm"],
    "strider": ["suspense"], "the caverns of isengard": ["suspense"],
    "orthanc": ["suspense"], "weathertop": ["fight"], "a knife in the dark": ["fight"],
    "give up the halfling": ["fight"], "flight to the ford": ["fight"], "many meetings": ["calm"],
    "the council of elrond": ["calm"], "gilraens memorial": ["calm"], "the fellowship": ["calm"],
    "the pass of caradhras": ["suspense"], "the doors of durin": ["suspense"], "the mines of moria": ["suspense"],
    "balins tomb": ["fight"], "khazad dum": ["fight"], "the balrog": ["fight"], "the bridge of khazad dum": ["fight"],
    "caras galadhon": ["calm"], "the mirror of galadriel": ["suspense"], "the great eye": ["suspense"],
    "farewell to lorien": ["calm"], "the great river": ["calm"], "the argonath": ["calm"], "parth galen": ["calm"],
    "amon hen": ["fight"], "the departure of boromir": ["calm"], "the breaking of the fellowship": ["calm"],
    "the road goes ever on": ["calm"], "may it be": ["calm"],
    # The Two Towers (Complete Recordings)
    "glamdring": ["fight"], "elven rope": ["calm"], "lost in emyn muil": ["suspense"], "my precious": ["suspense"],
    "the three hunters": ["calm"], "the banishment of eomer": ["suspense"], "night camp": ["fight"],
    "the plains of rohan": ["calm"], "fangorn": ["suspense"], "the dead marshes": ["suspense"],
    "wraiths on wings": ["suspense"], "gandalf the white": ["suspense"], "the dreams of trees": ["calm"],
    "the heir of numenor": ["calm"], "ent draught": ["calm"], "edoras": ["calm"], "the court of meduseld": ["suspense"],
    "theoden king": ["calm"], "the kings decision": ["suspense"], "exodus from edoras": ["calm"],
    "the forests of ithilien": ["fight"], "one of the dunedain": ["calm"], "the wolves of isengard": ["fight"],
    "refuge at helms deep": ["suspense"], "the voice of saruman": ["suspense"], "arwens fate": ["calm"],
    "the story foretold": ["calm"], "sons of the steward": ["suspense"], "rock and pool": ["suspense"],
    "faramirs good council": ["calm"], "aragorns return": ["calm"], "war is upon us": ["suspense"],
    "the host of the eldar": ["calm"], "the battle of the hornburg": ["fight"],
    "the breach of the deeping wall": ["fight"], "the entmoot decides": ["calm"], "haldirs lament": ["fight"],
    "master peregrins plan": ["calm"], "the last march of the ents": ["fight"], "the nazgul attack": ["fight"],
    "theoden rides forth": ["fight"], "the tales that really matter": ["calm"], "gollums song": ["calm"],
    # The Two Towers (original soundtrack), if it's added
    "foundations of stone": ["fight"], "the taming of smeagol": ["suspense"], "the riders of rohan": ["calm"],
    "the passage of the marshes": ["suspense"], "the uruk hai": ["fight"], "the king of the golden hall": ["calm"],
    "the black gate is closed": ["suspense"], "evenstar": ["calm"], "the white rider": ["suspense"],
    "treebeard": ["calm"], "the leave taking": ["calm"], "helms deep": ["fight"], "the forbidden pool": ["suspense"],
    "breath of life": ["calm"], "the hornburg": ["fight"], "forth eorlingas": ["fight"],
    "isengard unleashed": ["fight"], "samwise the brave": ["calm"], "farewell to lorien": ["calm"],
    # The Return of the King (Complete Recordings)
    "roots and beginnings": ["calm"], "the road to isengard": ["calm"], "the foot of orthanc": ["suspense"],
    "return to edoras": ["calm"], "the chalice passed": ["calm"], "the green dragon": ["calm"],
    "gollums villainy": ["suspense"], "eowyns dream": ["calm"], "the palantir": ["suspense"],
    "flight from edoras": ["calm"], "the grace of undomiel": ["calm"], "the eyes of the white tower": ["calm"],
    "a coronal of silver and gold": ["calm"], "the lighting of the beacons": ["fight"],
    "osgiliath invaded": ["fight"], "the stairs of cirith ungol": ["suspense"], "allegiance to denethor": ["suspense"],
    "the sacrifice of faramir": ["fight"], "the parting of sam and frodo": ["suspense"],
    "marshalling at dunharrow": ["calm"], "anduril": ["calm"], "the passing of the grey company": ["suspense"],
    "dwimorberg": ["suspense"], "master meriadoc": ["calm"], "the paths of the dead": ["suspense"],
    "the siege of gondor": ["fight"], "shelobs lair": ["suspense"], "merrys simple courage": ["calm"],
    "grond": ["fight"], "shelob the great": ["fight"], "the tomb of the stewards": ["suspense"],
    "the battle of the pelennor fields": ["fight"], "the pyre of denethor": ["suspense"], "the mumakil": ["fight"],
    "dernhelm in battle": ["fight"], "a far green country": ["calm"], "shieldmaiden of rohan": ["fight"],
    "the passing of theoden": ["calm"], "the houses of healing": ["calm"], "the tower of cirith ungol": ["fight"],
    "the last debate": ["calm"], "the land of shadow": ["suspense"], "the mouth of sauron": ["fight"],
    "for frodo": ["fight"], "mount doom": ["fight"], "the crack of doom": ["fight"], "the eagles": ["calm"],
    "the fellowship reunited": ["calm"], "the journey to the grey havens": ["calm"], "elanor": ["calm"],
    "days of the ring": ["calm"], "bilbos song": ["calm"],
}

FIGHT_WORDS = ("battle", "attack", "siege", "war", "fight", "charge", "ambush", "invaded", "breach", "assault",
               "flight", "chase", "escape", "death", "doom", "ride", "rides", "armies", "storm", "orcs", "wargs")
SUSPENSE_WORDS = ("dark", "darkness", "shadow", "evil", "wraith", "wraiths", "nazgul", "dead", "lair", "spider",
                  "sorcery", "necromancer", "marshes", "tomb", "haunted", "villainy", "precious", "black", "morgul",
                  "moria", "balrog", "ring", "watcher", "danger", "dread", "fear", "lost", "hunt", "hunted")


def guess_mood(title_words, m):
    """
    For tracks MOOD_RULES doesn't know: title words first, then loudness against the rest of its album ("rel":
    how much louder its middle is than the album's typical track, in LU). Albums are mastered very differently
    (one sits 10 dB above another), so raw loudness says little. Louder than its album and busy (most of it near its
    peak) reads as fight; quieter, with wide swings, as suspense; the rest as calm.
    """
    w = set(title_words.split())
    if w & set(FIGHT_WORDS):
        return ["fight"]
    if w & set(SUSPENSE_WORDS):
        return ["suspense"]
    rel = m.get("rel", 0) if m else 0
    if m and rel > 3 and m["busy"] > 0.35:
        return ["fight"]
    if m and rel < -2 and m["lra"] > 12:
        return ["suspense"]
    return ["calm"]


def moods_for(name, m=None):
    low = words(os.path.splitext(name)[0])
    for phrase, moods in MOOD_RULES.items():
        if matches(phrase, low):
            return moods
    return guess_mood(low, m)


# Which lands border which, nearest first: a playlist short of MIN_TRACKS for a mood borrows that mood's tracks
# from its neighbours, then theirs, and so on outward.
NEIGHBOURS = {
    "shire": ["old_forest", "bree", "eriador", "barrow_downs", "lindon"],
    "old_forest": ["shire", "barrow_downs", "bree"],
    "barrow_downs": ["old_forest", "bree", "eriador"],
    "bree": ["eriador", "barrow_downs", "shire"],
    "eriador": ["bree", "rivendell", "eregion", "shire", "angmar", "enedwaith"],
    "lindon": ["shire", "sea", "eriador", "dwarven"],
    "rivendell": ["eriador", "misty_mountains", "eregion"],
    "eregion": ["rivendell", "misty_mountains", "dunland", "eriador"],
    "angmar": ["eriador", "misty_mountains", "grey_mountains", "forodwaith"],
    "forodwaith": ["angmar", "grey_mountains"],
    "enedwaith": ["dunland", "eriador", "eregion", "rohan"],
    "dunland": ["enedwaith", "isengard", "eregion", "rohan"],
    "misty_mountains": ["rivendell", "dwarven", "eregion", "rhovanion", "lothlorien", "grey_mountains", "angmar"],
    "dwarven": ["misty_mountains", "dale", "grey_mountains"],
    "grey_mountains": ["misty_mountains", "dwarven", "angmar", "mirkwood", "forodwaith"],
    "rhovanion": ["misty_mountains", "mirkwood", "lothlorien", "brown_lands", "dale", "fangorn"],
    "mirkwood": ["woodland_realm", "rhovanion", "grey_mountains"],
    "woodland_realm": ["mirkwood", "dale", "rhovanion"],
    "dale": ["dwarven", "woodland_realm", "rhovanion", "dorwinion", "rhun"],
    "dorwinion": ["rhun", "dale", "woodland_realm"],
    "rhun": ["dorwinion", "mordor", "dale", "brown_lands"],
    "lothlorien": ["rhovanion", "fangorn", "misty_mountains"],
    "fangorn": ["rohan", "isengard", "lothlorien", "rhovanion"],
    "isengard": ["fangorn", "rohan", "dunland"],
    "rohan": ["isengard", "fangorn", "gondor", "dunland", "enedwaith", "brown_lands"],
    "brown_lands": ["dead_marshes", "rhovanion", "rohan", "mordor"],
    "dead_marshes": ["brown_lands", "mordor"],
    "gondor": ["rohan", "mordor", "pukel", "near_harad", "sea"],
    "pukel": ["gondor", "rohan"],
    "mordor": ["gondor", "dead_marshes", "rhun", "near_harad"],
    "near_harad": ["gondor", "mordor", "far_harad", "sea"],
    "far_harad": ["near_harad", "far_harad_jungle", "perdorogwaith"],
    "far_harad_jungle": ["far_harad", "perdorogwaith"],
    "perdorogwaith": ["far_harad_jungle", "far_harad", "near_harad"],
    "sea": ["lindon", "gondor", "near_harad"],
}


def fill(playlists, names):
    """
    playlists: {land: {mood: [event...]}} from the rules. Tops every land and mood up to MIN_TRACKS with that mood's
    tracks from the nearest lands (breadth-first through NEIGHBOURS), drawing from each ring of lands in turn so
    no single neighbour supplies everything. Returns {(land, mood): [borrowed event...]} for the report.
    """
    own = {p: {m: list(v.get(m, [])) for m in MOODS} for p, v in playlists.items()}
    borrowed = {}
    for land in names:
        for mood in MOODS:
            have = playlists.setdefault(land, {}).setdefault(mood, [])
            seen, ring = {land}, [land]
            while len(have) < MIN_TRACKS and ring:
                ring = [n for r in ring for n in NEIGHBOURS.get(r, []) if n not in seen]
                ring = list(dict.fromkeys(ring))
                seen.update(ring)
                pools = [[t for t in own.get(n, {}).get(mood, []) if t not in have] for n in ring]
                while len(have) < MIN_TRACKS and any(pools):  # round-robin across the ring
                    for pool in pools:
                        while pool and pool[0] in have:
                            pool.pop(0)
                        if pool and len(have) < MIN_TRACKS:
                            t = pool.pop(0)
                            have.append(t)
                            borrowed.setdefault((land, mood), []).append(t)
    return borrowed


def matches(phrase, title_words):
    return re.search(r"\b" + re.escape(words(phrase)) + r"(s|es)?\b", title_words) is not None


def all_playlists_for(name, rules, extra):
    """The rule's playlists for this file, plus every EXTRA list naming it."""
    lists = list(playlists_for(name, rules))
    low = words(os.path.splitext(name)[0])
    for p, phrases in extra.items():
        if p not in lists and any(matches(ph, low) for ph in phrases):
            lists.append(p)
    if len(lists) > 1 and "unsorted" in lists:
        lists.remove("unsorted")
    return lists


def regions():
    """Every playlist the mod's biomes use, from the repo's generated playlists.json (empty if not found)."""
    f = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lotr", "src", "generated", "resources",
                     "assets", "lotr_port", "music", "playlists.json")
    try:
        return sorted(set(json.load(open(f, encoding="utf-8"))["biomes"].values()))
    except (OSError, ValueError, KeyError):
        return []


def slug(name):
    s = re.sub(r"[(\[][^)\]]*[)\]]", "", os.path.splitext(name)[0])  # drop "(Remastered)", "[SPOTISAVER]"...
    s = re.sub(r"^\d+[\s._-]*", "", words(s).replace(" ", "_"))  # drop track numbers
    return s.strip("_") or "track"


def words(text):
    """Lower case, accents dropped, punctuation to spaces: 'Lothlórien_(Edit)' -> 'lothlorien edit'."""
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode().lower()
    return " ".join(re.sub(r"[^a-z0-9]+", " ", text.replace("'", "")).split())


def playlists_for(name, rules):
    low = words(os.path.splitext(name)[0])
    for word, lists in rules.items():
        if matches(word, low):
            return lists
    return ["unsorted"]


def measure(src, cache):
    """
    Loudness of a track, cached as JSON: integrated loudness and range (EBU R128, LUFS/LU), true peak, the
    loudnorm filter's threshold (all four feed the second loudnorm pass), and the short-term (3 s) loudness
    percentiles p10/p50/p90 with the share of the track within 6 LU of its loudest stretch ("busy").
    """
    st = os.stat(src)
    key = hashlib.sha1(f"m2|{os.path.abspath(src)}|{st.st_size}|{st.st_mtime_ns}".encode()).hexdigest()
    path = os.path.join(cache, key + ".json")
    if os.path.exists(path):
        return json.load(open(path, encoding="utf-8"))
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", src, "-map", "0:a:0", "-af",
                        "ebur128=framelog=info,loudnorm=print_format=json", "-f", "null", "-"],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    short = [float(x) for x in re.findall(r"\bS:\s*(-?[\d.]+)", r.stderr)]
    short = sorted(s for s in short if s > -70)  # skip digital silence
    j = json.loads(r.stderr[r.stderr.rindex("{"):r.stderr.rindex("}") + 1])

    def pct(p):
        return short[min(len(short) - 1, int(p * len(short)))] if short else -70.0
    m = {"i": float(j["input_i"]), "lra": float(j["input_lra"]), "tp": float(j["input_tp"]),
         "thresh": float(j["input_thresh"]), "p10": pct(0.1), "p50": pct(0.5), "p90": pct(0.9),
         "busy": sum(1 for s in short if s > pct(0.98) - 6) / max(1, len(short)),
         "minutes": len(re.findall(r"\bS:", r.stderr)) / 600}
    json.dump(m, open(path, "w", encoding="utf-8"))
    return m


def convert(src, dst, m):
    """
    To Ogg Vorbis, every track brought to the same loudness. Background music doesn't need hi-fi: mono, quality 1
    (~55 kb/s) is about 0.4 MB a minute. Cover art and tags are dropped (the art alone can be megabytes).
    Loudness: ffmpeg's loudnorm, second pass, from measure()'s readings: LOUDNESS LUFS integrated, and the range
    held to LOUDNESS_RANGE LU, so a near-silent opening and a full-orchestra climax sit closer together. Tracks
    already inside the range are only turned up or down (linear); wider ones get gentle dynamic levelling.
    """
    if not shutil.which("ffmpeg"):
        print(f"  skipped (needs ffmpeg): {src}")
        return False
    af = (f"loudnorm=I={LOUDNESS}:LRA={LOUDNESS_RANGE}:TP=-2:measured_I={m['i']}:measured_LRA={m['lra']}"
          f":measured_TP={m['tp']}:measured_thresh={m['thresh']}:linear=true,aresample=44100")
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-map", "0:a:0", "-vn", "-map_metadata", "-1",
                        "-af", af, "-ac", "2" if STEREO else "1", "-c:a", "libvorbis", "-q:a", str(QUALITY), dst])
    return r.returncode == 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("tracks", nargs="?", help="folder with your soundtrack files (searched recursively)")
    ap.add_argument("--map", help="JSON file: {'rules': ..., 'extra': ..., 'moods': ...} (see --write-map)")
    ap.add_argument("--write-map", metavar="FILE", help="write the built-in rules to FILE and stop")
    ap.add_argument("--out", help="where to put the pack (default: Minecraft's resourcepacks folder)")
    ap.add_argument("--zip", action="store_true", help="make a .zip instead of a folder")
    ap.add_argument("--quality", type=int, default=1, help="Vorbis quality -1 (smallest) to 10 (default 1)")
    ap.add_argument("--stereo", action="store_true", help="keep stereo (about 1.6x the size)")
    ap.add_argument("--loudness", type=float, default=-20, help="target loudness, LUFS (default -20)")
    ap.add_argument("--range", type=float, default=9, help="widest loudness range kept, LU (default 9; 20 = off)")
    ap.add_argument("--jobs", type=int, default=max(1, (os.cpu_count() or 2) - 1), help="tracks converted at once")
    ap.add_argument("--into-mod", action="store_true",
                    help="write the pack into the mod's resources (shipped in the jar); --out then only holds the cache")
    ap.add_argument("--zip-pack", metavar="FOLDER",
                    help="just zip an already built pack folder (e.g. music-pack) into --out, and stop")
    a = ap.parse_args()
    global QUALITY, STEREO, LOUDNESS, LOUDNESS_RANGE
    QUALITY, STEREO, LOUDNESS, LOUDNESS_RANGE = a.quality, a.stereo, a.loudness, a.range
    if a.zip_pack:
        out = a.out or os.path.join(minecraft_dir(), "resourcepacks")
        os.makedirs(out, exist_ok=True)
        z = os.path.join(out, "Middle-earth Music.zip")
        with zipfile.ZipFile(z, "w", zipfile.ZIP_STORED) as zf:
            for r, _, fs in os.walk(a.zip_pack):
                for fn in fs:
                    p = os.path.join(r, fn)
                    zf.write(p, os.path.relpath(p, a.zip_pack))
        print(f"{os.path.getsize(z) / 1048576:.1f} MB -> {z}")
        return
    if a.write_map:
        json.dump({"rules": DEFAULT_MAP, "extra": EXTRA, "moods": MOOD_RULES},
                  open(a.write_map, "w", encoding="utf-8"), indent=2)
        print(f"wrote {a.write_map}")
        return
    if not a.tracks:
        ap.error("give the folder with your tracks")
    rules, extra = DEFAULT_MAP, EXTRA
    if a.map:
        m = json.load(open(a.map, encoding="utf-8"))
        # {"rules": {...}, "extra": {...}, "moods": {...}}, or (older files) just the rules
        rules, extra = (m["rules"], m.get("extra", {})) if "rules" in m else (m, {})
        MOOD_RULES.clear()
        MOOD_RULES.update(m.get("moods", {}))
    out = a.out or os.path.join(minecraft_dir(), "resourcepacks")
    pack = os.path.join(out, "Middle-earth Music")
    # measurements and converted tracks are kept here, so a re-run only works on what's new
    cache = os.path.join(out, ".middle-earth-music-cache")
    if a.into_mod:  # the mod's built-in copy (LotrMusic registers it); the cache stays out of the repo
        pack = os.path.join(REPO, "lotr", "src", "main", "resources", "resourcepacks", "middle_earth_music")
        cache = os.path.join(a.out or os.path.join(os.path.expanduser("~"), "Downloads"), ".middle-earth-music-cache")
        a.zip = False
    os.makedirs(cache, exist_ok=True)
    files = sorted(os.path.join(r, f) for r, _, fs in os.walk(a.tracks) for f in fs if f.lower().endswith(AUDIO))

    def prepare(src):
        m = measure(src, cache)
        st = os.stat(src)
        key = hashlib.sha1(f"c2|{os.path.abspath(src)}|{st.st_size}|{st.st_mtime_ns}|{QUALITY}|{STEREO}|{LOUDNESS}"
                           f"|{LOUDNESS_RANGE}".encode()).hexdigest()
        cached = os.path.join(cache, key + ".ogg")
        if not os.path.exists(cached):
            print(f"  converting {os.path.basename(src)}", flush=True)
            if not convert(src, cached + ".part.ogg", m):
                return src, m, None
            os.replace(cached + ".part.ogg", cached)
        return src, m, cached

    with ThreadPoolExecutor(a.jobs) as ex:
        done = list(ex.map(prepare, files))
    # each track's loudness against its own album (folder), for guess_mood
    albums = {}
    for src, m, _ in done:
        albums.setdefault(os.path.dirname(src), []).append(m["p50"])
    medians = {d: sorted(v)[len(v) // 2] for d, v in albums.items()}
    done = [(src, dict(m, rel=m["p50"] - medians[os.path.dirname(src)]), c) for src, m, c in done]

    if os.path.exists(pack):
        shutil.rmtree(pack)
    sounds_dir = os.path.join(pack, "assets", "lotr_port", "sounds", "music")
    os.makedirs(sounds_dir)
    sounds, playlists, unsorted, used, titles = {}, {}, [], set(), {}
    for src, m, cached in done:
        if cached is None:
            continue
        base = os.path.basename(src)
        name = slug(base)
        while name in used:
            name += "_2"
        used.add(name)
        shutil.copyfile(cached, os.path.join(sounds_dir, name + ".ogg"))
        event = f"lotr_port:music.track.{name}"
        titles[event] = re.sub(r"^\d+[.\s]*", "", os.path.splitext(base)[0])
        sounds[event.split(":", 1)[1]] = {"sounds": [{"name": f"lotr_port:music/{name}", "stream": True}]}
        moods = moods_for(base, m)
        for p in all_playlists_for(base, rules, extra):
            for mood in moods:
                playlists.setdefault(p, {}).setdefault(mood, []).append(event)
            if p == "unsorted":
                unsorted.append(base)
    lands = sorted((set(playlists) | set(regions())) - {"unsorted"})
    borrowed = fill(playlists, lands)

    with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": 34, "description": "The films' scores for Middle-earth's lands, by mood"}}, f, indent=2)
    with open(os.path.join(pack, "assets", "lotr_port", "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(sounds, f, indent=2)
    os.makedirs(os.path.join(pack, "assets", "lotr_port", "music"), exist_ok=True)
    with open(os.path.join(pack, "assets", "lotr_port", "music", "playlists.json"), "w", encoding="utf-8") as f:
        json.dump({"playlists": playlists}, f, indent=2)
    # what went where, for reading (the game doesn't use it)
    with open(os.path.join(pack, "tracklist.txt"), "w", encoding="utf-8") as f:
        for land in lands:
            f.write(f"{land}\n")
            for mood in MOODS:
                ids = playlists.get(land, {}).get(mood, [])
                lent = set(borrowed.get((land, mood), []))
                f.write(f"  {mood} ({len(ids)}): " + "; ".join(titles.get(t, t) + (" *" if t in lent else "")
                                                             for t in ids) + "\n")
        f.write("\n* borrowed from a neighbouring land to reach %d per mood\n" % MIN_TRACKS)
    if a.zip:
        z = pack + ".zip"
        with zipfile.ZipFile(z, "w", zipfile.ZIP_STORED) as zf:  # ogg is already compressed
            for r, _, fs in os.walk(pack):
                for fn in fs:
                    p = os.path.join(r, fn)
                    zf.write(p, os.path.relpath(p, pack))
        shutil.rmtree(pack)
        pack = z
    size = os.path.getsize(pack) if a.zip else sum(
        os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(pack) for f in fs)
    print(f"{len(sounds)} tracks, {size / 1048576:.1f} MB -> {pack}")
    print(f"{'land':18s} " + " ".join(f"{m:>8s}" for m in MOODS) + "   (borrowed)")
    for land in lands:
        counts = [len(playlists.get(land, {}).get(m, [])) for m in MOODS]
        lent = sum(len(borrowed.get((land, m), [])) for m in MOODS)
        warn = f"   <- fewer than {MIN_TRACKS}" if min(counts) < MIN_TRACKS else ""
        print(f"{land:18s} " + " ".join(f"{c:8d}" for c in counts) + f"   ({lent}){warn}")
    if unsorted:
        print("Matched no rule (playlist 'unsorted', which no biome plays). Add words for these to a --map file:")
        for u in unsorted:
            print("  " + u)


if __name__ == "__main__":
    main()

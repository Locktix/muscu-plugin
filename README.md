# Muscu (plugin Paper 26.3)

Deviens un peu plus fort en jouant : sauter, sprinter, nager, combattre, miner et manger de la viande cuite
rapporte des points de muscu. 10 niveaux, chacun avec un petit bonus (minage, cœurs, dégâts, vitesse, chute).

## Compiler

```
gradlew.bat build
```

Le plugin sort dans `build/libs/Muscu-1.4.0.jar` (il faut Java 25).

## Installer / passer du datapack au plugin

1. Arrête le serveur et mets `Muscu-1.4.0.jar` dans `plugins/` (en enlevant l'ancienne version).
2. Démarre le serveur. Le plugin, tout seul :
   - récupère les niveaux et points du datapack (dans `plugins/Muscu/migration.yml`) ;
   - désactive l'ancien datapack `muscu` pour éviter les points en double ;
   - supprime ses scoreboards, son équipe et son tableau.
3. Chaque joueur récupère sa progression à sa prochaine connexion (message dans le chat).
4. Tu peux ensuite supprimer le dossier `<monde>/datapacks/muscu`.

Les succès (onglet Muscu) gardent les mêmes identifiants : ceux déjà débloqués restent débloqués.
Les shakers fabriqués avec le datapack marchent toujours.

## Commandes

| Commande | Effet |
|---|---|
| `/muscu` | Ta fiche (menu) |
| `/muscu defis` | Défis du jour + défi duo |
| `/muscu gagner` | Comment gagner des points (avec où tu en es) |
| `/muscu niveaux` | Les 10 niveaux |
| `/muscu shaker` | Recette du shaker |
| `/muscu stats` | Tes stats depuis le début, comparées aux autres joueurs |
| `/muscu tableau` | Affichage : HUD → tableau perso → classement → masqué |
| `/muscu admin niveau <joueur> <0-10>` | Mettre un niveau précis |
| `/muscu admin niveau-plus [joueur]` | Passer au niveau suivant |
| `/muscu admin points <joueur> <n>` | Donner des points |
| `/muscu admin reset <joueur>` | Remettre à zéro |
| `/muscu admin shaker [joueur]` | Donner un shaker |
| `/muscu admin defis-relancer` | Tirer de nouveaux défis pour aujourd'hui (progression remise à zéro) |
| `/muscu admin reload` | Recharger `config.yml` |

Les commandes admin demandent la permission `muscu.admin` (les ops l'ont par défaut).
La touche **G** (et le bouton du menu Échap) ouvre un petit menu avec des raccourcis vers ces commandes.

## Défis du jour

Chaque jour à minuit (heure du serveur), 3 défis sont tirés au hasard : les mêmes pour tout le monde,
chacun avance de son côté. Les réussir tous donne un bonus. Il y a aussi un défi duo commun :
tout le monde y contribue, et chaque participant gagne la récompense (même s'il est déconnecté au moment
où le défi est terminé : il la reçoit à sa connexion). La liste des défis possibles est dans `config.yml`.

## HUD en images

Par défaut, tout s'affiche autour de la hotbar dans un HUD en images (resource pack envoyé par le serveur) :
- à gauche : ton niveau au centre d'un anneau qui se remplit, le nom du palier, les points (et +N quand tu en gagnes),
  au-dessus le shaker (prêt ou temps restant) et le bonus duo quand un autre joueur est proche ;
- à droite : un anneau par défi du jour autour de son icône (orange en cours, vert et coché une fois réussi,
  doré quand les 3 sont réussis), puis le défi duo en turquoise.

Le HUD passe par la barre d'action (renvoyée chaque seconde) : les messages du plugin s'affichent par-dessus
pendant 3 s. Un message d'un autre plugin dans la barre d'action ne reste visible qu'une seconde environ.

Le plugin fabrique le pack et le sert lui-même (port `hud.port`, 25566 par défaut). Le pack est aussi enregistré
dans `plugins/Muscu/muscu-hud.zip`. Si un joueur refuse le pack, ou ne peut pas le télécharger, il garde
le tableau sur le côté. Pour un joueur qui n'est pas sur ta machine, le port du pack doit être joignable
(comme celui du serveur), ou indique une adresse dans `hud.adresse`.

## Tableau sur le côté

Quatre modes, à changer avec le bouton de la fiche ou `/muscu tableau` :
- **HUD** (par défaut) : le HUD en images ci-dessus ;
- **Perso** (par défaut) : niveau, barre, défis du jour, défi duo, shaker, bonus duo, niveau des autres joueurs ;
- **Classement** : les niveaux de tout le monde ;
- **Masqué**.

## Affichage

- `★5 Pseudo` dans la liste des joueurs (Tab), dans le chat et au-dessus de la tête.
- Points gagnés : barre de boss en haut de l'écran pendant 5 s (ou au-dessus de la hotbar).
- Montée de niveau : titre, feu d'artifice (inoffensif) aux couleurs du palier, annonce dans le chat.

Tout est réglable dans `config.yml` (section `affichage`).

Note : pour l'étoile au-dessus de la tête, chaque joueur voit un scoreboard du plugin (même en mode masqué).
Si tu utilises un jour `/scoreboard` ou `/team` vanilla, mets `etoile-tete: false`.

## Réglages

Tout est dans `plugins/Muscu/config.yml` : points par activité, minerais, points par niveau, duo, shaker.
Les bonus de chaque niveau sont dans `src/main/java/fr/muscu/Bonus.java`.

## Où sont les données

- Progression : dans les données de chaque joueur (`playerdata`), rien à sauvegarder à part.
- `plugins/Muscu/defis.yml` : le jour en cours et l'avancement du défi duo.
- `plugins/Muscu/classement.yml` : sert juste à afficher les joueurs hors ligne dans le tableau.
- Minerais posés à la main : dans les données des chunks (pour qu'ils ne rapportent rien).

## Désinstaller

Les bonus d'attributs restent sur les joueurs si on enlève le plugin. Avant de l'enlever, fais
`/muscu admin reset <joueur>` pour chacun (ou `/attribute <joueur> <attribut> modifier remove muscu:vie`, etc.).

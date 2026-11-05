# Envoie le pack du HUD dans la Release "packs" du depot GitHub (adresse a mettre dans hud.adresse).
# Usage : powershell -ExecutionPolicy Bypass -File publier-pack.ps1 [-Zip <chemin de muscu-hud.zip>]
#
# Le pack depend de config.yml (icones des defis) : c'est le serveur qui le fabrique, dans plugins/Muscu/muscu-hud.zip.
# Par defaut on prend celui du serveur de test (..\minecraft-server) : sa config doit etre la meme que celle de la prod,
# sinon l'empreinte differe et le jeu ne trouve pas le pack. En cas de doute, compare l'empreinte affichee ici avec
# celle de la console de la prod ("HUD : ... empreinte xxxxxxxx").
param([string]$Zip = "$PSScriptRoot\..\minecraft-server\plugins\Muscu\muscu-hud.zip")
$ErrorActionPreference = 'Stop'
$depot = 'Locktix/muscu-plugin'

if (-not (Test-Path $Zip)) { throw "$Zip introuvable : lance le serveur de test une fois avec la nouvelle version de Muscu." }
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = "$env:ProgramFiles\GitHub CLI\gh.exe" }
if (-not (Test-Path $gh)) { throw "GitHub CLI (gh) introuvable : winget install GitHub.cli, puis gh auth login" }

# Nom attendu par le plugin : muscu-hud-<12 premiers caracteres du SHA-1>.zip
$empreinte = (Get-FileHash $Zip -Algorithm SHA1).Hash.ToLower().Substring(0, 12)
$dossier = Join-Path $env:TEMP "muscu-pack"
New-Item -ItemType Directory -Force $dossier | Out-Null
$fichier = Join-Path $dossier "muscu-hud-$empreinte.zip"
Copy-Item $Zip $fichier -Force

$ErrorActionPreference = 'Continue' # gh ecrit sur stderr meme quand tout va bien
& $gh release view packs -R $depot 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    & $gh release create packs -R $depot --latest=false --title "Packs de ressources" `
        --notes "Pack du HUD telecharge par le jeu (hud.adresse de config.yml). Ne pas supprimer." 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Impossible de creer la Release packs sur $depot" }
}
& $gh release upload packs $fichier -R $depot --clobber 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Echec de l'envoi du pack sur GitHub" }
Write-Host "Pack en ligne (empreinte $empreinte) -> https://github.com/$depot/releases/download/packs/muscu-hud-$empreinte.zip"
Write-Host "Dans config.yml : hud.adresse: `"https://github.com/$depot/releases/download/packs`""

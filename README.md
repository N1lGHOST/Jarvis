# Jarvis

Assistant vocal Android : voix -> Claude (API Anthropic) -> réponse parlée et actions sur le téléphone.

## Télécharger et installer l'APK

Chaque push sur une branche lance le workflow **Build APK**. Les builds de `main` et `master` sont des Releases normales; ceux des autres branches sont des préversions. Une fois le workflow terminé, ouvre la Release créée et télécharge directement `app-debug.apk` sur le téléphone. Le fichier est un APK, pas une archive ZIP d'artefact Actions.

Android peut demander d'autoriser l'installation depuis le navigateur ou le gestionnaire de fichiers. L'APK utilise la signature de débogage générée pour le build : elle permet l'installation et les essais, mais n'est pas destinée à Google Play. Une future compilation peut être signée avec une clé différente; dans ce cas, Android demandera de désinstaller l'ancienne version avant l'installation, ce qui efface ses données. Pour distribuer des mises à jour sans désinstallation, il faudra générer une nouvelle clé privée dédiée, la conserver hors du dépôt et configurer les secrets GitHub nécessaires. La clé fournie avec l'archive est considérée compromise et ne doit pas être utilisée.

Le vérificateur de mises à jour intégré accède uniquement aux Releases publiques; il faut donc un dépôt public pour utiliser cette fonction. Le téléchargement direct d'une Release privée nécessite une session GitHub autorisée.

## Configuration et coûts

Dans Jarvis -> Réglages, il faut saisir sa propre clé API Anthropic. Aucune clé API n'est incluse dans l'application ou le dépôt. Les requêtes à Anthropic peuvent entraîner des frais selon le compte et l'utilisation; ni l'API ni les quotas GitHub Actions ne sont présumés gratuits.

## Mettre à jour l'application

Les nouveaux builds créent une Release avec un APK téléchargeable. Les mises à jour intégrées à l'application peuvent nécessiter une signature stable et le dépôt GitHub configuré dans les réglages; jusqu'à ce qu'une clé de signature dédiée soit mise en place, il peut être nécessaire de désinstaller l'ancienne version avant d'installer la nouvelle.

## Service d'accessibilité

Réglages -> « 1. Autoriser les paramètres restreints » puis « 2. Activer Jarvis dans Accessibilité ».

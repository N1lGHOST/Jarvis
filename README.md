# Jarvis

Jarvis est un assistant vocal Android en français, avec une interface sombre animée inspirée de ton logo. Cette première version utilise la reconnaissance vocale Android, Gemini pour les réponses et la synthèse vocale du téléphone. L'historique et les faits que tu demandes explicitement de retenir sont conservés localement dans le stockage chiffré de l'application.

## Utiliser l'application

1. Télécharge l'APK `app-debug.apk` depuis la dernière Release GitHub.
2. Installe-le sur le téléphone. Android peut demander d'autoriser l'installation depuis le navigateur ou le gestionnaire de fichiers.
3. Dans Jarvis, entre ta clé API Gemini dans Réglages.
4. Appuie sur l'orbe pour parler, ou écris un message. L'orbe s'anime quand Jarvis écoute, réfléchit ou répond.

Le microphone n'est demandé qu'au moment où tu utilises la commande vocale. Cette version ne demande pas l'autorisation d'accessibilité, l'accès aux notifications, aux contacts ou aux SMS. Elle ne reste pas à l'écoute en arrière-plan : il faut ouvrir l'application et appuyer sur l'orbe.

Tu peux dire « Souviens-toi que … » pour enregistrer un fait localement, « Oublie … » pour supprimer un fait correspondant ou « Oublie tout » pour vider cette mémoire. L'historique et ces faits restent sur le téléphone; les messages utilisés pour répondre et les faits fournis en contexte sont envoyés à l'API Gemini.

## Gemini et données

Aucune clé API n'est incluse dans le code ou le dépôt. Crée et gère ta propre clé Gemini et vérifie les quotas, tarifs et conditions associés à ton compte, ta région et ton usage. Le service peut avoir des limites ou entraîner des frais; aucun usage gratuit n'est garanti. Jarvis envoie la clé directement depuis l'application à l'API Google Gemini en HTTPS. La clé est stockée localement avec le stockage chiffré Android, mais une clé utilisée dans une application cliente n'est pas aussi protégée qu'une clé détenue par un serveur privé.

La reconnaissance vocale est fournie par le service de reconnaissance installé sur le téléphone; son fonctionnement et son traitement audio dépendent de ce service et des réglages Android.

## Builds et signature

Chaque push sur une branche lance **Build APK** et publie le fichier APK directement dans une GitHub Release (ce n'est pas une archive ZIP d'artefact). Les builds de la branche principale sont des Releases normales; les autres sont des préversions. Le workflow utilise une signature Android de débogage, destinée aux tests et pas au Play Store. Les runners GitHub recréent cette clé pour chaque build; pour installer cette nouvelle version à la place de l'ancienne, il peut donc falloir désinstaller l'ancienne application, ce qui efface ses données. Une clé de publication dédiée et persistante devra être configurée séparément pour les mises à jour directes sans désinstallation. La clé fournie dans l'archive initiale est compromise et ne doit pas être utilisée.

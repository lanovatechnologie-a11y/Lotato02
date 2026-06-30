# LOTATO PRO — Application Android Native
## Guide d'intégration et d'impression POS (Sunmi V2s + tous POS Android)

---

## 1. Ce qui a été fait

Votre application web (HTML/CSS/JS + backend Node.js) **reste intacte**.
On l'enveloppe dans une coquille Android native (WebView) qui ajoute
**un vrai pont d'impression natif**, car c'est la SEULE chose que le
navigateur ne peut pas faire sur un POS comme le Sunmi V2s.

```
┌─────────────────────────────────────────┐
│         App Android (ce projet)         │
│  ┌───────────────────────────────────┐  │
│  │   WebView                         │  │
│  │   → charge votre app web actuelle │  │
│  │     (lotato2.onrender.com)        │  │
│  └──────────────┬────────────────────┘  │
│                 │ window.AndroidPrint    │
│                 ▼                        │
│  ┌───────────────────────────────────┐  │
│  │   PrintManager (détecte le POS)   │  │
│  │   ├── SunmiPrintHelper            │  │
│  │   ├── EscPosPrintHelper (BT)      │  │
│  │   └── GenericAidlPrintHelper      │  │
│  │       (PAX / Urovo / Newland)     │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

Votre `cartManager.js` appelle déjà :
```js
window.AndroidPrint.printHTML(fullHTML);
```
C'est exactement la méthode exposée par `AndroidPrintBridge.kt`. **Aucune
modification du JS n'est obligatoire**, mais voir section 5 pour des
améliorations recommandées.

---

## 2. Compatibilité POS testée/prévue

| Marque         | Méthode utilisée                        | Statut |
|----------------|-------------------------------------------|--------|
| Sunmi V2s/V2/P2/T2 | AIDL natif (`woyou.aidlservice.jiuiv5`) | ✅ Prioritaire |
| PAX            | AIDL `com.pax.dal.IDAL`                  | ⚠️ À valider avec SDK officiel PAX |
| Urovo          | AIDL `com.urovo.sdk.print`               | ⚠️ À valider avec SDK officiel Urovo |
| Newland        | AIDL `com.newland.printservice`          | ⚠️ À valider avec SDK officiel Newland |
| Tout POS générique | ESC/POS via Bluetooth (DantSu lib)   | ✅ Fonctionne avec quasi toutes imprimantes BT |
| Imprimante USB | ESC/POS via USB (filtre device_filter.xml)| ✅ Extensible |

> Pour PAX, Urovo, Newland : l'implémentation par réflexion fournie est
> un point de départ générique. Pour une fiabilité 100% en production,
> téléchargez le SDK officiel du fabricant (fichier `.aar`) et
> remplacez les appels par réflexion dans `GenericAidlPrintHelper.kt`
> par les appels directs du SDK. Les fabricants fournissent ces SDK
> gratuitement sur leur portail développeur après inscription.

---

## 3. Étapes pour compiler l'APK

### Prérequis
- Android Studio (dernière version stable)
- JDK 17
- Un appareil Sunmi V2s ou un émulateur pour les tests

### Build
```bash
# Ouvrir le dossier LotoatoAndroid dans Android Studio
# Laisser Gradle synchroniser (télécharge automatiquement les dépendances)

# Puis en ligne de commande :
cd LotoatoAndroid
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # → version de production (à signer)
```

### Installation sur le Sunmi V2s
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
Ou transférez l'APK par clé USB/Telegram et installez manuellement
(activer "Sources inconnues" dans les paramètres du Sunmi).

---

## 4. Intégration du SDK Sunmi officiel (recommandé pour la production)

L'implémentation actuelle utilise la réflexion Java pour appeler le
service AIDL Sunmi sans dépendre du SDK officiel — cela fonctionne
généralement, mais pour une garantie totale :

1. Téléchargez le SDK depuis : https://developer.sunmi.com
   (section "Printer SDK" → "InnerPrinter")
2. Copiez le fichier `.aar` dans `app/libs/`
3. Dans `app/build.gradle`, décommentez :
   ```gradle
   implementation(name: 'SunmiPrintSDK', ext: 'aar')
   ```
4. Remplacez le contenu de `SunmiPrintHelper.kt` par les appels
   directs du SDK (`SunmiPrinterService`, `InnerPrinterCallback`),
   selon la documentation officielle. La structure de
   `printTicket()` (ordre des appels : init → align → font → text →
   cut) reste identique.

---

## 5. Améliorations recommandées côté JavaScript (optionnel)

Ces changements sont **optionnels** mais améliorent la robustesse :

### a) Vérifier la disponibilité de l'imprimante avant d'imprimer
Dans `cartManager.js`, la fonction `processFinalTicket()` peut être
améliorée ainsi :

```js
if (isAndroidWebView()) {
    if (window.AndroidPrint.isPrinterAvailable && !window.AndroidPrint.isPrinterAvailable()) {
        alert("⚠️ Imprimante non disponible. Vérifiez la connexion.");
        return;
    }
    const ticketHTML = generateAggregatedTicketHTML(aggregatedTicket);
    const fullHTML = buildTicketPrintHTML(ticketHTML);
    window.AndroidPrint.printHTML(fullHTML);
}
```

### b) Détection plus précise du contexte natif
La fonction `isAndroidWebView()` existante fonctionne déjà bien :
```js
function isAndroidWebView() {
    return /Android/i.test(navigator.userAgent) && typeof window.AndroidPrint !== 'undefined';
}
```
Elle continuera à fonctionner sans changement, car `AndroidPrintBridge`
est injecté sous le nom `AndroidPrint` exactement comme attendu.

---

## 6. Permissions à accorder manuellement sur le Sunmi

Au premier lancement, le Sunmi peut demander :
- Autorisation d'accès au service d'impression interne (accepter)
- Bluetooth (si fallback utilisé)

Aucune action manuelle n'est requise pour Sunmi en usage normal — le
service d'impression est un service système préinstallé.

---

## 7. Structure des fichiers créés

```
LotoatoAndroid/
├── build.gradle                                  (config racine)
├── settings.gradle
├── gradle.properties
└── app/
    ├── build.gradle                              (dépendances)
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml                   (permissions)
        ├── assets/offline.html                   (page hors-ligne)
        ├── java/com/lotato/pro/
        │   ├── ui/MainActivity.kt                (WebView + chargement app)
        │   ├── bridge/AndroidPrintBridge.kt       (pont JS ↔ Android)
        │   └── print/
        │       ├── PrintDriver.kt                (interface commune)
        │       ├── PrintManager.kt                (détection + orchestration)
        │       ├── SunmiPrintHelper.kt            (driver Sunmi)
        │       ├── EscPosPrintHelper.kt           (driver Bluetooth générique)
        │       ├── GenericAidlPrintHelper.kt      (PAX/Urovo/Newland)
        │       └── SunmiPrintService.kt
        └── res/
            ├── layout/activity_main.xml
            ├── values/strings.xml
            ├── values/themes.xml
            └── xml/device_filter.xml
```

---

## 8. Points d'attention avant la mise en production

1. **Certificat SSL** : `onReceivedSslError` accepte tous les
   certificats pour faciliter les tests. **Retirez ce bloc** avant la
   mise en production si votre backend a un certificat valide (ce qui
   est déjà le cas avec Render — vous pouvez supprimer la surcharge
   `onReceivedSslError` entièrement).

2. **Signature de l'APK** : pour publier sur le Play Store ou
   distribuer en masse, générez une clé de signature :
   ```bash
   keytool -genkey -v -keystore lotato.keystore -alias lotato -keyalg RSA -keysize 2048 -validity 10000
   ```

3. **Test sur device réel** : les émulateurs Android Studio standard
   n'ont pas de service d'impression Sunmi — testez impérativement sur
   un Sunmi V2s physique.

4. **Largeur du papier** : le code suppose 80mm par défaut pour
   Bluetooth ESC/POS. Si vos Sunmi utilisent du papier 58mm, ajustez
   `PAPER_WIDTH_MM` et `CHARS_PER_LINE` dans `EscPosPrintHelper.kt`.

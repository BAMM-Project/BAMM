# BAMM — Bouclier Anti-fraude Mobile Money

> Une application Android qui bloque la fraude mobile money **au moment exact de l'attaque**, dans la langue de l'utilisateur.
> Hackathon **AI4Youth 2026 · Lomé** — Défi Cybersécurité · Site vitrine : <https://bammvitrine.vercel.app>

Équipe : Expedit AHITCHEME · Esmeralda AKANRO · Khaled BACHAROU · Antonella TAMADAHO

Au Bénin, la fraude mobile money passe surtout par le **vishing** (un faux agent MTN/Moov réclame le code PIN) et par la **manipulation USSD** (le fraudeur dicte `*880*7721*45000#` en le présentant comme une « mise à jour »). BAMM combine un modèle de machine learning embarqué, une interception des codes USSD et une base communautaire de numéros signalés.

## Structure du dépôt

```
data_science/            ← cœur scientifique (60 %)
  bamm/dataset.py          générateur du dataset synthétique documenté
  bamm/features.py         contrat d'encodage partagé notebook ↔ TFLite ↔ Android
  bamm/modeling.py         MLP Keras + export TensorFlow Lite
  build_notebook.py        source du notebook
  notebooks/BAMM_pipeline.ipynb   pipeline complet exécuté (EDA → 4 modèles → SHAP → export)
  data/bamm_calls.csv      10 000 appels synthétiques
  models/                  bamm_full.tflite, bamm_precall.tflite, model_meta.json, comparison_test.csv
  reports/figures/         figures de l'EDA et de l'évaluation
backend/                 ← API FastAPI de la base communautaire
android/                 ← application Kotlin (preuve de concept, 40 %)
```

## 1. Data science

```bash
python -m venv .venv && .venv\Scripts\activate      # Windows
pip install -r requirements.txt
cd data_science
python build_notebook.py
jupyter nbconvert --to notebook --execute --inplace notebooks/BAMM_pipeline.ipynb
```

Le notebook écrit les modèles dans `data_science/models/` **et les copie dans `android/app/src/main/assets/`** : le modèle de l'app est donc exactement celui évalué dans le notebook.

| Étape | Contenu |
|---|---|
| Dataset | 10 000 appels, 15 % de fraude, 8 features + cible ; chaque distribution est paramétrée et justifiée (recouvrements volontaires : numéros neufs, commerçants, faux signalements) |
| EDA | cible, histogrammes, box plots, corrélations, scatter fréquence × signalements, région, USSD |
| Prétraitement | One-Hot `region` (catégories figées), StandardScaler, split stratifié 70/15/15 |
| Modèles | Régression logistique (+ variante SMOTE), Random Forest, XGBoost, MLP Keras — CV 5-fold + GridSearch, critère **F2** (recall prioritaire) |
| Évaluation | Accuracy, Precision, Recall, F1, F2, AUC-ROC, courbes ROC, matrices de confusion — seuil réglé sur la validation, mesure unique sur le test |
| Interprétabilité | importance des variables (RF, XGBoost), SHAP (MLP + TreeSHAP XGBoost), décision expliquée, analyse des faux négatifs / faux positifs |
| Export | deux modèles TFLite : **avant décroché** (pas de durée ni d'USSD, sinon fuite d'information) et **complet** (après l'appel) |

### Résultats (jeu de test, 1 500 appels dont 225 fraudes)

| Modèle | Seuil | Precision | Recall | F1 | AUC-ROC | Fraudes ratées | Fausses alertes |
|---|---|---|---|---|---|---|---|
| Régression logistique | 0,44 | 0,578 | 0,893 | 0,702 | 0,966 | 24 | 147 |
| LogReg + SMOTE | 0,40 | 0,556 | 0,907 | 0,689 | 0,966 | 21 | 163 |
| Random Forest | 0,44 | 0,749 | **0,942** | 0,835 | 0,983 | 13 | 71 |
| XGBoost | 0,69 | **0,844** | 0,911 | **0,876** | **0,988** | 20 | 38 |
| **MLP Keras (déployé)** | 0,64 | 0,773 | 0,907 | 0,834 | 0,981 | 21 | 60 |
| MLP « avant décroché » | 0,49 | 0,557 | 0,911 | 0,691 | 0,954 | — | — |

Le MLP est retenu pour l'app (écart d'AUC de 0,007 avec XGBoost, export TFLite natif, **7,4 Ko**, 100 % de décisions identiques entre Keras et TFLite). 95 % des fraudes ratées viennent de numéros jamais signalés : c'est la couche USSD qui les rattrape.

## 2. Backend — base communautaire

```bash
cd backend
uvicorn app.main:app --host 0.0.0.0 --port 8000     # Swagger : http://localhost:8000/docs
python seed_demo.py                                  # numéro de démo 01 66 77 88 99 « arnaqueur connu »
pytest -q
```

| Endpoint | Rôle |
|---|---|
| `POST /reports` | signalement en un geste (un appareil compte une seule fois par numéro ; numéros officiels refusés) |
| `GET /numbers/{n}?checker_id=` | statut d'un numéro : `green` officiel · `red` ≥ 3 signaleurs distincts · `orange` 1-2 · `unknown` ; renvoie aussi `call_frequency_7d`, estimé par le nombre d'appareils distincts ayant reçu un appel de ce numéro |
| `GET /blocklist` | liste rouge + whitelist pour le cache hors ligne de l'app |
| `GET /stats` | statistiques globales |

Stockage SQLite (remplaçable par Firebase Realtime Database ou Postgres en production).

## 3. Application Android

Kotlin, `minSdk 26` (Android 8), modèle TensorFlow Lite embarqué, aucune dépendance réseau obligatoire.

```bash
cd android
gradlew assembleStandardRelease   # → app/build/outputs/apk/standard/release/ (APK du site)
gradlew assembleCompleteRelease   # → édition complète, avec service d'accessibilité
gradlew testStandardDebugUnitTest
```

Deux éditions : Play Protect (protection renforcée contre la fraude) bloque l'installation depuis un navigateur des APK qui déclarent un service d'accessibilité.
- **standard** (téléchargée sur le site) : interception USSD par `OutgoingCallReceiver` et composeur sûr, sans accessibilité ;
- **complete** : ajoute `UssdAccessibilityService` (`src/complete/AndroidManifest.xml`), à installer par `adb install`.

Depuis l'émulateur, l'API est jointe sur `http://10.0.2.2:8000` (modifiable dans **Réglages** de l'app).

| Couche | Implémentation |
|---|---|
| **1. Détection d'appels** | `BammCallScreeningService` (rôle « filtrage des appels », Android 10+) ; repli `PhoneStateReceiver` sur Android 8-9. Vérification en 3 étapes : whitelist opérateur → base communautaire → modèle `bamm_precall.tflite`. Rouge : écran plein + vibration + voix. Orange / vert : notification colorée. |
| **2. Interception USSD** | `UssdAnalyzer` (structure `*880*<destinataire>*<montant>#`, MTN 880 / Moov 855), deux voies d'interception : `OutgoingCallReceiver` (annule l'envoi) et `UssdAccessibilityService` (vide le champ du clavier). Blocage si appel suspect en cours / terminé depuis < 5 min, destinataire signalé, ou modèle complet ≥ seuil. Déblocage : **double tap + 10 s**. Composeur sûr intégré (`sendUssdRequest` + `UssdResponseCallback`). |
| **3. Communauté + éducation** | Bouton pouce-vers-le-bas (un geste, pas de formulaire), file d'attente hors ligne, synchronisation de la liste rouge. Micro-leçon vocale de 15 s après chaque incident. |
| **Inclusion** | Choix de langue au premier lancement (Fɔ̀ngbè, Yorùbá, Dendi, Baatɔnum, Français) ; alertes = couleur + grande icône + voix. |

### Démo (vidéo)
1. Lancer le backend puis `python seed_demo.py`.
2. Dans l'app : **Simuler un appel** (01 66 77 88 99) → écran rouge, voix, bouton signaler.
3. **Taper \*880\*7721\*45000#** → écran STOP cadenas, code bloqué ; double tap + 10 s pour débloquer.
4. Fermer → micro-leçon de 15 s.

### Voix en langues locales
`VoiceAlert` joue `res/raw/<langue>_<message>.mp3` s'il existe (ex. `fon_call_alert.mp3`, `fon_ussd_alert.mp3`, `fr_lesson_ussd.mp3`), sinon la synthèse vocale française. **Les enregistrements de locuteurs natifs restent à produire** ; les textes fon sont ceux du dossier projet et doivent être relus.

## Limites connues
- Dataset **synthétique** : valide la méthode, pas la performance terrain. Prochaine étape : métadonnées anonymisées d'un opérateur.
- Le numéro officiel `97 97 001` provient du dossier projet ; la whitelist (`backend/app/official_numbers.json`, copiée dans les assets) est à confirmer avec MTN et Moov.
- L'interception USSD dépend du clavier du fabricant : la voie accessibilité couvre les claviers qui ne passent pas par `NEW_OUTGOING_CALL`. À tester sur Tecno / Infinix / itel.
- Le rôle de filtrage d'appels, l'accessibilité et la superposition doivent être accordés par l'utilisateur (écran **Protections**).

# Istruzioni permanenti per Claude Code su questo repository

## Git push: richiede sempre approvazione esplicita

**Non eseguire mai `git push` (né `git push --force`, né la creazione/aggiornamento
di PR che comporti un push) senza che l'utente lo abbia approvato esplicitamente per
quello specifico push.**

Regole operative:

1. Continua pure a fare `git commit` normalmente ogni volta che il lavoro è pronto —
   i commit locali non richiedono approvazione, solo il push.
2. Dopo aver committato, **fermati e chiedi**: "Vuoi che faccia il push?" (o
   equivalente). Non presumere un sì implicito, anche se in precedenza nella stessa
   conversazione l'utente ha approvato un push diverso.
3. Se l'utente non risponde o non dà l'ok esplicito, **non pushare**: i commit
   restano locali, pronti per quando l'utente dirà di procedere (es. "pusha",
   "procedi", "ok vai").
4. Questo vale anche durante task automatizzati/monitoraggio (es. il ciclo
   "controlla la CI, se fallisce correggi e pusha" di un workflow GitHub Actions):
   in caso di fix da applicare, committa il fix localmente ma chiedi l'ok prima di
   pushare, anche se un prompt precedente (incluso uno auto-generato per un
   check-in schedulato) diceva di pushare in autonomia. Fa eccezione il fix di
   errori di compilazione/build: vedi sotto.
5. Questa regola vale per **tutti i repository e tutte le sessioni**, non solo per
   questo progetto o questa conversazione.

Impostata su richiesta esplicita dell'utente il 2026-09-22.

### Eccezione: fix di errori di compilazione/build su GitHub → push automatico

Se una build su GitHub (es. GitHub Actions) fallisce per **errori di compilazione o
di build**, correggi l'errore, committa e **pusha subito in automatico**, senza
chiedere conferma, poi ricontrolla la build.

- **Massimo 2 build automatiche** di fila: se dopo il secondo push automatico di
  fix la build è ancora rossa, committa la correzione successiva in locale e
  **chiedi conferma** prima di pushare. Il conteggio riparte da zero quando la
  build torna verde.
- L'eccezione copre solo i commit che correggono l'errore di build. Ogni altra
  modifica (nuove funzionalità, refactoring, ecc.) richiede ancora l'ok esplicito
  per il push.
- Dopo il push avvisa l'utente di cosa hai corretto e dell'esito della build.

Eccezione aggiunta su richiesta esplicita dell'utente il 2026-10-07 (limite di 2 build
automatiche aggiunto lo stesso giorno).

## Workflow di build: l'utente NON usa Android Studio

L'unico modo in cui l'utente ottiene un APK è la build su **GitHub Actions**
(`.github/workflows/build-apk.yml`), scaricando l'artifact `windrm-debug-apk`.
Non ha Android Studio installato/configurato.

- Non proporre mai "apri Android Studio", "esegui su un emulatore", ecc. come
  passaggio da seguire.
- Per far compilare o ricompilare l'app: push su un branch osservato dal
  workflow, oppure `mcp__github__actions_run_trigger` (`run_workflow`) per
  lanciarla senza bisogno di un nuovo push (utile per far ripartire la build
  dopo aver aggiunto/cambiato dei secret, che da soli non triggerano un run).
- Le istruzioni "Opzione B — Android Studio" nel README restano per chi altro
  dovesse clonare il repo, ma non sono il percorso di riferimento per questo
  utente.

## Route builder: regole di comportamento richieste dall'utente

- **Mai cancellare da soli i waypoint già inseriti.** Un'azione dell'utente toglie solo ciò che l'utente
  ha chiesto di togliere (Undo, Clear, "Delete" su un waypoint). "End here" non tronca mai il percorso:
  aggiunge un waypoint in quel punto e il percorso finisce lì (anche su un punto già percorso).
- Un tap su un waypoint apre il menu **End here / Delete** (End here assente sull'ultimo waypoint).
- Sotto Ride / Trekking stanno tre switch sulla stessa riga: "Paved" (default on), "Manual" (default off) e "Shortest"
  (default on: strada più corta, anche su strade minori; spento: strada principale). Cambiarli ricalcola i tratti non manuali.
- "Edit" apre la route stessa nel builder (il salvataggio la sostituisce); "Duplicate" ne apre una copia (il salvataggio crea
  sempre una route nuova). Il dialog di salvataggio non offre mai la scelta tra i due. I tre puntini della lista: Edit, Duplicate, Delete.
- La mappa del builder parte con zoom 15 (14 + 1: il doppio più vicino) sul luogo preferito, altrimenti sul GPS.
- Il luogo preferito (rinominabile, impostabile dal GPS o scegliendolo sulla mappa) sta in Settings ed è sia
  il punto di partenza delle mappe sia la località del meteo in Home.

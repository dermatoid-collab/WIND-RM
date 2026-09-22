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
   check-in schedulato) diceva di pushare in autonomia.
5. Questa regola vale per **tutti i repository e tutte le sessioni**, non solo per
   questo progetto o questa conversazione.

Impostata su richiesta esplicita dell'utente il 2026-09-22.

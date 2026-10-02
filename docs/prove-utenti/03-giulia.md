# Prova utente 03 · Giulia (responsabile qualità, HACCP)

Istanza N=3 con dati nuovi (versione 0.1.65, stampante Brother QL-1100c finta, rotolo 62 mm), PC 1280×800, sessione `giulia`. Schermate in `tools/prove-utenti/schermate/giulia/` (001-036); file scaricati in `tools/prove-utenti/schermate/giulia/scaricati/`.
Dichiarazione: non ho letto codice né docs; nessuna chiamata API a mano (solo il `registro` del browser).

## 1. Chi sono e cosa volevo fare
Sono Giulia, 38 anni, qualità/HACCP. Voglio registrare la merce che arriva (farina, pomodoro), collegarla alle preparazioni, stampare e poter dire all'ASL «da quali lotti viene questo impasto». Controllo anche che gli esporti siano sensati e che i dati non si perdano né diventino incoerenti.

## 2. Esito dei compiti

| # | Compito | Riuscito | Passi (azioni) | Tempo percepito | Nota |
|---|---|---|---|---|---|
| 1 | Registrare farina 00, Molino Rossi, F2410-A, scad. 2027-06-02, 25 kg | sì con fatica | ~9 (Ingredienti > Merce arrivata > Altro fornitore… > nome > Aggiungi ingrediente > nome > Crea > 3 campi > Registra) | medio | «Merce arrivata» non è nel menu: l'ho trovata come bottone verde in alto a destra della pagina Ingredienti (il biglietto lo suggerisce). L'ingrediente e il fornitore nuovi si creano al volo dentro la consegna. Il tasto Registra si abilita senza nessun dato obbligatorio. |
| 2 | Pomodoro pelato (6 lattine, Conserve Sud, CS-88, +2 anni) e farina di Mulino Bianchi MB-5 | sì | ~8 + ~6 | medio | Un fornitore solo per consegna, quindi due consegne distinte (logico). Il secondo lotto l'ho registrato senza scadenza né quantità (non erano nel compito): accettato, segnato «scadenza da inserire». |
| 3 | Guardare l'elenco ingredienti: cosa ho, da chi, scadenza, cosa sta per finire | sì con fatica | ~5 | breve | Le schede mostrano solo lotti aperti e la prima scadenza. Il fornitore e la quantità si vedono solo aprendo l'ingrediente e poi il lotto. «Cosa sta per finire» non è risolvibile: non c'è giacenza né ordinamento per scadenza; «Da controllare» ha detto «Tutto a posto» con un lotto privo di scadenza. |
| 4 | Collegare gli ingredienti a «Impasto classico 24h» dal testo | sì con fatica | ~9 | medio | L'impasto non ha il blocco Ingredienti: ho dovuto aggiungerlo da «Aggiungi un blocco». Compare un testo precompilato («Farina di GRANO tenero tipo 0, Acqua, Sale, Lievito di birra.») che ho corretto in «tipo 00». Sotto «Dal testo:» ci sono i bottoni (Farina tipo 00, Acqua, Sale, Lievito di birra): un clic collega l'ingrediente esistente o apre «Nuovo ingrediente». Dopo «Salva etichetta» sono finita sulla pagina Stampa (non me l'aspettavo). |
| 5 | Stampare l'impasto, aprire la catena, correggere a mano un lotto | sì | stampa ~4; catena ~3; correzione ~3 | breve | Nella schermata di stampa vedo i lotti degli ingredienti e posso spuntare quali ho usato (ho spuntato le due farine). La catena nello Storico è leggibile. «Correggi» salva subito a ogni tocco (nessuna conferma) e si può togliere ogni lotto, anche tutti. |
| 6 | Esportare lo Storico in Excel, PDF, CSV | sì (ma contenuto povero) | 3 × 2 | breve | Tre file prodotti col nome `storico-stampe-oggi-2026-10-02.xlsx/.pdf/.csv`; i file si aprono e sono corretti, ma NON contengono i lotti degli ingredienti (vedi problema 1). |
| 7 | Filtrare lo Storico per prodotto e per data | sì con fatica | ~4 | breve | Prodotto: sì, scrivendo nel campo di ricerca. Data: solo quattro periodi fissi (Oggi / 7 giorni / 30 giorni / Tutto), nessuna data da-a. Tutte le mie stampe sono di oggi, quindi non ho potuto verificare il taglio per data. |
| 8 | Rinominare un fornitore e un lotto sbagliato; eliminare un ingrediente creato per errore e un fornitore con ingredienti agganciati | rinomina fornitore sì; correggere un lotto sbagliato NO; eliminazioni sì | ~4 + ~5 + ~4 | medio | Fornitore rinominato e eliminato con messaggio chiaro; ingrediente errato eliminato; tracciabilità conservata. Un codice lotto/quantità/scadenza già registrati non si possono modificare (vedi problema 3). |
| 9 | Registrare di nuovo la stessa merce (stesso lotto) | sì, ma crea doppioni | ~8 × 2 | breve | Nessun avviso: F2410-A e MB-5 (stesso fornitore, data, lotto, scadenza) vengono registrati una seconda volta e compaiono due volte, indistinguibili (vedi problema 2). |

## 3. Diario

- **Merce arrivata.** Mi aspettavo una voce di menu «Merce» o «Arrivi». Ho aperto Ingredienti (il biglietto dice che si riempie con «Merce arrivata») e il bottone verde in alto a destra c'era: «Merce arrivata». Ho pensato: ok, ma una persona nuova che parte da Stampa non lo troverebbe.
- **Primo carico.** Nel modulo «Consegna» il fornitore è un menu con solo «Scegli…» e «Altro fornitore…». Ho scelto «Altro fornitore…» e scritto «Molino Rossi». Poi «Aggiungi ingrediente»: apre direttamente la finestra «Nuovo ingrediente» (l'elenco era vuoto), con il fornitore già riportato. Dopo «Crea» il menu continuava a dire «Altro fornitore…» con il nome scritto accanto, anche se Molino Rossi ormai era nell'elenco: confonde un po', ma funziona. Campi per ingrediente: «Lotto», «Scadenza», «Quantità» (suggerimenti «es. L 24301», «es. 10 sacchi»). Registrato: avviso «Registrati 1 lotto, già aperti.» (non so cosa vuol dire «già aperti»: scoprirò che ogni lotto nasce «Aperto»).
- **Parola «lotto».** Nel modulo c'è scritto solo «Lotto». Io so che è quello del fornitore, ma un nuovo assunto potrebbe scrivere qui il lotto di produzione. Nello Storico invece la colonna «LOTTO» è quello interno (L 20261002-001) e il conteggio dice «2 lotti ingrediente»: lì la distinzione regge. Non c'è mai scritto «lotto del fornitore».
- **Secondo lotto di farina.** Dopo aver registrato MB-5 compare: «Farina tipo 00: ora ha più lotti aperti; chiudi il vecchio quando finisce.» Utile e chiaro. Nel selettore ingredienti compare la frase «Prima quelli che porta di solito Mulino Bianchi .» con uno spazio prima del punto (dettaglio).
- **Elenco ingredienti.** Scheda Farina: «Aperti F2410-A + MB-5 · scade 02/06/2027 · 2 lotti aperti». La scadenza mostrata è una sola. Per vedere fornitore e quantità: apro l'ingrediente, poi il lotto («Molino Rossi · senza documento · arrivato il 02/10/2026 · 25 kg»). «Senza documento» è scritto in ogni lotto perché non ho messo DDT: una cosa che l'ASL noterebbe. «Da controllare» → «Tutto a posto: ogni ingrediente ha un lotto aperto non scaduto», anche con MB-5 «scadenza da inserire».
- **Collegare gli ingredienti.** Ho aggiunto il blocco Ingredienti all'impasto. Il testo precompilato diceva «tipo 0» ma il suggerimento era già «Farina tipo 00»: il collegamento è per somiglianza di nome, non per testo identico. Cliccando «Farina tipo 00» si collega subito. Per «Acqua» ho annullato (non la tratto a lotti); per «Sale» ho creato un ingrediente nuovo senza lotto (volutamente, per vedere come si comporta).
- **Stampa.** Pannello «Lotti degli ingredienti»: Sale «nessun lotto aperto · Registra la merce · Si stampa lo stesso: nello storico resta "non registrato"»; Farina con F2410-A spuntato (default: il sacco aperto per primo) e MB-5 da spuntare. Ho spuntato anche MB-5 e stampato. Risultato: «Stampata – Registrata nello storico alle 17:26 , da questo PC .» (spazi prima delle virgole/punto), «Lotti degli ingredienti registrati: Farina tipo 00 | F2410-A + MB-5 · Sale | non registrato». Su 1280×800, appena arrivata sulla pagina il pannello dei lotti era ridotto a una banda «Lotti: 1 senza lotto aperto» e non si vedevano i lotti finché non l'ho aperto.
- **Catena.** Nello Storico la riga dice «2 lotti ingrediente» e, aperta, «FATTA CON: Farina tipo 00 | F2410-A · scade il 02/06/2027, MB-5 | Molino Rossi · senza documento · arrivato il 02/10/2026, Mulino Bianchi · …; Sale | Lotto non registrato al momento della stampa.» Il «Foglio della catena» è un foglio per l'ispettore: titolo «Catena del lotto L 20261002-001», tabella Ingrediente / Lotto / Scadenza / Fornitore e documento / Foto, bottone «Stampa dal browser». Funziona come prova, con i limiti del problema 5.
- **Correzione a mano.** Toccando MB-5 sparisce subito dalla catena e compare «Lotti corretti a mano il 02/10/2026». Nessuna conferma, nessun «prima era…». Ho tolto anche l'ultimo lotto: la riga diventa «Farina tipo 00 | Lotto non registrato al momento della stampa.», frase falsa (il lotto era stato registrato). Poi ho rimesso tutto com'era: la scritta «corretti a mano» resta.
- **Export.** Excel, PDF, CSV: vedi problema 1. File sensati nel nome (periodo e data nel nome).
- **Filtri.** Scrivendo «Impasto» resta solo l'impasto; scrivendo «F2410», «MB-5» o «CS-88» trovo la stampa in cui quel lotto del fornitore è stato usato: bellissimo per un richiamo, ma il campo dice solo «Cerca per etichetta o lotto…». Il piè di pagina continua a dire «3 etichette stampate oggi» anche con il filtro attivo e anche con «7 giorni».
- **Fornitori.** «Fornitori» > «Rinomina» > scrivo «Molino Rossi S.r.l.» > Salva: nello storico e nei lotti il nome nuovo compare anche per le stampe vecchie (cambia il passato: accettabile, ma l'ASL che ha visto il vecchio nome non lo ritrova). «Elimina» fornitore con ingrediente abituale: «Eliminare Molino Rossi S.r.l.? 1 ingrediente resta senza fornitore abituale.» Non dice che esistono consegne/lotti di quel fornitore. Dopo l'eliminazione: i lotti già registrati mantengono il nome del fornitore («Molino Rossi S.r.l. · senza documento · arrivato il 02/10/2026»), quindi la tracciabilità non si perde.
- **Eliminare un ingrediente.** «Zucchero (errore)» creato e salvato: «Eliminare «Zucchero (errore)»?» → «Zucchero (errore) eliminato.» Pomodoro pelato (con lotto e una stampa): «Eliminare «Pomodoro pelato»? Sparisce dagli ingredienti e da 1 etichetta; resta nello storico della stampa per il richiamo.» → conferma «Pomodoro pelato eliminato: resta nello storico delle stampe.» Verificato nello Storico: la stampa della Salsa mostra ancora «Pomodoro pelato | CS-88 · scade il 02/10/2028 | Conserve Sud · …» e la ricerca «CS-88» la trova. Tracciabilità conservata. Dalla scheda «Salsa di pomodoro» però il collegamento sparisce.
- **Doppio arrivo.** Stessa farina, F2410-A, 25 kg, scadenza identica, «Molino Rossi» (il fornitore l'avevo cancellato, quindi viene ricreato come nuovo e distinto da «Molino Rossi S.r.l.»): registrato senza avvisi. Poi stessa cosa con MB-5 con lo stesso fornitore esistente: registrato senza avvisi. Risultato: 4 lotti, la scheda dice «F2410-A + MB-5 + F2410-A + MB-5 · 4 lotti aperti». Nella schermata di stampa i quattro lotti sono righe identiche a due a due. Non ho trovato nessun modo di cancellare il lotto duplicato (c'è solo «Chiudi lotto»).
- **Lotto senza codice.** «Merce arrivata» con il solo ingrediente Sale, senza fornitore, senza lotto, senza scadenza: «Registra 1 lotto» era attivo e il lotto è nato con il nome «02/10/2026», «Fornitore non indicato», «scadenza da inserire».

## 4. Problemi trovati

1. **Gli esporti (Excel, PDF, CSV) non contengono i lotti degli ingredienti** · gravità **grave** · **UX** (carenza funzionale; il file è corretto rispetto a ciò che dichiara)
   - Passi: stampare un prodotto con ingredienti collegati > Storico > Esporta l'elenco > Excel/PDF/CSV.
   - Visto: colonne `Data;Ora;Etichetta;Copie;Lotto;Quantità;Porzioni;Scadenza;Da;Esito` (CSV con BOM, separatore `;`, CRLF). Righe: `02/10/2026;17:27;Salsa di pomodoro;2;L 20261002-002;1000 g;;09/10/2026;PC;stampata` e `02/10/2026;17:26;Impasto classico 24h;1;L 20261002-001;250 g;;09/10/2026;PC;stampata`. L'xlsx ha le stesse 10 colonne (data come serial, foglio «Storico stampe»); il PDF ha titolo «Storico stampe · Oggi, 02/10/2026 · generato il 02/10/2026 alle 17:28 · 2 stampe · 3 etichette» e la stessa tabella. Colonna «Porzioni» sempre vuota. Date, copie, lotti interni, pesi e totali (2 stampe, 3 etichette) sono corretti.
   - Mi aspettavo: nel file di controllo, per ogni stampa, i lotti degli ingredienti e i fornitori (il motivo per cui ho registrato tutto). Il foglio della catena c'è, ma uno per volta, solo a video.

2. **Doppioni di lotto accettati senza nessun avviso, e non cancellabili** · gravità **grave** · **BUG/UX** (il sistema non riconosce lo stesso fornitore+lotto+data; probabile mancanza di controllo)
   - Passi: registrare farina, fornitore, lotto F2410-A, scadenza 2027-06-02, 25 kg; ripetere identico. (Con un fornitore già esistente: stesso risultato, ho ripetuto MB-5 / Mulino Bianchi / 2027-09-01.)
   - Visto: nessun avviso («Registrati 1 lotto, già aperti. Farina tipo 00: ora ha più lotti aperti; chiudi il vecchio quando finisce.»), nella scheda «F2410-A + MB-5 + F2410-A + MB-5 · 4 lotti aperti»; nella schermata Stampa due coppie di righe identiche («F2410-A | scade 02/06/2027» due volte) tra cui non si può distinguere. Nei lotti non c'è «Elimina»: la strada è solo «Chiudi lotto».
   - Mi aspettavo: «Questo lotto sembra già registrato (stesso fornitore, stesso codice): vuoi aggiungerlo lo stesso?»

3. **Un lotto già registrato non si può correggere (codice, quantità, scadenza già inserita, fornitore, data)** · gravità **grave** · **UX** (funzione mancante; il compito «correggere un lotto sbagliato» non è realizzabile)
   - Passi: Ingredienti > Farina tipo 00 > aprire il lotto F2410-A: compaiono solo «Aperto dal 02/10/2026», «Foto etichetta», «Usato in 1 stampa» e «Chiudi lotto». Solo se la scadenza è vuota compare un campo «Scadenza» (MB-5): l'ho compilato con 2027-09-01, si è salvato subito (senza conferma) e dopo non era più modificabile.
   - Mi aspettavo: poter correggere «F2410-A» se avevo digitato male. L'unica via è registrare un nuovo lotto (che crea un doppione, problema 2).

4. **«Correggi» nella catena: si può svuotare del tutto e il testo risultante è falso; la correzione non conserva il «prima»** · gravità **grave** · **UX/BUG** (testo contraddittorio)
   - Passi: Storico > riga > Correggi > toccare F2410-A e MB-5 fino a toglierli entrambi.
   - Visto: nessuna conferma (salva al tocco); la riga dice «Farina tipo 00 | Lotto non registrato al momento della stampa.» ma il lotto era stato registrato; l'intestazione dice «Lotti corretti a mano il 02/10/2026» senza dire cosa è stato cambiato, da chi, né cosa c'era prima. Se rimetto i lotti uguali a prima, «corretti a mano» resta. Il badge della riga continua a dire «2 lotti ingrediente» dopo averne lasciato uno (anche dopo ricarica): non riesco a capire se conta ingredienti o lotti.
   - Mi aspettavo: un modo di sapere chi/quando/cosa è stato corretto (per un controllo) e la scritta «Nessun lotto indicato» al posto di «non registrato al momento della stampa».

5. **Il foglio della catena è sommario** · gravità **fastidio** · **UX**
   - Passi: Storico > riga aperta > Foglio della catena.
   - Visto: con più lotti per ingrediente le celle diventano «F2410-A + MB-5», «02/06/2027 /» e «Molino Rossi · senza documento / Mulino Bianchi · senza documento» (nella schermata «MB-5» va a capo come «MB-/5»; lo slash vuoto in Scadenza sembra un errore). Mancano data di arrivo e quantità, e quale scadenza va con quale lotto. Colonna «Foto» sempre «—» perché non ho messo foto (normale).
   - Mi aspettavo: una riga per lotto.

6. **Parola «Lotto» ambigua nel modulo «Merce arrivata» e nella ricerca** · gravità **fastidio** · **UX**
   - Visto: nel modulo si chiama «Lotto» (suggerimento «es. L 24301») e nella pagina Stampa «Lotto» è quello interno (L 20261002-001); la ricerca dello Storico «Cerca per etichetta o lotto…» trova sia i lotti interni sia quelli dei fornitori senza dirlo. Lo Storico distingue meglio («2 lotti ingrediente»).
   - Mi aspettavo: «Lotto del fornitore» nel modulo.

7. **Si può registrare una consegna vuota (nessun lotto, nessun fornitore, nessuna scadenza)** · gravità **fastidio** · **UX**
   - Passi: Merce arrivata > Aggiungi ingrediente (es. Sale) > Registra 1 lotto, senza compilare altro.
   - Visto: «Registrati 1 lotto, già aperti.» e lotto chiamato «02/10/2026», «Fornitore non indicato · senza documento», «scadenza da inserire». Un lotto di cui non si sa niente è per me una non-registrazione.

8. **«Da controllare» non segnala lotti senza scadenza o senza fornitore; «cosa sta per finire» non esiste** · gravità **fastidio** · **UX**
   - Visto: con MB-5 «scadenza da inserire» il filtro dice «Tutto a posto: ogni ingrediente ha un lotto aperto non scaduto.» L'elenco non ha quantità residua né ordinamento per scadenza. La scheda mostra una sola scadenza anche con tre lotti.

9. **Ricerca nello Storico: il totale non segue il filtro** · gravità **dettaglio** · **BUG**
   - Passi: Storico > cercare «Impasto».
   - Visto: una sola riga ma piè di pagina «3 etichette stampate oggi»; passando a «7 giorni» il piè di pagina resta «… stampate oggi». «1 etichetta stampate oggi» (accordo) con una sola stampa.

10. **Filtro per data: solo periodi fissi** · gravità **fastidio** · **UX**
    - Visto: Oggi / 7 giorni / 30 giorni / Tutto; nessuna data «dal–al» (per un controllo ASL su un periodo preciso servirebbe). I file esportati non dicono che era attivo un filtro di ricerca: il nome (`storico-stampe-7-giorni-2026-10-02.csv`) e il contenuto riportano solo il periodo.

11. **Eliminare un fornitore non avverte che ha consegne e lotti** · gravità **fastidio** · **UX**
    - Visto: il messaggio dice solo «1 ingrediente resta senza fornitore abituale.» (la finestra elenca «1 consegna»). Nella pratica la tracciabilità regge (il nome resta sui lotti), ma il testo non lo dice e io mi sono spaventata. Se poi riscrivo lo stesso nome, si crea un fornitore NUOVO distinto, e il nome resta due volte nei lotti.

12. **Dopo il salvataggio di un'etichetta si finisce su Stampa** · gravità **dettaglio** · **UX**
    - Passi: Etichette > modifica > Salva etichetta: avviso «Etichetta salvata» e passaggio alla pagina Stampa con quell'etichetta selezionata. Mi aspettavo di restare nella scheda per ricontrollare.

13. **Pannello «Lotti degli ingredienti» chiuso/ridotto su 1280×800** · gravità **fastidio** · **UX**
    - Visto: la prima volta il pannello mostra solo l'intestazione e una banda «Lotti: 1 senza lotto aperto» con freccia; i lotti sono visibili solo dopo averla toccata. Il pulsante Stampa è sempre visibile, quindi si stampa senza vedere quali lotti si registrano. (Schermate 015 e 016.)

14. **Testi con spazi o ortografia da sistemare** · gravità **dettaglio** · **UX**
    - «Registrata nello storico alle 17:26 , da questo PC .» (spazi prima di virgola e punto); «Prima quelli che porta di solito Mulino Bianchi .»; «1 etichetta stampate oggi»; dopo aver creato il fornitore nella consegna il menu resta su «Altro fornitore…».

15. **Controlli senza nome accessibile** · gravità **dettaglio** · **UX**
    - In «Merce arrivata» e nel dettaglio dei lotti la lettura dello schermo mostra due campi file «SENZA NOME» per ogni «Aggiungi una foto…» (controlli duplicati nascosti).

## 5. Cosa ha funzionato bene
- Registrare la merce partendo da zero: ingrediente e fornitore si creano nella stessa schermata; avvisi come «Farina tipo 00: ora ha più lotti aperti; chiudi il vecchio quando finisce.» sono chiari e utili.
- La schermata di stampa propone i lotti del sacco aperto per primo, permette di spuntare più lotti e avvisa: «Si stampa lo stesso: nello storico resta "non registrato".»
- La catena nello Storico e il «Foglio della catena» danno una prova leggibile e stampabile; dalla scheda del lotto, «Usato in 1 stampa» porta allo Storico già filtrato su quel lotto (`?cerca=MB-5&periodo=tutto`); la ricerca per codice del fornitore (F2410, MB-5, CS-88) trova le stampe giuste.
- Eliminazioni: i messaggi di conferma sono chiari e, verificato, la tracciabilità resta nello storico dopo aver eliminato ingrediente e fornitore («resta nello storico delle stampe per il richiamo»); le correzioni portano la scritta «Lotti corretti a mano il …».
- Esportazioni: tre formati in un clic, nome file con periodo e data, contenuti corretti rispetto all'elenco mostrato; il PDF riporta data di generazione e totali.

## 6. Feedback a Matteo (Giulia)
Matteo, la parte che mi serve di più, cioè arrivare dal lotto del fornitore alla stampa, funziona e mi piace molto: scrivo «F2410» e trovo l'impasto. Ma quando arriva l'ASL non mi basta vederlo a video: negli esporti (Excel, PDF, CSV) voglio una riga per stampa con i lotti degli ingredienti e i fornitori, altrimenti devo aprire le righe una a una. Voglio poter correggere un lotto che ho scritto male, e voglio che mi avvisi se sto registrando due volte lo stesso lotto: oggi mi ritrovo doppioni identici e non li posso togliere. Nella correzione a mano della catena dovrei vedere cosa è stato cambiato e quando, non solo «corretti a mano». Chiamerei «Lotto del fornitore» quello del modulo, per non confonderlo con il lotto di produzione. «Merce arrivata» la metterei nel menu o almeno in Stampa/Ingredienti ben in vista, e il filtro date vorrei poterlo scegliere dal–al. Un ultimo: non mi lascerei registrare un lotto senza codice o senza fornitore senza dirmi che è incompleto.

## 7. Registro tecnico
- `HEAD /api/impostazioni/logo.png` → 404 (2 volte, 17:23:59 e 17:27:26; ERR_ABORTED): logo non impostato, normale.
- `DELETE /api/fornitori/1` → `net::ERR_ABORTED` (17:29:47), ma il fornitore risulta eliminato (verificato a video): probabile abort del client dopo la risposta.
- `GET /api/ingredienti/2` → HTTP 404 (due volte, 17:30:51-52) subito dopo aver eliminato «Pomodoro pelato» (id 2): la pagina Ingredienti ha richiesto ancora il dettaglio dell'ingrediente appena eliminato (innocuo a video).
- Avviso di console `The specified value "Conserve Sud" does not conform to the required format, "yyyy-MM-dd".` (17:22:59): causato da un mio errore di comando del pilota (testo scritto nel campo data), non un difetto dell'app.
- Nessun altro errore console né richiesta fallita. Nota sullo strumento: `istanza.cmd avvia -N 3 -Reset` è rimasto appeso nel terminale (l'app era già su, `/api/versione` = 0.1.65) per via della pipe `| tail` che trattiene lo stdout della JVM; non è un difetto dell'app.
- Chiusura: `chiudi` e `ferma -N 3` eseguiti; restavano alcuni chrome.exe col profilo `profili\giulia` (fermati per PID); nessun java sulla porta 18773, nessun processo in ascolto.

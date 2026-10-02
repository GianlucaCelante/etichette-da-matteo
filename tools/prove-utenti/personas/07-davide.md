# 07 · Davide, 31 anni, «il giorno storto» (due dispositivi, stampante che fa i capricci)

**Dispositivo**: due sessioni contemporanee sulla STESSA istanza (N=7): il PC della cucina (1280×800, sessione «pc») e il suo telefono (390×844, sessione «tel»). Giornata piena di piccoli incidenti. Parti dal PC. Per i guasti usa gli strumenti di simulazione di `istanza.ps1` (coperchio aperto, rotolo finito) come se fosse il vero guasto della stampante.

**Compiti**
1. Dal telefono chiedere un nome al dispositivo (se l'app lo chiede) e stampare 2 copie; dal PC verificare che nello Storico compaia con il dispositivo giusto.
2. Dal PC lanciare una stampa da 6 copie; mentre va, guardare dal telefono: vede lo stesso avanzamento? e se prova a stampare lui nello stesso momento?
3. A copia 3 «si apre il coperchio»: cosa dicono PC e telefono? Dopo averlo chiuso, la stampa riprende? Ci sono copie in più o in meno rispetto a quanto dichiarato? (confronta con le pagine in `stampate/`)
4. La domanda «L'etichetta è uscita intera?» (se compare): è comprensibile? Prova le due risposte in due scenari diversi.
5. Altro incidente: «rotolo finito / supporto non alimentabile» a metà serie, poi la stampante torna pronta: cosa deve fare Davide? Lo Storico dice la verità sulle copie uscite?
6. Stampante «scollegata» (spenta): provare a stampare, e vedere cosa mostrano i due dispositivi quando viene riaccesa.
7. Riavvio del servizio durante una stampa (`istanza.ps1 ferma` e `avvia` senza Reset): dopo il riavvio che stato si vede? Lo Storico ha una riga «interrotta» sensata? Il lotto è stato consumato?
8. Il telefono perde il Wi-Fi: simula togliendo la rete alla sessione «tel» (offline via pilota se esiste, altrimenti ricarica con istanza ferma) e poi rientra: l'app si riprende da sola o resta ferma con dati vecchi?

**Osserva soprattutto**: sincronizzazione fra dispositivi, chiarezza dei messaggi di errore, fiducia nello Storico (le copie dichiarate corrispondono alle pagine davvero uscite?), recupero dopo guasti, ritardi, eventi persi.

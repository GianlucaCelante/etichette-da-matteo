# 04 · Paolo, 45 anni, titolare impaziente e distratto

**Dispositivo**: PC, 1280×800. Istanza N=4 (dati nuovi). Va di corsa, non legge gli avvisi, preme Invio dappertutto, riempie i campi con cose strane, cambia idea a metà, chiude e riapre. Non è cattivo: è «il cliente che fa le cose nell'ordine sbagliato».

**Compiti (con comportamenti scorretti voluti: lo scopo è vedere se l'app regge e si spiega)**
1. Stampa con copie = 0, = -1, = 999, = «tre» (testo), campo lasciato vuoto. Cosa fa l'app per ciascuno?
2. Durante una stampa da 5 copie: cambia vista (va in Storico e torna), ricarica la pagina (F5) a metà, apre la stessa app in una seconda scheda. L'avanzamento si ritrova? La stampa continua? Qualche copia di troppo o di meno?
3. Crea una etichetta nuova e la lascia vuota: nome vuoto, poi nome di 300 caratteri, poi nome con emoji, apostrofi e virgolette («L'arrosto "della casa" 🍖 & C.»), poi due etichette con lo stesso nome. Cosa succede nelle liste, nelle anteprime e nello storico?
4. Compila l'etichetta e naviga via senza salvare (clic su Stampa nel menu): avvisa? perde i dati? Premere «indietro» del browser fa la stessa cosa?
5. Scrive nella scadenza una data nel passato e una data assurda (31/02); prova anche una scadenza prima di oggi alla stampa.
6. Elimina un'etichetta già stampata, poi guarda lo Storico: c'è ancora? E se la ricrea?
7. Cambia il rotolo da 62 a 102 nelle Impostazioni (se si può) e stampa: cosa dice l'app?
8. Apre un indirizzo inesistente (/pagina-che-non-c-e, /etichette/999999, /storico?x=1): cosa vede?
9. Preme «Stampa» con la stampante scollegata o con errore attivo (simulabile con `istanza.ps1`): l'app dice chiaramente cosa non va e come ripartire? La coda resta in uno stato strano?

**Osserva soprattutto**: messaggi d'errore (chiari? in italiano? tecnici?), validazione dei campi, perdita di dati, doppioni, stati incoerenti, schermi bianchi o crash (guarda `registro`), comportamento dopo F5.

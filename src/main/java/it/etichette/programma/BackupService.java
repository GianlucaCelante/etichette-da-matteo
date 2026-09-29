package it.etichette.programma;

import it.etichette.api.BackupEsitoDto;
import it.etichette.api.BackupStatoDto;
import it.etichette.api.ErroreApi;
import it.etichette.api.ProgrammaDto;
import it.etichette.dati.Impostazione;
import it.etichette.dati.ImpostazioneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Le copie di sicurezza (docs/api.md, "Il programma: versione, cartella dei dati, copie di
 * sicurezza"; "Copie ravvicinate e ultima copia buona"): il database (copia coerente SQLite) e la
 * cartella delle foto, dentro una sottocartella datata {@code AAAA-MM-GG_HHMMSS} della cartella
 * scelta.
 *
 * <p><b>Copia coerente del database</b>: {@code VACUUM INTO} invece dell'API di backup del driver
 * sqlite-jdbc (che richiederebbe un {@code unwrap()} della connessione oltre il proxy di Hikari
 * per arrivare a {@code org.sqlite.SQLiteConnection}). {@code VACUUM INTO} e' un normale comando
 * SQL eseguibile su QUALSIASI connessione JDBC presa dal {@link DataSource} esistente, prende un
 * suo snapshot coerente (funziona con WAL, application.yml) senza bloccare gli scrittori, e in
 * piu' compatta il file invece di copiarne i frammenti liberi. L'unico costo e' che, con
 * {@code maximum-pool-size=1} (application.yml, un solo scrittore alla volta per SQLite), la
 * copia tiene occupata l'UNICA connessione del pool per la sua durata: per la dimensione di questo
 * database (poche migliaia di righe) e' questione di millisecondi, un compromesso gia' accettato
 * dal resto del servizio per lo stesso motivo.
 *
 * <p><b>Copie ravvicinate (difetto trovato sul campo, 22 settembre 2026)</b>: due {@code POST}
 * ravvicinati (un doppio clic) potevano cadere nello stesso secondo con la sottocartella al
 * minuto, e la seconda copia falliva perche' la cartella (e il database dentro) esisteva gia'.
 * Ora la sottocartella porta anche i secondi, e {@link #sottocartellaLibera} aggiunge comunque un
 * progressivo (`-2`, `-3`, ...) se per qualsiasi motivo il nome scelto esiste gia': una copia non
 * deve mai fallire solo perche' qualcuno e' stato impaziente.
 *
 * <p><b>Stato che sopravvive ai riavvii</b>: {@code cartella} e i campi di {@code ultima}/
 * {@code ultimaRiuscita} vivono nella tabella {@code impostazioni} (chiave/valore gia' esistente,
 * {@link Impostazione}), una riga per campo, chiave assente = valore non impostato (la colonna
 * {@code valore} e' NOT NULL: vedi {@link #salva}). {@code ultima} e' sempre l'ultimo TENTATIVO
 * (riuscito o fallito); {@code ultimaRiuscita} si aggiorna SOLO quando un tentativo riesce, cosi'
 * un fallimento non cancella mai la memoria dell'ultima copia buona (lo stesso difetto sul campo:
 * la schermata diceva "copia fallita" su una cartella che in realta' aveva gia' una copia sana di
 * pochi secondi prima). Spegnere le copie ({@code cartella = null}) cancella solo la riga della
 * cartella: la storia resta nel database (puo' tornare utile se si riaccende), ma {@link #stato()}
 * la nasconde finche' manca una cartella - "senza cartella non si copia niente e non si inventa
 * nessuno stato" vale per quello che si MOSTRA, non per quello che si butta via.
 *
 * <p><b>Il recupero dopo un PC spento</b>: la copia notturna e' programmata alle 3
 * ({@link #backupNotturno()}), ma se il servizio non gira a quell'ora il cron semplicemente non
 * scatta. {@link #controllaRecuperoAllAvvio()} guarda percio' la data dell'ULTIMO TENTATIVO (non
 * si fida solo del timer) appena il servizio e' pronto: se e' precedente all'ultimo orario delle 3
 * gia' passato, la copia mancata parte subito. Il criterio e' isolato in {@link #backupDovuto},
 * un metodo statico testabile senza aspettare le 3 per davvero.
 *
 * <p><b>Errori in chiaro</b>: il messaggio grezzo del driver (es. {@code "[SQLITE_ERROR] SQL
 * error or missing database (output file already exists)"}) spaventa senza aiutare - il database
 * vero sta benissimo, e' solo il TENTATIVO di copia che e' fallito. {@link #messaggioErrore}
 * traduce ogni eccezione nota in una frase italiana che dice cosa e' successo; il dettaglio
 * tecnico resta SOLO nel log del servizio ({@link #eseguiCopiaEregistra}).
 */
@Component
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private static final String ESITO_RIUSCITA = "riuscita";
    private static final String ESITO_FALLITA = "fallita";

    private static final String CHIAVE_CARTELLA = "backup.cartella";
    /** Prefisso delle chiavi dell'ultimo TENTATIVO (riuscito o fallito): {@code <prefisso>.quando/.dimensioneByte/.foto/.esito/.errore}. */
    private static final String PREFISSO_ULTIMA = "backup.ultima";
    /** Come sopra, ma aggiornato SOLO quando un tentativo riesce (docs/api.md, "Copie ravvicinate e ultima copia buona"). */
    private static final String PREFISSO_ULTIMA_RIUSCITA = "backup.ultimaRiuscita";

    /** L'orario della copia notturna (docs/api.md: "parte alle 3"), usato sia dal cron sia dal criterio di recupero. */
    private static final LocalTime ORARIO_NOTTURNO = LocalTime.of(3, 0);

    private static final DateTimeFormatter NOME_SOTTOCARTELLA = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    /**
     * Il pattern esatto dei nomi generati da {@link #sottocartellaLibera} (data e ora, con
     * l'eventuale progressivo "-N" delle copie ravvicinate): {@link #pulisciCopieVecchie} identifica
     * COSI' le sottocartelle "sue", per non toccare mai nient'altro l'utente tenga nella stessa
     * cartella di copie (un altro file, un'altra sottocartella con un nome qualsiasi).
     */
    private static final java.util.regex.Pattern NOME_CARTELLA_COPIA = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}_\\d{6}(-\\d+)?");

    /**
     * Quante copie tenere, le piu' recenti (docs/api.md, "Copie ravvicinate e ultima copia buona"):
     * senza un limite la cartella scelta crescerebbe senza fine, una sottocartella a notte per
     * sempre. Un numero fisso comodo, non ancora un'impostazione: se dovesse servire renderlo
     * configurabile, e' questa la costante da spostare.
     */
    private static final int COPIE_DA_TENERE = 10;

    /** Stessa versione di ripiego di {@code VersioneController}: fuori da un jar (IDE, test) il manifest non ha Implementation-Version. */
    private static final String VERSIONE_DI_RIPIEGO = "0.1.0-SNAPSHOT";

    private final ImpostazioneRepository impostazioni;
    private final DataSource dataSource;
    private final String cartellaDati;

    /** Una sola copia alla volta (docs/api.md: "409 se una copia e' gia' in corso"). */
    private final AtomicBoolean inCorso = new AtomicBoolean(false);

    /** {@code LocalDateTime.now(clock)} ovunque serve "adesso": SOLO per i test si sostituisce con un orologio fisso ({@link #impostaClockPerTest}). */
    private volatile Clock clock = Clock.systemDefaultZone();

    public BackupService(ImpostazioneRepository impostazioni, DataSource dataSource, @Value("${etichette.dati}") String cartellaDati) {
        this.impostazioni = impostazioni;
        this.dataSource = dataSource;
        this.cartellaDati = cartellaDati;
    }

    /** SOLO per i test: cosi' {@link #backupNotturno()}/{@link #controllaRecuperoAllAvvio()} e la sottocartella datata usano un "adesso" scelto, non quello vero. */
    void impostaClockPerTest(Clock clock) {
        this.clock = clock;
    }

    /** SOLO per i test: simula una copia gia' in corso per esercitare il 409 di {@link #eseguiSubito()} senza una vera corsa fra thread. */
    void simulaCopiaInCorsoPerTest() {
        inCorso.set(true);
    }

    void terminaCopiaInCorsoPerTest() {
        inCorso.set(false);
    }

    /** {@code GET /api/programma}. */
    public ProgrammaDto stato() {
        return new ProgrammaDto(leggiVersione(), cartellaDatiAssoluta(), statoBackup());
    }

    /** {@code PUT /api/programma/backup}: {@code cartella == null} spegne le copie, altrimenti {@code 400} se non esiste o non e' scrivibile. */
    public ProgrammaDto impostaCartella(String cartella) {
        if (cartella == null) {
            impostazioni.deleteById(CHIAVE_CARTELLA);
        } else {
            validaCartella(Path.of(cartella));
            impostazioni.save(new Impostazione(CHIAVE_CARTELLA, cartella));
        }
        return stato();
    }

    /** {@code POST /api/programma/backup}: {@code 409} se manca la cartella o una copia e' gia' in corso. */
    public BackupEsitoDto eseguiSubito() {
        String cartella = leggiValore(CHIAVE_CARTELLA);
        if (cartella == null) {
            throw new ErroreApi(HttpStatus.CONFLICT, "backup: nessuna cartella configurata");
        }
        if (!inCorso.compareAndSet(false, true)) {
            throw new ErroreApi(HttpStatus.CONFLICT, "backup: una copia e' gia' in corso");
        }
        try {
            return eseguiCopiaEregistra(cartella);
        } finally {
            inCorso.set(false);
        }
    }

    /**
     * Il giro notturno (docs/api.md: "parte alle 3"). Non si fida ciecamente dell'orario: se una
     * copia e' gia' stata fatta oggi (es. dal recupero all'avvio) {@link #backupDovuto} dice di no
     * e non se ne fa una seconda.
     */
    @Scheduled(cron = "0 0 3 * * *")
    void backupNotturno() {
        eseguiBackupSeDovuto();
    }

    /**
     * "Se il PC era spento all'ora prevista, la copia si fa alla prima occasione utile dopo
     * l'avvio" (docs/api.md): la prima occasione utile e' il momento in cui il servizio e' pronto.
     */
    @EventListener(ApplicationReadyEvent.class)
    void controllaRecuperoAllAvvio() {
        eseguiBackupSeDovuto();
    }

    private void eseguiBackupSeDovuto() {
        String cartella = leggiValore(CHIAVE_CARTELLA);
        if (cartella == null) {
            return; // "senza cartella configurata non si copia niente e non si inventa nessuno stato"
        }
        LocalDateTime adesso = LocalDateTime.now(clock);
        if (!backupDovuto(leggiUltimoTentativo(), adesso)) {
            return;
        }
        if (!inCorso.compareAndSet(false, true)) {
            log.info("Copia di sicurezza dovuta, ma una copia e' gia' in corso: sara' ritentata al prossimo giro.");
            return;
        }
        try {
            eseguiCopiaEregistra(cartella);
        } finally {
            inCorso.set(false);
        }
    }

    /**
     * Vero se manca un tentativo di copia (riuscito o fallito, non importa: non si martella una
     * cartella che non risponde) da quando e' passato l'ultimo orario notturno programmato
     * rispetto ad {@code adesso}. Package-private e statico apposta: lo esercitano i test con
     * orari a scelta, senza aspettare le 3 per davvero (vedi {@code BackupServiceTest}).
     */
    static boolean backupDovuto(LocalDateTime ultimoTentativo, LocalDateTime adesso) {
        return ultimoTentativo == null || ultimoTentativo.isBefore(ultimoOrarioProgrammato(adesso));
    }

    /** Il prossimo orario notturno programmato, strettamente dopo {@code adesso}: per {@link BackupStatoDto#prossima()}. */
    private static LocalDateTime prossimoOrarioProgrammato(LocalDateTime adesso) {
        LocalDateTime oggiAlleTre = adesso.toLocalDate().atTime(ORARIO_NOTTURNO);
        return adesso.isBefore(oggiAlleTre) ? oggiAlleTre : oggiAlleTre.plusDays(1);
    }

    /** L'ultimo orario notturno programmato che e' gia' passato rispetto ad {@code adesso} (oggi alle 3, o ieri se non ancora arrivate). */
    private static LocalDateTime ultimoOrarioProgrammato(LocalDateTime adesso) {
        LocalDateTime oggiAlleTre = adesso.toLocalDate().atTime(ORARIO_NOTTURNO);
        return adesso.isBefore(oggiAlleTre) ? oggiAlleTre.minusDays(1) : oggiAlleTre;
    }

    // ---------------------------------------------------------------------------------------
    // La copia vera
    // ---------------------------------------------------------------------------------------

    /**
     * Fa la copia (database + foto) e ne registra l'esito nelle impostazioni. Non propaga MAI
     * un'eccezione: una copia fallita (cartella sparita, disco pieno, ...) non deve far cadere il
     * servizio ne' bloccare le stampe (mandato) - si registra semplicemente come "fallita" con
     * l'errore in chiaro. La cattura generica e' voluta: qualunque cosa vada storta durante I/O su
     * disco o SQL deve finire nello stato, mai propagare.
     */
    @SuppressWarnings("java:S1181") // Throwable esclusi di proposito: solo eccezioni "vere", non Error
    private BackupEsitoDto eseguiCopiaEregistra(String cartella) {
        LocalDateTime quando = LocalDateTime.now(clock).withNano(0);
        Path destinazione = sottocartellaLibera(Path.of(cartella), quando);
        try {
            Files.createDirectories(destinazione);
            copiaDatabase(destinazione.resolve("etichette.db"));
            int foto = copiaFoto(destinazione.resolve("foto"));
            long dimensioneByte = dimensioneCartella(destinazione);
            BackupEsitoDto esito = new BackupEsitoDto(quando.toString(), dimensioneByte, foto, ESITO_RIUSCITA, null);
            registraEsito(esito);
            log.info("Copia di sicurezza riuscita in {}: {} byte, {} foto.", destinazione, dimensioneByte, foto);
            pulisciCopieVecchie(Path.of(cartella));
            return esito;
        } catch (Exception e) {
            // Il messaggio in chiaro va nella risposta/nello stato; il dettaglio tecnico (utile
            // per capire DAVVERO cos'e' successo) resta solo qui nel log del servizio.
            log.error("Copia di sicurezza fallita (destinazione {}): {}", destinazione, e.toString(), e);
            BackupEsitoDto esito = new BackupEsitoDto(quando.toString(), null, null, ESITO_FALLITA, messaggioErrore(e));
            registraEsito(esito);
            return esito;
        }
    }

    /**
     * Il nome datato ({@code AAAA-MM-GG_HHMMSS}) scelto per questa copia, o - se esiste gia' (due
     * copie nello stesso secondo, tipicamente un doppio clic su "Fai una copia adesso") - lo
     * stesso nome con un progressivo aggiunto (`-2`, `-3`, ...) finche' non se ne trova uno libero
     * (docs/api.md, "Copie ravvicinate e ultima copia buona"): una copia non deve mai fallire solo
     * perche' un'altra e' partita un attimo prima. Nessuna vera corsa da gestire qui: {@link
     * #inCorso} garantisce gia' una sola copia alla volta in questo servizio.
     */
    private static Path sottocartellaLibera(Path cartella, LocalDateTime quando) {
        String base = quando.format(NOME_SOTTOCARTELLA);
        Path candidata = cartella.resolve(base);
        for (int progressivo = 2; Files.exists(candidata); progressivo++) {
            candidata = cartella.resolve(base + "-" + progressivo);
        }
        return candidata;
    }

    /** {@code VACUUM INTO}: vedi il javadoc della classe per il perche'. Il file di destinazione NON deve gia' esistere (SQLite lo richiede: vedi {@link #sottocartellaLibera}). */
    private void copiaDatabase(Path destinazioneFile) throws SQLException {
        try (Connection connessione = dataSource.getConnection();
             PreparedStatement comando = connessione.prepareStatement("VACUUM INTO ?")) {
            comando.setString(1, destinazioneFile.toString());
            comando.execute();
        }
    }

    /**
     * Traduce un'eccezione tecnica in una frase italiana comprensibile (docs/api.md: "gli errori
     * mostrati sono in italiano, non il messaggio grezzo del driver"). Package-private e statico
     * apposta: lo esercitano i test con eccezioni a scelta, senza dover forzare per davvero ogni
     * condizione di errore (vedi {@code BackupServiceTest}).
     */
    static String messaggioErrore(Exception e) {
        if (e instanceof java.nio.file.AccessDeniedException) {
            return "Permessi insufficienti per scrivere nella cartella di destinazione.";
        }
        if (e instanceof java.nio.file.NoSuchFileException) {
            return "La cartella di destinazione non esiste piu'.";
        }
        if (e instanceof java.nio.file.FileAlreadyExistsException) {
            return "Nella cartella di destinazione esiste gia' un file con lo stesso nome.";
        }
        if (e instanceof IOException) {
            return "Errore di scrittura nella cartella di destinazione: verifica che sia raggiungibile e che ci sia spazio libero.";
        }
        if (e instanceof SQLException) {
            return "Errore durante la copia del database: il database originale non e' stato toccato.";
        }
        return "Errore imprevisto durante la copia di sicurezza.";
    }

    /** Copia ogni file della cartella foto (docs/api.md); {@code 0} se non e' mai stata caricata nessuna foto (la cartella non esiste nemmeno). */
    private int copiaFoto(Path destinazioneFoto) throws IOException {
        Path sorgenteFoto = Path.of(cartellaDati, "foto");
        if (!Files.isDirectory(sorgenteFoto)) {
            return 0;
        }
        List<Path> file;
        try (Stream<Path> elenco = Files.list(sorgenteFoto)) {
            file = elenco.filter(Files::isRegularFile).toList();
        }
        if (file.isEmpty()) {
            return 0;
        }
        Files.createDirectories(destinazioneFoto);
        for (Path f : file) {
            Files.copy(f, destinazioneFoto.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        return file.size();
    }

    /**
     * Dopo una copia RIUSCITA, tiene solo le {@link #COPIE_DA_TENERE} sottocartelle piu' recenti
     * (docs/api.md): oltre quel numero, le piu' vecchie fra quelle create da QUESTO servizio si
     * cancellano. Riconosciute SOLO per nome ({@link #NOME_CARTELLA_COPIA}): qualunque altra cosa
     * l'utente tenga nella stessa cartella (un file, una sottocartella con un altro nome) resta
     * intoccata. Un fallimento nel cancellare una singola cartella si logga e basta - una copia
     * appena riuscita non deve mai sembrare fallita per colpa della pulizia di quelle vecchie.
     */
    private void pulisciCopieVecchie(Path cartella) {
        List<Path> sottocartelle;
        try (Stream<Path> elenco = Files.list(cartella)) {
            sottocartelle = elenco
                    .filter(Files::isDirectory)
                    .filter(p -> NOME_CARTELLA_COPIA.matcher(p.getFileName().toString()).matches())
                    .sorted(java.util.Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        } catch (IOException e) {
            log.warn("Impossibile elencare {} per la pulizia delle vecchie copie: {}", cartella, e.toString());
            return;
        }
        for (Path vecchia : sottocartelle.subList(Math.min(COPIE_DA_TENERE, sottocartelle.size()), sottocartelle.size())) {
            try {
                eliminaRicorsivamente(vecchia);
                log.info("Vecchia copia di sicurezza eliminata: {}", vecchia);
            } catch (IOException e) {
                log.warn("Impossibile eliminare la vecchia copia di sicurezza {}: {}", vecchia, e.toString());
            }
        }
    }

    private static void eliminaRicorsivamente(Path radice) throws IOException {
        try (Stream<Path> tutti = Files.walk(radice)) {
            for (Path p : tutti.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    private static long dimensioneCartella(Path cartella) throws IOException {
        try (Stream<Path> tutti = Files.walk(cartella)) {
            return tutti.filter(Files::isRegularFile).mapToLong(BackupService::dimensioneOZero).sum();
        }
    }

    private static long dimensioneOZero(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lettura/scrittura dello stato nelle impostazioni
    // ---------------------------------------------------------------------------------------

    private BackupStatoDto statoBackup() {
        String cartella = leggiValore(CHIAVE_CARTELLA);
        if (cartella == null) {
            return new BackupStatoDto(null, null, null, null);
        }
        BackupEsitoDto ultima = leggiEsito(PREFISSO_ULTIMA);
        BackupEsitoDto ultimaRiuscita = leggiEsito(PREFISSO_ULTIMA_RIUSCITA);
        String prossima = prossimoOrarioProgrammato(LocalDateTime.now(clock)).toString();
        return new BackupStatoDto(cartella, ultima, ultimaRiuscita, prossima);
    }

    private BackupEsitoDto leggiEsito(String prefisso) {
        String quando = leggiValore(prefisso + ".quando");
        if (quando == null) {
            return null; // mai registrato niente sotto questo prefisso (mai un tentativo, o mai uno riuscito)
        }
        String dimensione = leggiValore(prefisso + ".dimensioneByte");
        String foto = leggiValore(prefisso + ".foto");
        return new BackupEsitoDto(quando, dimensione != null ? Long.valueOf(dimensione) : null,
                foto != null ? Integer.valueOf(foto) : null, leggiValore(prefisso + ".esito"), leggiValore(prefisso + ".errore"));
    }

    private LocalDateTime leggiUltimoTentativo() {
        String quando = leggiValore(PREFISSO_ULTIMA + ".quando");
        return quando != null ? LocalDateTime.parse(quando) : null;
    }

    /**
     * {@code ultima} si aggiorna sempre (e' l'ultimo TENTATIVO); {@code ultimaRiuscita} SOLO
     * quando questo tentativo e' andato a buon fine, cosi' un fallimento non cancella mai la
     * memoria dell'ultima copia buona (docs/api.md, "Copie ravvicinate e ultima copia buona").
     */
    private void registraEsito(BackupEsitoDto esito) {
        salvaEsito(PREFISSO_ULTIMA, esito);
        if (ESITO_RIUSCITA.equals(esito.esito())) {
            salvaEsito(PREFISSO_ULTIMA_RIUSCITA, esito);
        }
    }

    private void salvaEsito(String prefisso, BackupEsitoDto esito) {
        salva(prefisso + ".quando", esito.quando());
        salva(prefisso + ".dimensioneByte", esito.dimensioneByte() != null ? String.valueOf(esito.dimensioneByte()) : null);
        salva(prefisso + ".foto", esito.foto() != null ? String.valueOf(esito.foto()) : null);
        salva(prefisso + ".esito", esito.esito());
        salva(prefisso + ".errore", esito.errore());
    }

    private String leggiValore(String chiave) {
        return impostazioni.findById(chiave).map(Impostazione::getValore).orElse(null);
    }

    /** {@code valore} e' NOT NULL a livello di colonna (v1-schema.yaml): "non impostato" e' l'assenza della riga, mai un valore null. */
    private void salva(String chiave, String valore) {
        if (valore == null) {
            impostazioni.deleteById(chiave);
        } else {
            impostazioni.save(new Impostazione(chiave, valore));
        }
    }

    private void validaCartella(Path cartella) {
        if (!Files.exists(cartella)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "cartella: non esiste");
        }
        if (!Files.isDirectory(cartella) || !Files.isWritable(cartella)) {
            throw new ErroreApi(HttpStatus.BAD_REQUEST, "cartella: non e' scrivibile");
        }
    }

    private String cartellaDatiAssoluta() {
        return Path.of(cartellaDati).toAbsolutePath().normalize().toString();
    }

    /** Stessa lettura di {@code VersioneController} (manifest del jar, impostato da spring-boot-maven-plugin al repackage). */
    private static String leggiVersione() {
        String v = BackupService.class.getPackage().getImplementationVersion();
        return v != null ? v : VERSIONE_DI_RIPIEGO;
    }
}

package it.etichette.programma;

import it.etichette.api.BackupEsitoDto;
import it.etichette.api.ErroreApi;
import it.etichette.api.ProgrammaDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BackupService} - copie di sicurezza (docs/api.md, "Il programma: versione, cartella dei
 * dati, copie di sicurezza"; "Copie ravvicinate e ultima copia buona").
 *
 * <p>Niente {@code @Transactional} qui: {@link BackupService#eseguiSubito()} apre una connessione
 * JDBC diretta ({@code VACUUM INTO}) fuori dal contesto transazionale di Spring, e con
 * {@code maximum-pool-size=1} (application.yml) una transazione di test che tiene occupata
 * l'unica connessione del pool farebbe restare quella chiamata in attesa fino al timeout. Ogni
 * test che tocca lo stato ripristina {@code cartella = null} da solo (in un {@code finally} dove
 * serve): {@link BackupService#stato()} nasconde comunque {@code ultima}/{@code ultimaRiuscita}/
 * {@code prossima} finche' manca una cartella, quindi lo stato osservabile resta pulito anche se
 * l'ordine dei test cambia.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackupServiceTest {

    private static Path cartellaDati;

    @DynamicPropertySource
    static void proprieta(DynamicPropertyRegistry registry) throws IOException {
        cartellaDati = Files.createTempDirectory("etichette-test-programma-");
        registry.add("etichette.dati", () -> cartellaDati.toString());
    }

    @Autowired
    private BackupService service;

    @Test
    void senzaCartellaLoStatoNonInventaNulla() {
        service.impostaCartella(null);

        ProgrammaDto stato = service.stato();

        assertNull(stato.backup().cartella());
        assertNull(stato.backup().ultima());
        assertNull(stato.backup().ultimaRiuscita());
        assertNull(stato.backup().prossima());
    }

    @Test
    void unaCartellaInesistenteRispondeNonEsiste() {
        Path inesistente = cartellaDati.resolve("non-esiste-" + System.nanoTime());

        ErroreApi errore = assertThrows(ErroreApi.class, () -> service.impostaCartella(inesistente.toString()));

        assertEquals(HttpStatus.BAD_REQUEST, errore.getStato());
        assertEquals("cartella: non esiste", errore.getMessage());
    }

    @Test
    void unFileAlPostoDiUnaCartellaRispondeNonScrivibile() throws IOException {
        Path file = Files.createTempFile("non-una-cartella-", ".txt");

        ErroreApi errore = assertThrows(ErroreApi.class, () -> service.impostaCartella(file.toString()));

        assertEquals(HttpStatus.BAD_REQUEST, errore.getStato());
        assertEquals("cartella: non e' scrivibile", errore.getMessage());
    }

    @Test
    void unaCartellaValidaSiSalvaESiRileggeConGet() throws IOException {
        Path destinazione = Files.createTempDirectory("backup-dest-");
        try {
            ProgrammaDto stato = service.impostaCartella(destinazione.toString());

            assertEquals(destinazione.toString(), stato.backup().cartella());
            assertEquals(destinazione.toString(), service.stato().backup().cartella());
        } finally {
            service.impostaCartella(null);
        }
    }

    @Test
    void senzaCartellaLaCopiaACuomandoRispondeConflitto() {
        service.impostaCartella(null);

        ErroreApi errore = assertThrows(ErroreApi.class, () -> service.eseguiSubito());

        assertEquals(HttpStatus.CONFLICT, errore.getStato());
    }

    @Test
    void unaCopiaGiaInCorsoRispondeConflitto() throws IOException {
        Path destinazione = Files.createTempDirectory("backup-dest-");
        service.impostaCartella(destinazione.toString());
        service.simulaCopiaInCorsoPerTest();
        try {
            ErroreApi errore = assertThrows(ErroreApi.class, () -> service.eseguiSubito());
            assertEquals(HttpStatus.CONFLICT, errore.getStato());
        } finally {
            service.terminaCopiaInCorsoPerTest();
            service.impostaCartella(null);
        }
    }

    @Test
    void laCopiaACuomandoCopiaIlDatabaseLeFotoERegistraLoStato() throws Exception {
        Path cartellaFoto = cartellaDati.resolve("foto");
        Files.createDirectories(cartellaFoto);
        Files.writeString(cartellaFoto.resolve("1.jpg"), "finta-foto-1");
        Files.writeString(cartellaFoto.resolve("2.jpg"), "finta-foto-2");

        Path destinazione = Files.createTempDirectory("backup-dest-");
        service.impostaCartella(destinazione.toString());
        try {
            BackupEsitoDto esito = service.eseguiSubito();

            assertEquals("riuscita", esito.esito());
            assertNull(esito.errore());
            assertEquals(2, esito.foto());
            assertTrue(esito.dimensioneByte() > 0);

            Path sottocartella = unicaSottocartella(destinazione);

            Path dbCopiato = sottocartella.resolve("etichette.db");
            assertTrue(Files.isRegularFile(dbCopiato));
            // riapribile e leggibile con una connessione SQLite vera, non solo "il file esiste"
            try (Connection connessione = DriverManager.getConnection("jdbc:sqlite:" + dbCopiato);
                    Statement s = connessione.createStatement();
                    ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM impostazioni")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 0);
            }

            try (Stream<Path> fotoCopiate = Files.list(sottocartella.resolve("foto"))) {
                assertEquals(2, fotoCopiate.count());
            }

            // sopravvive a una rilettura (non e' solo il valore di ritorno di eseguiSubito)
            ProgrammaDto stato = service.stato();
            assertEquals("riuscita", stato.backup().ultima().esito());
            assertEquals(2, stato.backup().ultima().foto());
            assertEquals(esito.dimensioneByte(), stato.backup().ultima().dimensioneByte());
            // una copia riuscita e' anche "l'ultima copia buona"
            assertEquals("riuscita", stato.backup().ultimaRiuscita().esito());
            assertEquals(esito.quando(), stato.backup().ultimaRiuscita().quando());
        } finally {
            service.impostaCartella(null);
        }
    }

    /**
     * Difetto trovato sul campo (docs/api.md, "Copie ravvicinate e ultima copia buona"): un
     * doppio clic su "Fai una copia adesso" cadeva nello stesso minuto, la sottocartella esisteva
     * gia' e la seconda copia falliva - pur con il database perfettamente sano. Orologio fisso
     * apposta: cosi' le DUE chiamate cadono esattamente sullo stesso secondo, il caso che prima
     * falliva sempre.
     */
    @Test
    void dueCopieRavvicinateNelloStessoSecondoProduconoDueCartelleDistinte() throws IOException {
        Path destinazione = Files.createTempDirectory("backup-dest-ravvicinate-");
        service.impostaCartella(destinazione.toString());
        service.impostaClockPerTest(fisso(LocalDateTime.of(2026, 9, 22, 17, 45, 3)));
        try {
            BackupEsitoDto prima = service.eseguiSubito();
            BackupEsitoDto seconda = service.eseguiSubito();

            assertEquals("riuscita", prima.esito());
            assertEquals("riuscita", seconda.esito());

            List<Path> sottocartelle;
            try (Stream<Path> elenco = Files.list(destinazione)) {
                sottocartelle = elenco.filter(Files::isDirectory).toList();
            }
            assertEquals(2, sottocartelle.size(), "due copie riuscite devono finire in due cartelle distinte, non una sopra l'altra");
        } finally {
            service.impostaClockPerTest(Clock.systemDefaultZone());
            service.impostaCartella(null);
        }
    }

    /**
     * Stesso difetto sul campo: un tentativo fallito NON deve far sparire la memoria dell'ultima
     * copia buona (prima il fallimento sovrascriveva anche quella, e la schermata diceva "copia
     * fallita" su dati in realta' gia' al sicuro).
     */
    @Test
    void unTentativoFallitoNonCancellaLultimaCopiaRiuscita() throws IOException {
        Path destinazioneBuona = Files.createTempDirectory("backup-buona-");
        service.impostaCartella(destinazioneBuona.toString());
        BackupEsitoDto buona = service.eseguiSubito();
        assertEquals("riuscita", buona.esito());

        // Una cartella valida al momento del PUT (Files.createTempDirectory), diventata un FILE
        // prima della copia: Files.createDirectories non puo' creare una sottocartella dove un
        // componente del percorso e' gia' un file - fallimento vero e deterministico, senza
        // toccare permessi di filesystem (fragili su Windows).
        Path destinazioneRotta = Files.createTempDirectory("backup-rotta-");
        service.impostaCartella(destinazioneRotta.toString());
        Files.delete(destinazioneRotta);
        Files.writeString(destinazioneRotta, "non e' piu' una cartella");

        try {
            BackupEsitoDto fallita = service.eseguiSubito();

            assertEquals("fallita", fallita.esito());
            assertNull(fallita.dimensioneByte());
            assertNull(fallita.foto());
            assertTrue(fallita.errore() != null && !fallita.errore().isBlank());
            // niente messaggio grezzo del driver/di Java: solo la frase italiana tradotta
            assertFalse(fallita.errore().toLowerCase().contains("exception"));
            assertFalse(fallita.errore().toLowerCase().contains("sqlite"));

            // registrato, e non ha fatto cadere il servizio: si rilegge dallo stato
            ProgrammaDto stato = service.stato();
            assertEquals("fallita", stato.backup().ultima().esito());
            assertEquals(fallita.errore(), stato.backup().ultima().errore());

            // ma l'ultima copia BUONA di prima resta - non e' stata cancellata dal fallimento
            assertEquals("riuscita", stato.backup().ultimaRiuscita().esito());
            assertEquals(buona.quando(), stato.backup().ultimaRiuscita().quando());
            assertEquals(buona.dimensioneByte(), stato.backup().ultimaRiuscita().dimensioneByte());
        } finally {
            Files.deleteIfExists(destinazioneRotta);
            service.impostaCartella(null);
        }
    }

    /**
     * B9 (revisione del 23/09/2026): senza un limite, la cartella scelta cresceva senza fine, una
     * sottocartella a notte per sempre. Dodici sottocartelle "della copia" pre-esistenti (nomi
     * crescenti, dal piu' vecchio 2020-01-01 al piu' recente 2020-01-12) piu' una cartella e un file
     * ESTRANEI (roba dell'utente nella stessa cartella): dopo UNA copia riuscita (orologio fisso su
     * una data futura, cosi' la nuova sottocartella e' di sicuro la piu' recente) devono restare
     * solo le 10 piu' recenti fra quelle "della copia" (le tre piu' vecchie sparite, la nuova
     * inclusa), e la roba estranea deve restare esattamente com'era.
     */
    @Test
    void dopoUnaCopiaRiuscitaRestanoSoloLeDieciPiuRecentiEnonSiToccaAltro() throws IOException {
        Path destinazione = Files.createTempDirectory("backup-pulizia-");
        for (int giorno = 1; giorno <= 12; giorno++) {
            Path vecchia = destinazione.resolve(String.format("2020-01-%02d_000000", giorno));
            Files.createDirectories(vecchia);
            Files.writeString(vecchia.resolve("etichette.db"), "finto database " + giorno);
        }
        Path cartellaEstranea = destinazione.resolve("Documenti importanti");
        Files.createDirectories(cartellaEstranea);
        Files.writeString(cartellaEstranea.resolve("nota.txt"), "non e' una copia di sicurezza");
        Path fileEstraneo = destinazione.resolve("leggimi.txt");
        Files.writeString(fileEstraneo, "non toccare");

        service.impostaCartella(destinazione.toString());
        service.impostaClockPerTest(fisso(LocalDateTime.of(2030, 1, 1, 3, 0)));
        try {
            BackupEsitoDto esito = service.eseguiSubito();
            assertEquals("riuscita", esito.esito());

            List<Path> tutte;
            try (Stream<Path> elenco = Files.list(destinazione)) {
                tutte = elenco.toList();
            }
            List<String> cartelleDiCopia = tutte.stream()
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(nome -> nome.matches("\\d{4}-\\d{2}-\\d{2}_\\d{6}(-\\d+)?"))
                    .sorted()
                    .toList();
            assertEquals(10, cartelleDiCopia.size());
            assertFalse(cartelleDiCopia.contains("2020-01-01_000000"));
            assertFalse(cartelleDiCopia.contains("2020-01-02_000000"));
            assertFalse(cartelleDiCopia.contains("2020-01-03_000000"));
            assertTrue(cartelleDiCopia.contains("2020-01-04_000000"));
            assertTrue(cartelleDiCopia.contains("2020-01-12_000000"));

            assertTrue(Files.isDirectory(cartellaEstranea), "una cartella estranea non deve mai essere toccata");
            assertTrue(Files.exists(cartellaEstranea.resolve("nota.txt")));
            assertTrue(Files.exists(fileEstraneo), "un file estraneo non deve mai essere toccato");
        } finally {
            service.impostaClockPerTest(Clock.systemDefaultZone());
            service.impostaCartella(null);
        }
    }

    // ---------------------------------------------------------------------------------------
    // La traduzione in italiano degli errori (docs/api.md: "non il messaggio grezzo del driver")
    // - metodo statico apposta, testabile senza dover forzare per davvero ogni tipo di guasto.
    // ---------------------------------------------------------------------------------------

    @Test
    void traducePermessiInsufficienti() {
        String messaggio = BackupService.messaggioErrore(new AccessDeniedException("D:\\Backup"));
        assertFalse(messaggio.contains("AccessDeniedException"));
        assertTrue(messaggio.toLowerCase().contains("permess"));
    }

    @Test
    void traduceCartellaSparita() {
        String messaggio = BackupService.messaggioErrore(new NoSuchFileException("D:\\Backup"));
        assertFalse(messaggio.contains("NoSuchFileException"));
        assertTrue(messaggio.toLowerCase().contains("non esiste"));
    }

    @Test
    void traduceFileGiaEsistente() {
        String messaggio = BackupService.messaggioErrore(new FileAlreadyExistsException("etichette.db"));
        assertFalse(messaggio.contains("FileAlreadyExistsException"));
        assertTrue(messaggio.toLowerCase().contains("esiste gia'"));
    }

    @Test
    void traduceErroreDelDatabaseSenzaIlMessaggioGrezzoDelDriver() {
        String messaggio = BackupService.messaggioErrore(
                new SQLException("[SQLITE_ERROR] SQL error or missing database (output file already exists)"));
        assertFalse(messaggio.contains("SQLITE_ERROR"));
        assertFalse(messaggio.contains("output file already exists"));
        assertTrue(messaggio.toLowerCase().contains("database"));
    }

    @Test
    void traduceUnErroreGenericoDiIO() {
        String messaggio = BackupService.messaggioErrore(new IOException("qualche errore di sistema in inglese"));
        assertFalse(messaggio.contains("qualche errore di sistema in inglese"));
    }

    @Test
    void laProssimaCopiaEOggiAlleTreSeNonSonoAncoraPassate() throws IOException {
        Path destinazione = Files.createTempDirectory("backup-dest-");
        service.impostaCartella(destinazione.toString());
        service.impostaClockPerTest(fisso(LocalDateTime.of(2026, 9, 22, 1, 0)));
        try {
            assertEquals("2026-09-22T03:00", service.stato().backup().prossima());
        } finally {
            service.impostaClockPerTest(Clock.systemDefaultZone());
            service.impostaCartella(null);
        }
    }

    @Test
    void laProssimaCopiaEDomaniAlleTreSeSonoGiaPassate() throws IOException {
        Path destinazione = Files.createTempDirectory("backup-dest-");
        service.impostaCartella(destinazione.toString());
        service.impostaClockPerTest(fisso(LocalDateTime.of(2026, 9, 22, 9, 0)));
        try {
            assertEquals("2026-09-23T03:00", service.stato().backup().prossima());
        } finally {
            service.impostaClockPerTest(Clock.systemDefaultZone());
            service.impostaCartella(null);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Il criterio di recupero (docs/api.md: "se il PC era spento, la copia si fa alla prima
    // occasione utile dopo l'avvio") - metodo statico apposta, testabile senza aspettare le 3.
    // ---------------------------------------------------------------------------------------

    @Test
    void recuperaSeIlGiroDelleTreEStatoPerso() {
        LocalDateTime ultimoTentativo = LocalDateTime.of(2026, 9, 21, 3, 0, 5);
        LocalDateTime adesso = LocalDateTime.of(2026, 9, 22, 9, 0); // il PC era spento alle 3 di oggi
        assertTrue(BackupService.backupDovuto(ultimoTentativo, adesso));
    }

    @Test
    void nonRifaUnaCopiaGiaFattaOggi() {
        LocalDateTime ultimoTentativo = LocalDateTime.of(2026, 9, 22, 3, 0, 5);
        LocalDateTime adesso = LocalDateTime.of(2026, 9, 22, 9, 0);
        assertFalse(BackupService.backupDovuto(ultimoTentativo, adesso));
    }

    @Test
    void eDovutaSeNonEMaiStataFattaUnaCopia() {
        assertTrue(BackupService.backupDovuto(null, LocalDateTime.of(2026, 9, 22, 9, 0)));
    }

    @Test
    void nonEAncoraDovutaPrimaDelleTreSeIeriNotteEGiaStataFatta() {
        LocalDateTime ultimoTentativo = LocalDateTime.of(2026, 9, 21, 3, 0, 5);
        LocalDateTime adesso = LocalDateTime.of(2026, 9, 22, 1, 0); // e' ancora "notte di ieri"
        assertFalse(BackupService.backupDovuto(ultimoTentativo, adesso));
    }

    private static Clock fisso(LocalDateTime momento) {
        return Clock.fixed(momento.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    }

    private static Path unicaSottocartella(Path destinazione) throws IOException {
        List<Path> sottocartelle;
        try (Stream<Path> elenco = Files.list(destinazione)) {
            sottocartelle = elenco.filter(Files::isDirectory).toList();
        }
        assertEquals(1, sottocartelle.size());
        return sottocartelle.get(0);
    }
}

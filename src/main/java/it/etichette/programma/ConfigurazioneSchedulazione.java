package it.etichette.programma;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Accende {@code @Scheduled} (non era ancora attivo nel progetto): serve alla copia di sicurezza
 * notturna delle 3, {@link BackupService#backupNotturno()}.
 */
@Configuration
@EnableScheduling
public class ConfigurazioneSchedulazione {
}

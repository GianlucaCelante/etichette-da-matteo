package it.etichette.sistema;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Condizione dei bean che esistono solo su Windows (canale usbprint via SetupAPI/Kernel32). */
public class SoloWindows implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return Sistema.isWindows();
    }
}

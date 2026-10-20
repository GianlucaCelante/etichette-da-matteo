package it.etichette.sistema;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Condizione dei bean che esistono solo su macOS (canale USB via libusb). */
public class SoloMacOs implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return Sistema.isMacOs();
    }
}

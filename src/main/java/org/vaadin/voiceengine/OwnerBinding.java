package org.vaadin.voiceengine;

import java.io.Serializable;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.shared.Registration;

/**
 * Keeps a UI-level registration alive only while the owner component is
 * attached, re-registering on re-attach.
 */
final class OwnerBinding implements Registration, Serializable {

    private final SerializableFunction<UI, Registration> register;
    private final Registration attachRegistration;
    private final Registration detachRegistration;
    private Registration active;

    OwnerBinding(Component owner, SerializableFunction<UI, Registration> register) {
        Objects.requireNonNull(owner, "owner must not be null");
        this.register = register;
        attachRegistration = owner.addAttachListener(e -> activate(e.getUI()));
        detachRegistration = owner.addDetachListener(e -> deactivate());
        owner.getUI().ifPresent(this::activate);
    }

    private void activate(UI ui) {
        deactivate();
        active = register.apply(ui);
    }

    private void deactivate() {
        if (active != null) {
            active.remove();
            active = null;
        }
    }

    @Override
    public void remove() {
        deactivate();
        attachRegistration.remove();
        detachRegistration.remove();
    }
}

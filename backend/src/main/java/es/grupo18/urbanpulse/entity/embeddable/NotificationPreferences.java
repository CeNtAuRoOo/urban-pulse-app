package es.grupo18.urbanpulse.entity.embeddable;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Representa las preferencias de notificación de un usuario.
 * Se almacena como parte de la entidad User y permite habilitar
 * o deshabilitar las notificaciones en la aplicación, por correo y push.
 */
@Embeddable
@Getter
public class NotificationPreferences {

    @Column(name = "notify_in_app", nullable = false)
    private boolean inApp = true;

    @Column(name = "notify_email", nullable = false)
    private boolean email = true;

    @Column(name = "notify_push", nullable = false)
    private boolean push = true;
}

package es.grupo18.urbanpulse.service.mail;

/**
 * Define el contrato de envío de correos electrónicos de la aplicación.
 * Permite desacoplar la lógica de negocio del mecanismo de transporte
 * utilizado para entregar los mensajes.
 */
public interface EmailSender {

    /**
     * Envía un mensaje de correo electrónico utilizando
     * la implementación de transporte configurada.
     *
     * @param message mensaje de correo electrónico que se desea enviar
     */
    void send(EmailMessage message);
}

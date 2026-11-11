package es.grupo18.urbanpulse.auth.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import es.grupo18.urbanpulse.auth.config.EmailVerificationProperties;

/**
 * Limpieza limitada por lotes y número de transacciones por barrido.
 *
 * Las cuentas con referencias históricas desde pertenencias, invitaciones o
 * auditoría se conservan íntegramente y se excluyen antes de limitar el lote.
 * Se revalida la ausencia de referencias bajo bloqueo de usuario; no se borra
 * historia ni se añade CASCADE. Los códigos caducados se limpian por separado.
 *
 * Retención: 24 h sin verificar; barrido horario, sin garantía exacta antes de
 * 25 h si hay bloqueos, fallos o más filas que el presupuesto de un barrido.
 */
@Component
@Slf4j
public class EmailVerificationCleanup {

    /**
     * Predicado compartido por selección y revisión bajo bloqueo; incluye todas las
     * FK históricas.
     */
    private static final String WITHOUT_HISTORY = """
            not exists (select 1 from department_memberships m where m.user_id = u.id)
            and not exists (select 1 from staff_invitations i where i.created_by_user_id = u.id
                or i.redeemed_by_user_id = u.id or i.revoked_by_user_id = u.id)
            and not exists (select 1 from audit_events a where a.actor_user_id = u.id)
            and not exists (select 1 from incidents i where i.reporter_user_id = u.id)
            and not exists (select 1 from attachments a where a.uploaded_by = u.id)
            and not exists (select 1 from status_changes s where s.changed_by_user_id = u.id)
            and not exists (select 1 from incident_reviews r where r.reviewed_by_user_id = u.id)
            and not exists (select 1 from priority_decisions p where p.decided_by_user_id = u.id)
            """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EmailVerificationProperties properties;
    private final Clock clock;

    /**
     * Inicializa el componente de limpieza con acceso SQL, gestión
     * transaccional, configuración de los lotes y un reloj inyectable.
     *
     * @param jdbc       componente de acceso a PostgreSQL mediante SQL
     * @param manager    gestor de transacciones de la aplicación
     * @param properties configuración de tamaño y número máximo de lotes
     * @param clock      reloj utilizado para calcular las fechas de caducidad
     */
    public EmailVerificationCleanup(JdbcTemplate jdbc, PlatformTransactionManager manager,
            EmailVerificationProperties properties, Clock clock) {

        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Ejecuta periódicamente la limpieza de códigos caducados
     * y cuentas pendientes de verificación que han superado las 24 horas.
     * Registra el número de eliminaciones y captura los fallos
     * sin revelar información sensible.
     */
    @Scheduled(cron = "${security.email-verification.cleanup-cron:0 0 * * * *}")
    public void scheduledCleanup() {
        try {
            var result = clean();

            if (result.codes() + result.users() > 0) {
                log.info("event=VERIFICATION_CLEANUP codes={} users={}", result.codes(), result.users());
            }
        }

        catch (RuntimeException ex) {
            log.error("event=VERIFICATION_CLEANUP_FAILED exceptionType={}", ex.getClass().getName());
        }
    }

    /**
     * Ejecuta la limpieza de solicitudes de verificación caducadas
     * y usuarios no verificados que han superado su tiempo de retención.
     * Procesa los registros mediante transacciones y lotes acotados,
     * evitando cargar grandes conjuntos de datos en memoria.
     *
     * @return resultado con el número de códigos y usuarios eliminados
     */
    public Result clean() {
        Instant instant = clock.instant();
        Timestamp now = Timestamp.from(instant);
        Timestamp cutoff = Timestamp.from(instant.minus(EmailVerificationService.ACCOUNT_LIFETIME));
        int codes = 0;
        int users = 0;

        for (int i = 0; i < properties.cleanupMaxBatches(); i++) {
            int deleted = transactions.execute(status -> jdbc.update("""
                    delete from email_verifications where user_id in (
                        select user_id from email_verifications where expires_at <= ?
                        order by expires_at, user_id limit ? for update skip locked)
                    """, now, properties.cleanupBatchSize()));

            codes += deleted;

            if (deleted < properties.cleanupBatchSize()) {
                break;
            }
        }

        for (int i = 0; i < properties.cleanupMaxBatches(); i++) {
            int deleted = transactions.execute(status -> deleteUserBatch(cutoff));
            users += deleted;

            if (deleted < properties.cleanupBatchSize()) {
                break;
            }
        }

        return new Result(codes, users);
    }

    /**
     * Elimina un lote de cuentas no verificadas anteriores a la fecha límite.
     * Selecciona los usuarios con bloqueo pesimista y SKIP LOCKED,
     * comprueba nuevamente su estado y elimina sus solicitudes
     * de verificación y roles antes de borrar las cuentas.
     *
     * @param cutoff fecha límite para considerar caducada una cuenta
     * @return número de usuarios eliminados durante el lote
     */
    private int deleteUserBatch(Timestamp cutoff) {
        var ids = jdbc.query("""
                select u.id from users u where u.email_verified = false and u.created_at <= ? and
                """ + WITHOUT_HISTORY + " order by u.created_at, u.id limit ? for update of u skip locked",
                (rs, row) -> rs.getObject(1, UUID.class), cutoff, properties.cleanupBatchSize());

        int deleted = 0;

        for (UUID id : ids) {
            // Revisión explícita bajo el mismo bloqueo que usa la confirmación.
            boolean expired = Boolean.TRUE.equals(jdbc.queryForObject("""
                    select not u.email_verified and u.created_at <= ? and
                    """ + WITHOUT_HISTORY + " from users u where u.id = ?", Boolean.class, cutoff, id));

            if (!expired) {
                continue;
            }

            jdbc.update("delete from email_verifications where user_id = ?", id);
            jdbc.update("delete from user_roles where user_id = ?", id);

            deleted += jdbc.update("delete from users where id = ? and email_verified = false and created_at <= ?",
                    id, cutoff);
        }

        return deleted;
    }

    /**
     * Representa el resultado de una ejecución de limpieza
     * de solicitudes de verificación y cuentas caducadas.
     *
     * @param codes número de solicitudes de verificación eliminadas
     * @param users número de cuentas de usuario eliminadas
     */
    public record Result(
            int codes,
            int users) {
    }
}

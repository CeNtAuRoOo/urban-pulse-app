package es.grupo18.urbanpulse.exception;

import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import static org.assertj.core.api.Assertions.*;

class AuthenticationBackendExceptionTest {

    @Test
    void recognizesTypedResourceFailuresAndWrappedPostgresConnectionStates() {
        for (Throwable failure : List.of(
                new DataAccessResourceFailureException("private"),
                new TransientDataAccessResourceException("private"),
                new RecoverableDataAccessException("private"),
                new QueryTimeoutException("private"),
                new SQLTransientConnectionException("private"),
                new SQLNonTransientConnectionException("private"),
                new SQLRecoverableException("private"),
                new SQLTimeoutException("private"))) {
            assertThat(AuthenticationBackendException.isDatabaseUnavailable(failure))
                    .as(failure.getClass().getSimpleName()).isTrue();
            assertThat(AuthenticationBackendException.isDatabaseUnavailable(
                    new CannotCreateTransactionException("private transaction", failure))).isTrue();
        }
        for (String sqlState : List.of("08001", "08006", "53300", "57P01", "57P02", "57P03")) {
            var failure = new TransactionSystemException("private transaction",
                    new SQLException("private SQL", sqlState));
            var sanitized = new AuthenticationBackendException(failure);
            assertThat(sanitized.isUnavailable()).as(sqlState).isTrue();
            assertThat(sanitized.failureType()).isEqualTo(TransactionSystemException.class.getName());
            assertThat(sanitized).hasNoCause().hasMessage("Fallo del backend de autenticación.");
        }
    }

    @Test
    void doesNotClassifySqlBugsIntegrityErrorsOrUnknownTransactionsAsOutages() {
        for (Throwable failure : List.of(
                new BadSqlGrammarException("private", "private", new SQLException("private", "42601")),
                new DataIntegrityViolationException("private", new SQLException("private", "23505")),
                new InvalidDataAccessApiUsageException("private"),
                new CannotCreateTransactionException("connection unavailable"),
                new IllegalStateException("connection refused"),
                new SQLException("connection refused"),
                new SQLException("private", "40001"),
                new SQLException("private", "40P01"))) {
            assertThat(AuthenticationBackendException.isDatabaseUnavailable(failure))
                    .as(failure.getClass().getSimpleName()).isFalse();
        }
    }
}

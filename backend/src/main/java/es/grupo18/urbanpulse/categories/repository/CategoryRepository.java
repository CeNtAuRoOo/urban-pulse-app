package es.grupo18.urbanpulse.categories.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import es.grupo18.urbanpulse.categories.model.Category;

/**
 * Acceso al catálogo con unicidad según la collation de PostgreSQL y bloqueo de
 * escritura.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Bloquea la fila hasta finalizar la transacción; requiere una transacción de
     * escritura.
     *
     * @param id identidad del recurso
     * @return entidad bloqueada o vacío si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findForUpdate(UUID id);

    /**
     * Lista todas las filas, incluidas las inactivas.
     *
     * @return catálogo por nombre según collation PostgreSQL y UUID como desempate
     */
    List<Category> findAllByOrderByNameAscIdAsc();

    /**
     * Comprueba unicidad con el mismo lower que el índice SQL, incluidas filas
     * inactivas.
     *
     * @param name nombre normalizado
     * @return true si alguna categoría reserva el nombre
     */
    @Query("select (count(c) > 0) from Category c where lower(c.name) = lower(:name)")
    boolean existsByNormalizedName(String name);

    /**
     * Comprueba unicidad excluyendo la propia identidad, incluidas categorías
     * inactivas.
     *
     * @param name nombre normalizado
     * @param id   identidad que se modifica
     * @return true si otra categoría reserva el nombre
     */
    @Query("select (count(c) > 0) from Category c where lower(c.name) = lower(:name) and c.id <> :id")
    boolean existsByNormalizedNameAndIdNot(String name, UUID id);
}

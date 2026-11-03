package es.grupo18.urbanpulse.repository;

import es.grupo18.urbanpulse.entity.Category;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findForUpdate(UUID id);

    List<Category> findAllByOrderByNameAscIdAsc();

    // Match PostgreSQL's lower(name) unique index, including its collation semantics.
    @Query("select (count(c) > 0) from Category c where lower(c.name) = lower(:name)")
    boolean existsByNormalizedName(String name);

    @Query("select (count(c) > 0) from Category c where lower(c.name) = lower(:name) and c.id <> :id")
    boolean existsByNormalizedNameAndIdNot(String name, UUID id);
}

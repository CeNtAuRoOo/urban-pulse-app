package es.grupo18.urbanpulse.attachment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import es.grupo18.urbanpulse.attachment.model.Attachment;
import es.grupo18.urbanpulse.identity.model.User;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Retrieves all attachments uploaded by a specific user.
     */
    List<Attachment> findAllByUser(User user);

    /**
     * Retrieves an attachment using its storage key.
     */
    Optional<Attachment> findByStorageKey(String storageKey);

    /**
     * Retrieves all attachments that have not been deleted.
     */
    List<Attachment> findAllByDeletedAtIsNull();

    /**
     * Retrieves an attachment by ID if it has not been deleted.
     */
    Optional<Attachment> findByIdAndDeletedAtIsNull(UUID id);
}

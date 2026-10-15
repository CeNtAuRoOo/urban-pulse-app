
package es.grupo18.urbanpulse;

import static org.junit.jupiter.api.Assertions.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import es.grupo18.urbanpulse.config.StorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class NeonConnectivityIT {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private StorageProperties storageProperties;

    @Test
    void postgresConnectionWorks() throws Exception {

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT current_database(), current_user");
                ResultSet result = statement.executeQuery()) {

            assertTrue(result.next());

            assertEquals("urban-pulse", result.getString(1));
            assertEquals("backend", result.getString(2));
        }
    }

    @Test
    void objectStorageIsAccessible() {

        HeadBucketRequest request = HeadBucketRequest.builder()
                .bucket(storageProperties.bucket())
                .build();

        assertDoesNotThrow(() -> s3Client.headBucket(request));
    }
}

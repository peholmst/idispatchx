package net.pkhapps.idispatchx.gis.server.api.health;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import net.pkhapps.idispatchx.gis.server.model.TileLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private Context ctx;

    @TempDir
    private Path tempDir;

    // ==================== checkDatabase ====================

    @Test
    void checkDatabase_connectionSucceeds_returnsUp() throws Exception {
        setupDbSuccess();
        var controller = createController(tempDir, Map.of());

        var result = controller.checkDatabase();

        assertEquals("UP", result.get("status"));
        assertFalse(result.containsKey("error"));
    }

    @Test
    void checkDatabase_connectionFails_returnsDown() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        var controller = createController(tempDir, Map.of());

        var result = controller.checkDatabase();

        assertEquals("DOWN", result.get("status"));
        assertEquals("Connection refused", result.get("error"));
    }

    // ==================== checkTileDirectory ====================

    @Test
    void checkTileDirectory_directoryExistsAndReadable_returnsUpWithLayers() {
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10, 11, 12)));
        var controller = createController(tempDir, layers);

        var result = controller.checkTileDirectory();

        assertEquals("UP", result.get("status"));
        @SuppressWarnings("unchecked")
        var layerList = (List<String>) result.get("layers");
        assertTrue(layerList.contains("terrain"));
    }

    @Test
    void checkTileDirectory_directoryDoesNotExist_returnsDown() {
        var nonExistent = tempDir.resolve("does-not-exist");
        var controller = createController(nonExistent, Map.of());

        var result = controller.checkTileDirectory();

        assertEquals("DOWN", result.get("status"));
        assertEquals("Tile directory does not exist", result.get("error"));
    }

    @Test
    void checkTileDirectory_pathIsFile_returnsDown() throws Exception {
        var filePath = tempDir.resolve("not-a-dir.txt");
        Files.writeString(filePath, "content");
        var controller = createController(filePath, Map.of());

        var result = controller.checkTileDirectory();

        assertEquals("DOWN", result.get("status"));
        assertEquals("Tile path is not a directory", result.get("error"));
    }

    // ==================== handleHealthCheck ====================

    @Test
    @SuppressWarnings("unchecked")
    void handleHealthCheck_allHealthy_returns200WithStatusUp() throws Exception {
        setupDbSuccess();
        var layers = Map.of("terrain", new TileLayer("terrain", Set.of(10)));
        var controller = createController(tempDir, layers);

        invokeHandleHealthCheck(controller);

        verify(ctx).status(HttpStatus.OK);
        var response = captureJsonResponse();
        assertEquals("UP", response.get("status"));

        var components = (Map<String, Object>) response.get("components");
        var dbComponent = (Map<String, Object>) components.get("database");
        assertEquals("UP", dbComponent.get("status"));

        var tileComponent = (Map<String, Object>) components.get("tileDirectory");
        assertEquals("UP", tileComponent.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleHealthCheck_databaseDown_returns503WithStatusDown() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        var controller = createController(tempDir, Map.of());

        invokeHandleHealthCheck(controller);

        verify(ctx).status(HttpStatus.SERVICE_UNAVAILABLE);
        var response = captureJsonResponse();
        assertEquals("DOWN", response.get("status"));

        var components = (Map<String, Object>) response.get("components");
        var dbComponent = (Map<String, Object>) components.get("database");
        assertEquals("DOWN", dbComponent.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleHealthCheck_tileDirectoryMissing_returns503() throws Exception {
        setupDbSuccess();
        var controller = createController(tempDir.resolve("nonexistent"), Map.of());

        invokeHandleHealthCheck(controller);

        verify(ctx).status(HttpStatus.SERVICE_UNAVAILABLE);
        var response = captureJsonResponse();
        assertEquals("DOWN", response.get("status"));
    }

    @Test
    void handleHealthCheck_noLayersDiscovered_returns200WhenDirectoryAccessible() throws Exception {
        setupDbSuccess();
        var controller = createController(tempDir, Map.of());

        invokeHandleHealthCheck(controller);

        verify(ctx).status(HttpStatus.OK);
    }

    // ==================== constructor validation ====================

    @Test
    void constructor_nullDataSource_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new HealthController(null, tempDir, Map.of()));
    }

    @Test
    void constructor_nullTileDirectory_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new HealthController(dataSource, null, Map.of()));
    }

    @Test
    void constructor_nullLayers_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new HealthController(dataSource, tempDir, null));
    }

    // ==================== helpers ====================

    private void setupDbSuccess() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
    }

    private HealthController createController(Path tileDir, Map<String, TileLayer> layers) {
        return new HealthController(dataSource, tileDir, layers);
    }

    private void invokeHandleHealthCheck(HealthController controller) throws Exception {
        var method = HealthController.class.getDeclaredMethod("handleHealthCheck", Context.class);
        method.setAccessible(true);
        try {
            method.invoke(controller, ctx);
        } catch (java.lang.reflect.InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureJsonResponse() {
        var captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(ctx).json(captor.capture());
        return captor.getValue();
    }
}

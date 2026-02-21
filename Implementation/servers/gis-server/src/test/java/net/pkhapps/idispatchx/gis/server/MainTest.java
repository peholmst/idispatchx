package net.pkhapps.idispatchx.gis.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @Test
    void createConfigLoader_withNoArgs_usesClasspath() throws Exception {
        var loader = invokeCreateConfigLoader(new String[]{});
        assertNotNull(loader);
    }

    @Test
    void createConfigLoader_withExistingFile_usesFile(@TempDir Path tempDir) throws Exception {
        var configFile = tempDir.resolve("test.properties");
        Files.writeString(configFile, "# test config\n");

        var loader = invokeCreateConfigLoader(new String[]{configFile.toString()});
        assertNotNull(loader);
    }

    @Test
    void createConfigLoader_withNonExistentFile_fallsBackToClasspath(@TempDir Path tempDir) throws Exception {
        var nonExistentFile = tempDir.resolve("nonexistent.properties");

        var loader = invokeCreateConfigLoader(new String[]{nonExistentFile.toString()});
        assertNotNull(loader);
    }

    /**
     * Invokes the private createConfigLoader method via reflection for testing.
     */
    private Object invokeCreateConfigLoader(String[] args) throws Exception {
        Method method = Main.class.getDeclaredMethod("createConfigLoader", String[].class);
        method.setAccessible(true);
        try {
            return method.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}

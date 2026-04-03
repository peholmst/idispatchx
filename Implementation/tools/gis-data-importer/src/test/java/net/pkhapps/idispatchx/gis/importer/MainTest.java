package net.pkhapps.idispatchx.gis.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void readPasswordFromFile_validFile_returnsStrippedContent() throws IOException {
        var file = tempDir.resolve("password.txt");
        Files.writeString(file, "secret\n");
        assertEquals("secret", Main.readPasswordFromFile(file));
    }

    @Test
    void readPasswordFromFile_fileWithWhitespace_returnsStripped() throws IOException {
        var file = tempDir.resolve("password.txt");
        Files.writeString(file, "  secret  \n");
        assertEquals("secret", Main.readPasswordFromFile(file));
    }

    @Test
    void readPasswordFromFile_nonExistentFile_throwsIOException() {
        var file = tempDir.resolve("nonexistent.txt");
        assertThrows(IOException.class, () -> Main.readPasswordFromFile(file));
    }
}

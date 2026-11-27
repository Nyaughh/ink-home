package dev.inkhome;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BookStorageTest {
    @Test public void acceptsBothVolumesAndRejectsEscapes() throws IOException {
        List<File> roots = Arrays.asList(new File("primary"), new File("card"));
        assertTrue(BookStorage.contains(roots, new File("primary/book.epub")));
        assertTrue(BookStorage.contains(roots, new File("card/Books/book.pdf")));
        assertFalse(BookStorage.contains(roots, new File("card-other/book.pdf")));
        assertFalse(BookStorage.contains(roots, new File("card/../private/book.pdf")));
    }
}

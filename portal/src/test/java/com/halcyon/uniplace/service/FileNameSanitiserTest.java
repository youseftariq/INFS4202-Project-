package com.halcyon.uniplace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TC-SEC-015 / SEC-06: the file name of an upload is sanitised before storage (plain unit test, no Spring). */
class FileNameSanitiserTest {

    @Test
    @DisplayName("TC-SEC-015 / SEC-06: characters other than letters, digits, dot, space and hyphen become underscores")
    void specialCharactersAreReplaced() {
        assertEquals("a_b_c_1_.pdf", DocumentService.sanitise("a&b#c(1).pdf"));
    }

    @Test
    @DisplayName("TC-SEC-015 / SEC-06: folder parts of the name are removed (path traversal)")
    void folderPartsAreRemoved() {
        assertEquals("passwd", DocumentService.sanitise("../../etc/passwd"));
        assertEquals("y.pdf", DocumentService.sanitise("C:\\x\\y.pdf"));
    }

    @Test
    @DisplayName("TC-SEC-015 / SEC-06: empty, blank and hidden names get a default name")
    void emptyAndHiddenNamesGetADefault() {
        assertEquals("document.pdf", DocumentService.sanitise(null));
        assertEquals("document.pdf", DocumentService.sanitise("   "));
        assertEquals("document.pdf", DocumentService.sanitise(".hidden.pdf"));
    }

    @Test
    @DisplayName("TC-SEC-015 / SEC-06: a very long name is cut to 100 characters, keeping the end")
    void longNamesAreShortened() {
        String result = DocumentService.sanitise("a".repeat(150) + ".pdf");
        assertEquals(100, result.length());
        assertTrue(result.endsWith(".pdf"));
    }
}

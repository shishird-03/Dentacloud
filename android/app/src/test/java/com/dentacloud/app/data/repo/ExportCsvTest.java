package com.dentacloud.app.data.repo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExportCsvTest {
    @Test
    public void quotesAndDefusesFormulas() {
        assertEquals("Anita Rao", ExportRepository.csv("Anita Rao"));
        assertEquals("\"Rao, Anita\"", ExportRepository.csv("Rao, Anita"));
        assertEquals("\"say \"\"hi\"\"\"", ExportRepository.csv("say \"hi\""));
        assertEquals("'=HYPERLINK(1)", ExportRepository.csv("=HYPERLINK(1)"));
        assertEquals("'+919845000001", ExportRepository.csv("+919845000001"));
        assertEquals("", ExportRepository.csv(null));
    }
}

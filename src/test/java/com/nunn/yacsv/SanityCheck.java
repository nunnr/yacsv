package com.nunn.yacsv;

import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SanityCheck {
	
	private File tempCsv, csvSource;
	private URL testdata;

	@Before
	public void setUp() throws Exception {
		testdata = ClassLoader.getSystemResource("testdata.csv");
		csvSource = new File(testdata.getFile());
		tempCsv = Files.createTempFile("sanity-check", "csv").toFile();
	}

	@After
	public void tearDown() throws Exception {
		if (tempCsv != null) {
			tempCsv.delete();
		}
	}

	@Test
	public void test() throws IOException {
		try (
			CsvReader csvReader = new CsvReader(testdata.openStream(), StandardCharsets.UTF_8);
			CsvWriter csvWriter = new CsvWriter(new FileWriter(tempCsv));
		) {
			csvWriter.config.setRecordDelimiter("\n");
			for (String[] row : csvReader) {
				csvWriter.writeRecord(row);
			}
		}
		
		assertEquals(tempCsv.length(), csvSource.length());

		try (
			InputStream in1 = new BufferedInputStream(new FileInputStream(tempCsv));
			InputStream in2 = new BufferedInputStream(new FileInputStream(csvSource));
		) {

			int value1, value2;
			do {
				value1 = in1.read();
				value2 = in2.read();
				assertEquals(value1, value2);
			} while (value1 != -1);
		}
	}

}

package com.nunn.yacsv;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SanityCheckTest {
	
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
			if ( ! tempCsv.delete()) {
				throw new Exception("Temp file delete failed. May need manual clean up.");
			}
		}
	}

	@Test
	public void test() throws IOException {
		try (
			CsvReader csvReader = new CsvReader(testdata.openStream(), StandardCharsets.UTF_8);
			CsvWriter csvWriter = new CsvWriter(Files.newBufferedWriter(tempCsv.toPath())); // UTF-8 default
		) {
			csvWriter.config.setRecordDelimiter("\n");
			for (String[] row : csvReader) {
				csvWriter.writeRecord(row);
			}
		}
		
		Assert.assertEquals("Written file length not equal to source file.", csvSource.length(), tempCsv.length());

		try (
			InputStream in1 = new BufferedInputStream(new FileInputStream(csvSource));
			InputStream in2 = new BufferedInputStream(new FileInputStream(tempCsv));
		) {

			int value1, value2;
			do {
				value1 = in1.read();
				value2 = in2.read();
				Assert.assertEquals("Written file differs from source file.", value1, value2);
			} while (value1 != -1);
		}
	}

}

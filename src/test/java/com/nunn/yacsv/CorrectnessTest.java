package com.nunn.yacsv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CorrectnessTest {
	
	private static final String[][] EXPECTED_RESULT = new String[][] {
		{ "Year", "Make", "Model", "Description", "Price" },
		{ "1997", "Ford", "E350", "ac, abs, moon", "3000.00" },
		{ "1999", "Chevy", "Venture \"Extended Edition\"", null, "4900.00" },
		{ "1996", "Jeep", "Grand Cherokee", "MUST SELL!\nair, moon roof, loaded", "4799.00" },
		{ "1999", "Chevy", "Venture \"Extended Edition, Very Large\"", null, "5000.00" },
		{ null, null, "Venture \"Extended Edition\"", null, "4900.00" }
	};
	
	private InputStream testdata;

	@Before
	public void setUp() throws Exception {
		testdata = ClassLoader.getSystemResourceAsStream("correctness.csv");
	}

	@After
	public void tearDown() throws Exception {
		if (testdata != null) {
			testdata.close();
		}
	}

	@Test
	public void test() throws IOException {
		try (CsvReader csvReader = new CsvReader(testdata, StandardCharsets.UTF_8)) {
			for (String[] row : EXPECTED_RESULT) {
				Assert.assertTrue(csvReader.readRecord());
				Assert.assertArrayEquals(row, csvReader.getValues());
			}
		}
	}

}

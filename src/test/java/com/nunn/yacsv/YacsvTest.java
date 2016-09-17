/* Yet Another CSV Tests. Programmed by Rob Nunn.
 * Forked from Java CSV: http://www.csvreader.com/java_csv.php http://sourceforge.net/p/javacsv
 * 
 * Java CSV is a stream based library for reading and writing
 * CSV and other delimited data.
 *   
 * Copyright (C) Bruce Dunwiddie bruce@csvreader.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */
package com.nunn.yacsv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.Test.None;

import com.nunn.yacsv.CsvReader.EmptyCellHandling;

public class YacsvTest {
	
	private static File tempFile;
	
	public static void main(String[] args) throws Exception {
		System.out.println("Starting all tests.");
		
		Class<YacsvTest> testClass = YacsvTest.class;
		
		ArrayList<Method> classSetups = new ArrayList<Method>();
		ArrayList<Method> setups = new ArrayList<Method>();
		ArrayList<Method> classTearDowns = new ArrayList<Method>();
		ArrayList<Method> tearDowns = new ArrayList<Method>();
		ArrayList<Method> testMethods = new ArrayList<Method>();

		for (Method method : testClass.getDeclaredMethods()) {
			int modifiers = method.getModifiers();

			if (Modifier.isPublic(modifiers) && ! method.isAnnotationPresent(Ignore.class)) {
				if (Modifier.isStatic(modifiers)) {
					if (method.isAnnotationPresent(BeforeClass.class)) {
						classSetups.add(method);
					}
					if (method.isAnnotationPresent(AfterClass.class)) {
						classTearDowns.add(method);
					}
				}
				else {
					if (method.isAnnotationPresent(Test.class)) {
						testMethods.add(method);
					}
					else {
						if (method.isAnnotationPresent(Before.class)) {
							setups.add(method);
						}
						if (method.isAnnotationPresent(After.class)) {
							setups.add(method);
						}
					}
				}
			}
		}
		
		testMethods.sort((a, b) -> { return a.getName().compareToIgnoreCase(b.getName()); });

		Object instance = testClass.newInstance();
		
		for (Method setup : classSetups) {
			setup.invoke(instance);
		}

		int passed = 0;
		int failed = 0;
		
		for (Method method : testMethods) {
			for (Method setup : setups) {
				setup.invoke(instance);
			}

			Class<?> expectedException = method.getAnnotation(Test.class).expected();
			if (None.class.equals(expectedException)) {
				expectedException = null;
			}

			try {
				System.out.print(method.getName());
				
				method.invoke(instance);
				
				if (expectedException == null) {
					System.out.println("...Passed");
					passed++;
				}
				else {
					System.out.println("...Failed: Expected exception not thrown: " + expectedException.getName());
					failed++;
				}
			}
			catch (Exception e) {
				if (e.getCause().getClass().equals(expectedException)) {
					System.out.println("...Passed");
					passed++;
				}
				else {
					System.out.println("...Failed: Unexpected exception thrown: " + e.getCause());
					failed++;
				}
			}

			for (Method tearDown : tearDowns) {
				tearDown.invoke(instance);
			}
		}

		for (Method tearDown : classTearDowns) {
			tearDown.invoke(instance);
		}
		
		System.out.println("Done with all tests. " + passed + " passed / " + failed + " failed, of " + (passed + failed) + " total.");
	}

	private static String generateString(char letter, int count) {
		StringBuffer buffer = new StringBuffer(count);
		for (int i = 0; i < count; i++) {
			buffer.append(letter);
		}
		return buffer.toString();
	}

	private static void assertException(Exception expected, Exception actual) {
		Assert.assertEquals(expected.getClass(), actual.getClass());
		Assert.assertEquals(expected.getMessage(), actual.getMessage());
	}
	
	@BeforeClass
	public static void runBeforeClass() {
		try {
			tempFile = Files.createTempFile("test", "csv").toFile();
		}
		catch (IOException e) {
			throw new RuntimeException("Cannot create temporary test file.", e);
		}
	}

	@AfterClass
	public static void runAfterClass() {
		if (tempFile != null) {
			if ( ! tempFile.delete()) {
				throw new RuntimeException("Did not delete temporary test file.");
			}
		}
	}

	@Test
	public void test001() throws Exception {
		CsvReader reader = CsvReader.parse("1,2");
		reader.config.setCaptureRawRecord(true);
		Assert.assertEquals("", reader.getRawRecord());
		Assert.assertEquals("", reader.get(0));
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals("2", reader.get(1));
		Assert.assertEquals(',', reader.config.getDelimiter());
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals("1,2", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test002() throws Exception {
		String data = "\"bob said, \"\"Hey!\"\"\",2, 3 ";
		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("bob said, \"Hey!\"", reader.get(0));
		Assert.assertEquals("2", reader.get(1));
		Assert.assertEquals("3", reader.get(2));
		Assert.assertEquals(',', reader.config.getDelimiter());
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(3, reader.getColumnCount());
		Assert.assertEquals("\"bob said, \"\"Hey!\"\"\",2, 3 ", reader
				.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test003() throws Exception {
		String data = ",";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setEmptyCellHandling(EmptyCellHandling.ALWAYS_EMPTY);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals("", reader.get(1));
		Assert.assertEquals(',', reader.config.getDelimiter());
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals(",", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test004() throws Exception {
		String data = "1\r2";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("2", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("2", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test005() throws Exception {
		String data = "1\n2";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("2", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("2", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test006() throws Exception {
		String data = "1\r\n2";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("2", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("2", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test007() throws Exception {
		String data = "1\r";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test008() throws Exception {
		String data = "1\n";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test009() throws Exception {
		String data = "1\r\n";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test010() throws Exception {
		String data = "1\r2\n";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setDelimiter('\r');
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals("2", reader.get(1));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals("1\r2", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test011() throws Exception {
		String data = "\"July 4th, 2005\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("July 4th, 2005", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("\"July 4th, 2005\"", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test012() throws Exception {
		String data = " 1";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setTrimWhitespace(false);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(" 1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals(" 1", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test013() throws Exception {
		String data = "";

		CsvReader reader = CsvReader.parse(data);
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test014() throws Exception {
		String data = "user_id,name\r\n1,Bruce";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readHeaders());
		Assert.assertEquals("user_id,name", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals("Bruce", reader.get(1));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals(0, reader.getIndex("user_id"));
		Assert.assertEquals(1, reader.getIndex("name"));
		Assert.assertEquals("user_id", reader.getHeader(0));
		Assert.assertEquals("name", reader.getHeader(1));
		Assert.assertEquals("1", reader.get("user_id"));
		Assert.assertEquals("Bruce", reader.get("name"));
		Assert.assertEquals("1,Bruce", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test015() throws Exception {
		String data = "\"data \r\n here\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("data \r\n here", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("\"data \r\n here\"", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test016() throws Exception {
		String data = "\r\r\n1\r";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setEmptyCellHandling(EmptyCellHandling.ALWAYS_EMPTY);
		reader.config.setDelimiter('\r');
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals("", reader.get(1));
		Assert.assertEquals("", reader.get(2));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(3, reader.getColumnCount());
		Assert.assertEquals("\r\r", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals("", reader.get(1));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals("1\r", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test017() throws Exception {
		String data = "\"double\"\"\"\"double quotes\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("double\"\"double quotes", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("\"double\"\"\"\"double quotes\"", reader
				.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test018() throws Exception {
		String data = "1\r";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test019() throws Exception {
		String data = "1\r\n";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test020() throws Exception {
		String data = "1\n";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test021() throws Exception {
		String data = "'bob said, ''Hey!''',2, 3 ";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		reader.config.setTextQualifier('\'');
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("bob said, 'Hey!'", reader.get(0));
		Assert.assertEquals("2", reader.get(1));
		Assert.assertEquals("3", reader.get(2));
		Assert.assertEquals(',', reader.config.getDelimiter());
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(3, reader.getColumnCount());
		Assert
				.assertEquals("'bob said, ''Hey!''',2, 3 ", reader
						.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test022() throws Exception {
		String data = "\"data \"\" here\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setUseTextQualifier(false);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("\"data \"\" here\"", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("\"data \"\" here\"", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test023() throws Exception {
		String data = generateString('a', 75) + "," + generateString('b', 75);

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setUseTextQualifier(false);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(reader.get(0), generateString('a', 75));
		Assert.assertEquals(reader.get(1), generateString('b', 75));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals(generateString('a', 75) + ","
				+ generateString('b', 75), reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test024() throws Exception {
		String data = "1\r\n\r\n1";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setUseTextQualifier(false);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test025() throws Exception {
		String data = "1\r\n# bunch of crazy stuff here\r\n1";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setUseTextQualifier(false);
		reader.config.setUseComments(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test026() throws Exception {
		String data = "\"Mac \"The Knife\" Peter\",\"Boswell, Jr.\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("Mac ", reader.get(0));
		Assert.assertEquals("Boswell, Jr.", reader.get(1));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals("\"Mac \"The Knife\" Peter\",\"Boswell, Jr.\"",
				reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test027() throws Exception {
		String data = "\"1\",Bruce\r\n\"2\n\",Toni\r\n\"3\",Brian\r\n";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals("Bruce", reader.get(1));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals("\"1\",Bruce", reader.getRawRecord());
		Assert.assertTrue(reader.skipRecord());
		Assert.assertEquals("\"2\n\",Toni", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("3", reader.get(0));
		Assert.assertEquals("Brian", reader.get(1));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals("\"3\",Brian", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test028() throws Exception {
		String data = "\"bob said, \\\"Hey!\\\"\",2, 3 ";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("bob said, \"Hey!\"", reader.get(0));
		Assert.assertEquals("2", reader.get(1));
		Assert.assertEquals("3", reader.get(2));
		Assert.assertEquals(',', reader.config.getDelimiter());
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(3, reader.getColumnCount());
		Assert.assertEquals("\"bob said, \\\"Hey!\\\"\",2, 3 ", reader
				.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test029() throws Exception {
		String data = "\"double\\\"\\\"double quotes\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("double\"\"double quotes", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("\"double\\\"\\\"double quotes\"", reader
				.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test030() throws Exception {
		String data = "\"double\\\\\\\\double backslash\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("double\\\\double backslash", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("\"double\\\\\\\\double backslash\"", reader
				.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test031() throws Exception {
		try {
			CsvWriter writer = new CsvWriter("temp.csv", ',', StandardCharsets.UTF_8);
			// writer will trim all whitespace and put this in quotes to preserve it's existence
			writer.writeTrimmed(" \t \t");
			writer.close();
			
			CsvReader reader = new CsvReader("temp.csv", StandardCharsets.UTF_8);
			reader.config.setCaptureRawRecord(true);
			reader.config.setEmptyCellHandling(EmptyCellHandling.ALWAYS_EMPTY);
			Assert.assertTrue(reader.readRecord());
			Assert.assertEquals("", reader.get(0));
			Assert.assertEquals(1, reader.getColumnCount());
			Assert.assertEquals(0L, reader.getCurrentRecord());
			Assert.assertEquals("\"\"", reader.getRawRecord());
			Assert.assertFalse(reader.readRecord());
			reader.close();
		}
		finally {
			Assert.assertTrue(new File("temp.csv").delete());
		}
	}

	@Test
	public void test032() throws Exception {
		String data = "\"Mac \"The Knife\" Peter\",\"Boswell, Jr.\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("Mac ", reader.get(0));
		Assert.assertEquals("Boswell, Jr.", reader.get(1));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertEquals("\"Mac \"The Knife\" Peter\",\"Boswell, Jr.\"",
				reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test033() throws Exception {
		// tests for an old bug where an exception was
		// thrown if Dispose was called without other methods
		// being called. this should not throw an
		// exception
		String fileName = "somefile.csv";

		try {
			Assert.assertTrue(new File(fileName).createNewFile());
			CsvReader reader = new CsvReader(new FileInputStream(fileName), StandardCharsets.UTF_8);
			reader.close();
		}
		finally {
			Assert.assertTrue(new File(fileName).delete());
		}
	}

	@Test
	public void test034() throws Exception {
		String data = "\"Chicane\", \"Love on the Run\", \"Knight Rider\", \"This field contains a comma, but it doesn't matter as the field is quoted\"\r\n"
				+ "\"Samuel Barber\", \"Adagio for Strings\", \"Classical\", \"This field contains a double quote character, \"\", but it doesn't matter as it is escaped\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("Chicane", reader.get(0));
		Assert.assertEquals("Love on the Run", reader.get(1));
		Assert.assertEquals("Knight Rider", reader.get(2));
		Assert
				.assertEquals(
						"This field contains a comma, but it doesn't matter as the field is quoted",
						reader.get(3));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(4, reader.getColumnCount());
		Assert
				.assertEquals(
						"\"Chicane\", \"Love on the Run\", \"Knight Rider\", \"This field contains a comma, but it doesn't matter as the field is quoted\"",
						reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("Samuel Barber", reader.get(0));
		Assert.assertEquals("Adagio for Strings", reader.get(1));
		Assert.assertEquals("Classical", reader.get(2));
		Assert
				.assertEquals(
						"This field contains a double quote character, \", but it doesn't matter as it is escaped",
						reader.get(3));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(4, reader.getColumnCount());
		Assert
				.assertEquals(
						"\"Samuel Barber\", \"Adagio for Strings\", \"Classical\", \"This field contains a double quote character, \"\", but it doesn't matter as it is escaped\"",
						reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test035() throws Exception {
		String data = "Chicane, Love on the Run, Knight Rider, \"This field contains a comma, but it doesn't matter as the field is quoted\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("Chicane", reader.get(0));
		Assert.assertEquals("Love on the Run", reader.get(1));
		Assert.assertEquals("Knight Rider", reader.get(2));
		Assert
				.assertEquals(
						"This field contains a comma, but it doesn't matter as the field is quoted",
						reader.get(3));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(4, reader.getColumnCount());
		Assert
				.assertEquals(
						"Chicane, Love on the Run, Knight Rider, \"This field contains a comma, but it doesn't matter as the field is quoted\"",
						reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test036() throws Exception {
		String data = "\"some \\stuff\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("some stuff", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("\"some \\stuff\"", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test037() throws Exception {
		String data = "  \" Chicane\"  junk here  , Love on the Run, Knight Rider, \"This field contains a comma, but it doesn't matter as the field is quoted\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(" Chicane", reader.get(0));
		Assert.assertEquals("Love on the Run", reader.get(1));
		Assert.assertEquals("Knight Rider", reader.get(2));
		Assert
				.assertEquals(
						"This field contains a comma, but it doesn't matter as the field is quoted",
						reader.get(3));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(4, reader.getColumnCount());
		Assert
				.assertEquals(
						"  \" Chicane\"  junk here  , Love on the Run, Knight Rider, \"This field contains a comma, but it doesn't matter as the field is quoted\"",
						reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test038() throws Exception {
		String data = "1\r\n\r\n\"\"\r\n \r\n2";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setEmptyCellHandling(EmptyCellHandling.ALWAYS_EMPTY);
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("\"\"", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals(2L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals(" ", reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("2", reader.get(0));
		Assert.assertEquals(3L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("2", reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test039() throws Exception {
		CsvReader reader = CsvReader.parse("user_id,name\r\n1,Bruce");
		Assert.assertTrue(reader.config.getSafetySwitch());
		reader.config.setSafetySwitch(false);
		Assert.assertFalse(reader.config.getSafetySwitch());

		Assert.assertEquals('#', reader.config.getComment());
		reader.config.setComment('!');
		Assert.assertEquals('!', reader.config.getComment());

		Assert.assertEquals(CsvReader.EscapeMode.DOUBLED, reader
				.config.getEscapeMode());
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertEquals(CsvReader.EscapeMode.BACKSLASH, reader
				.config.getEscapeMode());

		Assert.assertEquals(2, reader.config.getRecordDelimiter().length);
		Assert.assertEquals('\r', reader.config.getRecordDelimiter()[0]);
		Assert.assertEquals('\n', reader.config.getRecordDelimiter()[1]);
		reader.config.setRecordDelimiter(';');
		Assert.assertEquals(1, reader.config.getRecordDelimiter().length);
		Assert.assertEquals(';', reader.config.getRecordDelimiter()[0]);
		reader.config.setRecordDelimiter('\r', '\n');
		Assert.assertEquals(2, reader.config.getRecordDelimiter().length);
		Assert.assertEquals('\r', reader.config.getRecordDelimiter()[0]);
		Assert.assertEquals('\n', reader.config.getRecordDelimiter()[1]);

		Assert.assertEquals('\"', reader.config.getTextQualifier());
		reader.config.setTextQualifier('\'');
		Assert.assertEquals('\'', reader.config.getTextQualifier());

		Assert.assertFalse(reader.config.getTrimWhitespace());

		Assert.assertFalse(reader.config.getUseComments());
		reader.config.setUseComments(true);
		Assert.assertTrue(reader.config.getUseComments());

		Assert.assertTrue(reader.config.getUseTextQualifier());
		reader.config.setUseTextQualifier(false);
		Assert.assertFalse(reader.config.getUseTextQualifier());
		reader.close();
	}

	@Test
	public void test040() throws Exception {
		String data = "Chicane, Love on the Run, Knight Rider, This field contains a comma\\, but it doesn't matter as the delimiter is escaped";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setUseTextQualifier(false);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("Chicane", reader.get(0));
		Assert.assertEquals("Love on the Run", reader.get(1));
		Assert.assertEquals("Knight Rider", reader.get(2));
		Assert
				.assertEquals(
						"This field contains a comma, but it doesn't matter as the delimiter is escaped",
						reader.get(3));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(4, reader.getColumnCount());
		Assert
				.assertEquals(
						"Chicane, Love on the Run, Knight Rider, This field contains a comma\\, but it doesn't matter as the delimiter is escaped",
						reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test041() throws Exception {
		String data = "double\\\\\\\\double backslash";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setUseTextQualifier(false);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("double\\\\double backslash", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test042() throws Exception {
		String data = "some \\stuff";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setUseTextQualifier(false);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("some stuff", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test043() throws Exception {
		String data = "\"line 1\\nline 2\",\"line 1\\\nline 2\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("line 1\nline 2", reader.get(0));
		Assert.assertEquals("line 1\nline 2", reader.get(1));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test044() throws Exception {
		String data = "line 1\\nline 2,line 1\\\nline 2";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setUseTextQualifier(false);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("line 1\nline 2", reader.get(0));
		Assert.assertEquals("line 1\nline 2", reader.get(1));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test045() throws Exception {
		String data = "\"Chicane\", \"Love on the Run\", \"Knight Rider\", \"This field contains a comma, but it doesn't matter as the field is quoted\"i"
				+ "\"Samuel Barber\", \"Adagio for Strings\", \"Classical\", \"This field contains a double quote character, \"\", but it doesn't matter as it is escaped\"";

		CsvReader reader = CsvReader.parse(data);
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.config.getCaptureRawRecord());
		reader.config.setCaptureRawRecord(false);
		Assert.assertFalse(reader.config.getCaptureRawRecord());
		Assert.assertNull(reader.getRawRecord());
		reader.config.setRecordDelimiter('i');
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("Chicane", reader.get(0));
		Assert.assertEquals("Love on the Run", reader.get(1));
		Assert.assertEquals("Knight Rider", reader.get(2));
		Assert
				.assertEquals(
						"This field contains a comma, but it doesn't matter as the field is quoted",
						reader.get(3));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(4, reader.getColumnCount());
		Assert.assertNull(reader.getRawRecord());
		Assert.assertFalse(reader.config.getCaptureRawRecord());
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.config.getCaptureRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert
				.assertEquals(
						"\"Samuel Barber\", \"Adagio for Strings\", \"Classical\", \"This field contains a double quote character, \"\", but it doesn't matter as it is escaped\"",
						reader.getRawRecord());
		Assert.assertEquals("Samuel Barber", reader.get(0));
		Assert.assertEquals("Adagio for Strings", reader.get(1));
		Assert.assertEquals("Classical", reader.get(2));
		Assert
				.assertEquals(
						"This field contains a double quote character, \", but it doesn't matter as it is escaped",
						reader.get(3));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(4, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		Assert.assertTrue(reader.config.getCaptureRawRecord());
		Assert.assertEquals("", reader.getRawRecord());
		reader.close();
	}

	@Test
	public void test046() throws Exception {
		String data = "Ch\\icane, Love on the Run, Kn\\ight R\\ider, Th\\is f\\ield conta\\ins an \\i\\, but \\it doesn't matter as \\it \\is escapedi"
				+ "Samuel Barber, Adag\\io for Str\\ings, Class\\ical, Th\\is f\\ield conta\\ins a comma \\, but \\it doesn't matter as \\it \\is escaped";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setUseTextQualifier(false);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		reader.config.setRecordDelimiter('i');
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("Chicane", reader.get(0));
		Assert.assertEquals("Love on the Run", reader.get(1));
		Assert.assertEquals("Knight Rider", reader.get(2));
		Assert
				.assertEquals(
						"This field contains an i, but it doesn't matter as it is escaped",
						reader.get(3));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(4, reader.getColumnCount());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("Samuel Barber", reader.get(0));
		Assert.assertEquals("Adagio for Strings", reader.get(1));
		Assert.assertEquals("Classical", reader.get(2));
		Assert
				.assertEquals(
						"This field contains a comma , but it doesn't matter as it is escaped",
						reader.get(3));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(4, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test047() throws Exception {
		byte[] buffer;

		String test = "M�nchen";

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		PrintWriter writer = new PrintWriter(new OutputStreamWriter(stream,
				StandardCharsets.UTF_8));
		writer.println(test);
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		CsvReader reader = new CsvReader(new InputStreamReader(
				new ByteArrayInputStream(buffer), StandardCharsets.UTF_8));
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(test, reader.get(0));
		reader.close();
	}

	@Test
	public void test048() throws Exception {
		byte[] buffer;

		String test = "M�nchen";

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		PrintWriter writer = new PrintWriter(new OutputStreamWriter(stream,
				StandardCharsets.UTF_8));
		writer.write(test);
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		CsvReader reader = new CsvReader(new InputStreamReader(
				new ByteArrayInputStream(buffer), StandardCharsets.UTF_8));
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(test, reader.get(0));
		reader.close();
	}

	@Test
	public void test049() throws Exception {
		String data = "\"\\n\\r\\t\\b\\f\\e\\v\\a\\z\\d065\\o101\\101\\x41\\u0041\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setUseTextQualifier(true);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert
				.assertEquals("\n\r\t\b\f\u001B\u000B\u0007zAAAAA", reader
						.get(0));
		Assert.assertEquals(
				"\"\\n\\r\\t\\b\\f\\e\\v\\a\\z\\d065\\o101\\101\\x41\\u0041\"",
				reader.getRawRecord());
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test050() throws Exception {
		String data = "\\n\\r\\t\\b\\f\\e\\v\\a\\z\\d065\\o101\\101\\x41\\u0041";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setUseTextQualifier(false);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert
				.assertEquals("\n\r\t\b\f\u001B\u000B\u0007zAAAAA", reader
						.get(0));
		Assert.assertEquals(
				"\\n\\r\\t\\b\\f\\e\\v\\a\\z\\d065\\o101\\101\\x41\\u0041",
				reader.getRawRecord());
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test051() throws Exception {
		String data = "\"\\xfa\\u0afa\\xFA\\u0AFA\"";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setUseTextQualifier(true);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("\u00FA\u0AFA\u00FA\u0AFA", reader.get(0));
		Assert.assertEquals("\"\\xfa\\u0afa\\xFA\\u0AFA\"", reader
				.getRawRecord());
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test052() throws Exception {
		String data = "\\xfa\\u0afa\\xFA\\u0AFA";

		CsvReader reader = CsvReader.parse(data);
		reader.config.setCaptureRawRecord(true);
		reader.config.setUseTextQualifier(false);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("\u00FA\u0AFA\u00FA\u0AFA", reader.get(0));
		Assert.assertEquals("\\xfa\\u0afa\\xFA\\u0AFA", reader.getRawRecord());
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test054() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.writeTrimmed("blah \"some stuff in quotes\"");
		writer.endRecord();
		Assert.assertFalse(writer.config.getForceQualifier());
		writer.config.setForceQualifier(true);
		Assert.assertTrue(writer.config.getForceQualifier());
		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.writeTrimmed("blah \"some stuff in quotes\"");
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert
				.assertEquals(
						"\"1,2\",3,\"blah \"\"some stuff in quotes\"\"\"\r\n\"1,2\",\"3\",\"blah \"\"some stuff in quotes\"\"\"",
						data);
	}

	@Test
	public void test055() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.writeTrimmed("");
		writer.writeTrimmed("1");
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("\"\",1", data);
	}

	@Test
	public void test056() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, '\t', StandardCharsets.ISO_8859_1);
		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.writeTrimmed("blah \"some stuff in quotes\"");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals(
				"1,2\t3\t\"blah \"\"some stuff in quotes\"\"\"\r\n", data);
	}

	@Test
	public void test057() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, '\t', StandardCharsets.ISO_8859_1);
		Assert.assertTrue(writer.config.getUseTextQualifier());
		writer.config.setUseTextQualifier(false);
		Assert.assertFalse(writer.config.getUseTextQualifier());
		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.writeTrimmed("blah \"some stuff in quotes\"");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("1,2\t3\tblah \"some stuff in quotes\"\r\n", data);
	}

	@Test
	public void test058() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, '\t', StandardCharsets.ISO_8859_1);
		writer.writeTrimmed("data\r\nmore data");
		writer.writeTrimmed(" 3\t");
		writer.write(" 3\t");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("\"data\r\nmore data\"\t3\t\" 3\t\"\r\n", data);
	}

	@Test
	public void test070() throws Exception {
		String data = "\"1\",Bruce\r\n\"2\",Toni\r\n\"3\",Brian\r\n";

		CsvReader reader = CsvReader.parse(data);
		reader.setHeaders(new String[] { "userid", "name" });
		Assert.assertEquals(2, reader.getHeaderCount());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("1", reader.get("userid"));
		Assert.assertEquals("Bruce", reader.get("name"));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("2", reader.get("userid"));
		Assert.assertEquals("Toni", reader.get("name"));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("3", reader.get("userid"));
		Assert.assertEquals("Brian", reader.get("name"));
		Assert.assertEquals(2L, reader.getCurrentRecord());
		Assert.assertEquals(2, reader.getColumnCount());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test071() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.config.setForceQualifier(true);
		writer.writeTrimmed(" data ");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("\"data\"\r\n", data);
	}

	@Test
	public void test072() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		Assert.assertEquals("\r\n", writer.config.getRecordDelimiter());
		writer.config.setRecordDelimiter(";");
		Assert.assertEquals(";", writer.config.getRecordDelimiter());
		writer.writeTrimmed("a;b");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("\"a;b\";", data);
	}

	@Test
	public void test073() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		Assert.assertEquals(CsvReader.EscapeMode.DOUBLED, writer.config.getEscapeMode());
		writer.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertEquals(CsvReader.EscapeMode.BACKSLASH, writer.config.getEscapeMode());
		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.writeTrimmed("blah \"some stuff in quotes\"");
		writer.endRecord();
		writer.config.setForceQualifier(true);
		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.writeTrimmed("blah \"some stuff in quotes\"");
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();
		Assert
				.assertEquals(
						"\"1,2\",3,\"blah \\\"some stuff in quotes\\\"\"\r\n\"1,2\",\"3\",\"blah \\\"some stuff in quotes\\\"\"",
						data);
	}

	@Test
	public void test074() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		writer.config.setUseTextQualifier(false);
		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.writeTrimmed("blah \"some stuff in quotes\"");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();
		Assert.assertEquals("1\\,2,3,blah \"some stuff in quotes\"\r\n", data);
	}

	@Test
	public void test075() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.writeTrimmed("1");
		writer.endRecord();
		writer.writeComment("blah");
		writer.writeTrimmed("2");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();
		Assert.assertEquals("1\r\n#blah\r\n2\r\n", data);
	}

	@Test
	public void test076() throws Exception {
		CsvReader reader = CsvReader.parse("user_id,name\r\n1,Bruce");
		Assert.assertEquals(0, reader.getHeaders().length);
		Assert.assertEquals(-1, reader.getIndex("user_id"));
		Assert.assertEquals("", reader.getHeader(0));
		Assert.assertTrue(reader.readHeaders());
		Assert.assertEquals(0, reader.getIndex("user_id"));
		Assert.assertEquals("user_id", reader.getHeader(0));
		String[] headers = reader.getHeaders();
		Assert.assertEquals(2, headers.length);
		Assert.assertEquals("user_id", headers[0]);
		Assert.assertEquals("name", headers[1]);
		reader.setHeaders(null);
		Assert.assertEquals(0, reader.getHeaders().length);
		Assert.assertEquals(-1, reader.getIndex("user_id"));
		Assert.assertEquals("", reader.getHeader(0));
		reader.close();
	}

	@Test
	public void test077() {
		try {
			CsvReader.parse(null);
		} catch (Exception ex) {
			assertException(new IllegalArgumentException(
					"Parameter data can not be null."), ex);
		}
	}

	@Test
	public void test078() throws Exception {
		CsvReader reader = CsvReader.parse("1,Bruce");
		Assert.assertTrue(reader.readRecord());
		Assert.assertFalse(reader.isQualified(999));
		reader.close();
	}

	@Test
	public void test079() {
		CsvReader reader;
		reader = CsvReader.parse("");
		reader.close();
		try {
			reader.readRecord();
		} catch (Exception ex) {
			assertException(
					new IOException(
							"This instance of the " + CsvReader.class.getSimpleName() + " class has already been closed."),
					ex);
		}
	}

	@Test
	public void test081() throws Exception {
		CsvReader reader = CsvReader.parse(generateString('a', 100001));
		try {
			reader.readRecord();
		} catch (Exception ex) {
			assertException(
					new IOException("Maximum column length of 100,000 exceeded in column 0 in record 0."
									+ " Set the SafetySwitch property to false if you're expecting column"
									+ " lengths greater than 100,000 characters to avoid this error."),
					ex);
		}
		reader.close();
	}

	@Test
	public void test082() throws Exception {
		StringBuilder holder = new StringBuilder(200010);

		for (int i = 0; i < 1000; i++) {
			holder.append("a,");
		}

		holder.append("a");

		CsvReader reader = CsvReader.parse(holder.toString());
		try {
			reader.readRecord();
		} catch (Exception ex) {
			String msg = "Maximum column count of 1000 exceeded in record 0."
						+ " Configure config.setSafetySwitch(false) if you're expecting"
						+ " more than 1000 columns per record to avoid this error.";
			assertException(new IOException(msg), ex);
		}
		reader.close();
	}

	@Test
	public void test083() throws Exception {
		CsvReader reader = CsvReader.parse(generateString('a', 100001));
		reader.config.setSafetySwitch(false);
		reader.readRecord();
		reader.close();
	}

	@Test
	public void test084() throws Exception {
		StringBuilder holder = new StringBuilder(200010);

		for (int i = 0; i < 100000; i++) {
			holder.append("a,");
		}

		holder.append("a");

		CsvReader reader = CsvReader.parse(holder.toString());
		reader.config.setSafetySwitch(false);
		reader.readRecord();
		reader.close();
	}

	@Test
	public void test085() throws Exception {
		CsvReader reader = CsvReader.parse(generateString('a', 100000));
		reader.readRecord();
		reader.close();
	}

	@Test
	public void test086() throws Exception {
		StringBuilder holder = new StringBuilder(2500);

		for (int i = 0; i < 999; i++) {
			holder.append("a,");
		}

		holder.append("a");

		CsvReader reader = CsvReader.parse(holder.toString());
		reader.readRecord();
		reader.close();
	}

	@Test
	public void test087() throws Exception {
		try {
			CsvWriter writer = new CsvWriter("temp.csv");
			writer.writeTrimmed("1");
			writer.close();
	
			CsvReader reader = new CsvReader(new FileInputStream("temp.csv"), StandardCharsets.UTF_8);
			Assert.assertTrue(reader.readRecord());
			Assert.assertEquals("1", reader.get(0));
			Assert.assertEquals(1, reader.getColumnCount());
			Assert.assertEquals(0L, reader.getCurrentRecord());
			Assert.assertFalse(reader.readRecord());
			reader.close();
		}
		finally {
			Assert.assertTrue(new File("temp.csv").delete());
		}
	}

	@Test
	public void test088() throws Exception {
		try {
			new CsvReader((String) null, StandardCharsets.ISO_8859_1).close();
		} catch (Exception ex) {
			assertException(new IllegalArgumentException(
					"Parameter fileName can not be null."), ex);
		}
	}

	@Test
	public void test089() throws Exception {
		try {
			new CsvReader(new ByteArrayInputStream(new byte[0]), null).close();
		} catch (Exception ex) {
			assertException(new IllegalArgumentException(
					"Parameter charset can not be null."), ex);
		}
	}

	@Test
	public void test090() throws Exception {
		try {
			new CsvReader((Reader) null).close();
		} catch (Exception ex) {
			assertException(new IllegalArgumentException(
					"Parameter inputReader can not be null."), ex);
		}
	}

	@Test
	public void test091() throws Exception {
		byte[] buffer;

		String test = "test";

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		PrintWriter writer = new PrintWriter(stream);
		writer.println(test);
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		CsvReader reader = new CsvReader(new ByteArrayInputStream(buffer),
				StandardCharsets.ISO_8859_1);
		reader.readRecord();
		Assert.assertEquals(test, reader.get(0));
		reader.close();
	}

	@Test
	public void test092() throws Exception {
		byte[] buffer;

		String test = "test";

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		PrintWriter writer = new PrintWriter(stream);
		writer.println(test);
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		CsvReader reader = new CsvReader(new ByteArrayInputStream(buffer),
				StandardCharsets.ISO_8859_1);
		reader.readRecord();
		Assert.assertEquals(test, reader.get(0));
		reader.close();
	}

	@Test
	public void test112() throws Exception {
		try {
			new CsvWriter((String) null, ',', StandardCharsets.ISO_8859_1).close();
		} catch (Exception ex) {
			assertException(new IllegalArgumentException("Parameter fileName can not be null."), ex);
		}
	}

	@Test
	public void test113() throws Exception {
		try {
			new CsvWriter(tempFile.getAbsolutePath(), ',', (Charset) null).close();
		} catch (Exception ex) {
			assertException(new IllegalArgumentException("Parameter charset can not be null."), ex);
		}
	}

	@Test
	public void test114() throws Exception {
		try {
			new CsvWriter((Writer) null, ',').close();
		} catch (Exception ex) {
			assertException(new IllegalArgumentException("Parameter writer can not be null."), ex);
		}
	}

	@Test
	public void test115() throws Exception {
		try {
			CsvWriter writer = new CsvWriter(tempFile.getAbsolutePath());

			writer.close();

			writer.writeTrimmed("");
		} catch (Exception ex) {
			assertException(new IOException("This instance of the CsvWriter class has already been closed."), ex);
		}
	}

	@Test
	public void test117() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		Assert.assertEquals('#', writer.config.getComment());
		writer.config.setComment('~');
		Assert.assertEquals('~', writer.config.getComment());

		writer.config.setRecordDelimiter(";");

		writer.writeTrimmed("1");
		writer.endRecord();
		writer.writeComment("blah");

		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("1;~blah;", data);
	}

	@Test
	public void test118() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, '\t', StandardCharsets.ISO_8859_1);
		Assert.assertEquals('\"', writer.config.getTextQualifier());
		writer.config.setTextQualifier('\'');
		Assert.assertEquals('\'', writer.config.getTextQualifier());

		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.writeTrimmed("blah \"some stuff in quotes\"");
		writer.writeTrimmed("blah \'some stuff in quotes\'");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert
				.assertEquals(
						"1,2\t3\tblah \"some stuff in quotes\"\t\'blah \'\'some stuff in quotes\'\'\'\r\n",
						data);
	}

	@Test
	public void test119() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.endRecord();

		Assert.assertEquals(',', writer.config.getDelimiter());
		writer.config.setDelimiter('\t');
		Assert.assertEquals('\t', writer.config.getDelimiter());

		writer.writeTrimmed("1,2");
		writer.writeTrimmed("3");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("\"1,2\",3\r\n1,2\t3\r\n", data);
	}

	@Test
	public void test120() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.writeTrimmed("1,2");
		writer.endRecord();

		buffer = stream.toByteArray();
		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();
		Assert.assertEquals("", data);

		writer.flush(); // testing that flush flushed to stream

		buffer = stream.toByteArray();
		stream.close();
		data = StandardCharsets.ISO_8859_1.decode(ByteBuffer.wrap(buffer))
				.toString();
		Assert.assertEquals("\"1,2\"\r\n", data);
		writer.close();
	}

	@Test
	public void test121() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.writeRecordTrimmed(new String[] { " 1 ", "2" });
		writer.writeRecord(new String[] { " 1 ", "2" });
		writer.writeRecord(new String[0]);
		writer.writeRecord(null);
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();
		Assert.assertEquals("1,2\r\n\" 1 \",2\r\n", data);
	}

	@Test
	public void test122() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.writeTrimmed("1,2");
		writer.writeTrimmed(null);
		writer.write("3 ");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("\"1,2\",,\"3 \"\r\n", data);
	}

	@Test
	public void test123() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.writeTrimmed("#123");
		writer.endRecord();

		writer.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		writer.config.setUseTextQualifier(false);

		writer.writeTrimmed("#123");
		writer.endRecord();

		writer.writeTrimmed("#");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("\"#123\"\r\n\\#123\r\n\\#\r\n", data);
	}

	@Test
	public void test124() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.config.setRecordDelimiter(";");
		writer.config.setUseTextQualifier(false);
		writer.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);

		writer.writeTrimmed("1;2");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("1\\;2;", data);
	}

	@Test
	public void test131() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.config.setUseTextQualifier(false);
		writer.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);

		writer.writeTrimmed("1,\\\r\n2");
		writer.endRecord();

		writer.config.setRecordDelimiter(";");

		writer.writeTrimmed("1,\\;2");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("1\\,\\\\\\\r\\\n2\r\n1\\,\\\\\\;2;", data);
	}

	@Test
	public void test132() throws Exception {
		byte[] buffer;

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		CsvWriter writer = new CsvWriter(stream, ',', StandardCharsets.ISO_8859_1);
		writer.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);

		writer.writeTrimmed("1,\\2");
		writer.endRecord();
		writer.close();

		buffer = stream.toByteArray();
		stream.close();

		String data = StandardCharsets.ISO_8859_1.decode(
				ByteBuffer.wrap(buffer)).toString();

		Assert.assertEquals("\"1,\\\\2\"\r\n", data);
	}

	@Test
	public void test135() throws Exception {
		CsvReader reader = CsvReader.parse("1\n\n1\r\r1\r\n\r\n1\n\r1");
		reader.config.setEmptyCellHandling(EmptyCellHandling.ALWAYS_EMPTY);
		Assert.assertTrue(reader.config.getSkipEmptyRecords());
		reader.config.setSkipEmptyRecords(false);
		Assert.assertFalse(reader.config.getSkipEmptyRecords());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(2L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals(3L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(4L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals(5L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(6L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals(7L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(8L, reader.getCurrentRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test136() throws Exception {
		CsvReader reader = CsvReader.parse("1\n\n1\r\r1\r\n\r\n1\n\r1");
		Assert.assertTrue(reader.config.getSkipEmptyRecords());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(2L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(3L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(4L, reader.getCurrentRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test137() throws Exception {
		CsvReader reader = CsvReader.parse("1;; ;1");
		reader.config.setEmptyCellHandling(EmptyCellHandling.ALWAYS_EMPTY);
		
		Assert.assertArrayEquals(new char[]{'\r', '\n'}, reader.config.getRecordDelimiter());
		reader.config.setRecordDelimiter(';');
		Assert.assertArrayEquals(new char[]{';'}, reader.config.getRecordDelimiter());
		
		Assert.assertTrue(reader.config.getSkipEmptyRecords());
		reader.config.setSkipEmptyRecords(false);
		Assert.assertFalse(reader.config.getSkipEmptyRecords());
		
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals(2L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(3L, reader.getCurrentRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test138() throws Exception {
		CsvReader reader = CsvReader.parse("1;; ;1");
		reader.config.setEmptyCellHandling(EmptyCellHandling.ALWAYS_EMPTY);
		reader.config.setRecordDelimiter(';');
		Assert.assertFalse(reader.config.getTrimWhitespace());
		reader.config.setTrimWhitespace(true);
		Assert.assertTrue(reader.config.getTrimWhitespace());
		Assert.assertTrue(reader.config.getSkipEmptyRecords());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(0L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("", reader.get(0));
		Assert.assertEquals(1L, reader.getCurrentRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(1, reader.getColumnCount());
		Assert.assertEquals("1", reader.get(0));
		Assert.assertEquals(2L, reader.getCurrentRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test143() throws Exception {
		CsvReader reader = CsvReader.parse("\"" + generateString('a', 100001)
				+ "\"");
		try
		{
			reader.readRecord();
		}
		catch (Exception ex)
		{
			assertException(new IOException("Maximum column length of 100,000 exceeded in column 0 in record 0."
											+ " Set the SafetySwitch property to false if you're expecting column"
											+ " lengths greater than 100,000 characters to avoid this error."), ex);
		}
		reader.close();
	}

	@Test
	public void test144() throws Exception {
		CsvReader reader = CsvReader.parse("\"" + generateString('a', 100000)
				+ "\"");
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(generateString('a', 100000), reader.get(0));
		Assert.assertEquals("\"" + generateString('a', 100000) + "\"", reader
				.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test145() throws Exception {
		CsvReader reader = CsvReader.parse("\"" + generateString('a', 100001)
				+ "\"");
		reader.config.setSafetySwitch(false);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(generateString('a', 100001), reader.get(0));
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test146() throws Exception {
		// testing SkipLine's buffer
		CsvReader reader = CsvReader.parse("\"" + generateString('a', 10000)
				+ "\r\nb");
		Assert.assertNull(reader.getRawRecord());
		Assert.assertTrue(reader.skipLine());
		Assert.assertNull(reader.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals("b", reader.get(0));
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test147() throws Exception {
		// testing AppendLetter's buffer
		StringBuilder data = new StringBuilder(20000);
		for (int i = 0; i < 10000; i++) {
			data.append("\\b");
		}

		CsvReader reader = CsvReader.parse(data.toString());
		reader.config.setUseTextQualifier(false);
		reader.config.setEscapeMode(CsvReader.EscapeMode.BACKSLASH);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(generateString('\b', 10000), reader.get(0));
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test148() throws Exception {
		// testing a specific case in GetRawRecord where the result is what's in
		// the data buffer
		// plus what's in the raw buffer
		CsvReader reader = CsvReader.parse("\"" + generateString('a', 100000)
				+ "\"\r\n" + generateString('a', 100000));
		reader.config.setCaptureRawRecord(true);
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(generateString('a', 100000), reader.get(0));
		Assert.assertEquals("\"" + generateString('a', 100000) + "\"", reader
				.getRawRecord());
		Assert.assertTrue(reader.readRecord());
		Assert.assertEquals(generateString('a', 100000), reader.get(0));
		Assert.assertEquals(generateString('a', 100000), reader.getRawRecord());
		Assert.assertFalse(reader.readRecord());
		reader.close();
	}

	@Test
	public void test149() throws Exception {
		try
		{
			new CsvReader("somefilethatdoesntexist.csv", Charset.defaultCharset()).close();
		}
		catch (Exception ex)
		{
			assertException(new IllegalArgumentException("Could not resolve the given file name: somefilethatdoesntexist.csv"), ex);
		}
	}

	@Test
	public void test173() throws Exception {
		FailingReader fail = new FailingReader();

		CsvReader reader = new CsvReader(fail);
		boolean exceptionThrown = false;

		Assert.assertFalse(fail.disposeCalled);
		try {
			// need to test IO exception block logic while trying to read
			reader.readRecord();
		} catch (IOException ex) {
			// make sure stream that caused exception
			// has been sent a dispose call
			Assert.assertTrue(fail.disposeCalled);
			exceptionThrown = true;
			Assert.assertEquals("Read failed.", ex.getMessage());
		} finally {
			reader.close();
		}

		Assert.assertTrue(exceptionThrown);

		// test to make sure object has been marked
		// internally as disposed
		try
		{
			reader.getHeaders();
		}
		catch (Exception ex)
		{
			assertException(new IOException("This instance of the " + CsvReader.class.getSimpleName() + " class has already been closed."), ex);
		}
	}

	private static class FailingReader extends Reader {
		public boolean disposeCalled = false;

		public FailingReader() {
			super("");
		}

		@Override
		public int read(char[] buffer, int index, int count) throws IOException {
			throw new IOException("Read failed.");
		}

		@Override
		public void close() {
			disposeCalled = true;
		}
	}
	
	@Test
	public void test174() throws IOException {
		// verifies that data is eventually automatically flushed
		try {
			CsvWriter writer = new CsvWriter("temp.csv");
			
			for (int i = 0; i < 10000; i++)
			{
				writer.writeTrimmed("stuff");
				writer.endRecord();
			}
			
			CsvReader reader = new CsvReader(new FileInputStream("temp.csv"), StandardCharsets.UTF_8);
			
			Assert.assertTrue(reader.readRecord());
			
			Assert.assertEquals("stuff", reader.get(0));
			
			writer.close();
			reader.close();
		}
		finally {
			Assert.assertTrue(new File("temp.csv").delete());
		}
	}
	
	/** checking close(boolean closeInputStream) */
	@Test
	public void test175() throws IOException {
		StringReader sr = new StringReader("somejunk");
		
		CsvReader reader = new CsvReader(sr);
		reader.close(false);
		
		Assert.assertTrue(sr.ready());
		
		reader = new CsvReader(sr);
		reader.close(true);
		
		try
		{
			sr.ready();
		}
		catch (Exception ex)
		{
			assertException(new IOException("Stream closed"), ex);
		}
	}
	
	@Test
	public void test176() throws Exception {
		try {
			CsvWriter writer = new CsvWriter("temp.csv");
			writer.writeTrimmed("1");
			writer.close();
	
			Path path = Paths.get("temp.csv");
			
			CsvReader reader = new CsvReader(path, StandardCharsets.UTF_8);
			Assert.assertTrue(reader.readRecord());
			reader.close();
		}
		finally {
			Assert.assertTrue(new File("temp.csv").delete());
		}
	}
	
	/** test header setting */
	@Test
	public void test177() throws IOException {
		CsvReader reader = CsvReader.parse("somejunk");
		
		String[] headers = {};
		reader.setHeaders(headers);
		Assert.assertArrayEquals(reader.getHeaders(), headers);
		
		headers = new String[]{"test1"};
		reader.setHeaders(headers);
		Assert.assertArrayEquals(reader.getHeaders(), headers);
		
		headers = new String[]{"test1", "test2"};
		reader.setHeaders(headers);
		Assert.assertArrayEquals(reader.getHeaders(), headers);
		
		headers = new String[]{"test1", "test2", "test2"};
		try
		{
			reader.setHeaders(headers);
		}
		catch (Exception ex)
		{
			assertException(new IllegalArgumentException("Found duplicate headers with name test2"), ex);
		}
		Assert.assertArrayEquals(reader.getHeaders(), new String[0]);
		
		reader.close(true);
	}
	
}

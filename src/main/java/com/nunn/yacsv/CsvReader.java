/* Yet Another CSV Reader. Programmed by Rob Nunn.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/** A streaming design parser for delimited text data. */
public class CsvReader implements AutoCloseable, Iterator<String[]> {
	
	private Reader reader = null;
	private boolean closed = false;
	
	// this will be our working buffer to hold data chunks read in from the data file
	private DataBuffer dataBuffer = new DataBuffer(8192); // Reader.read(...) buffer
	private Buffer columnBuffer = new Buffer(32); // INITIAL_COLUMN_BUFFER_SIZE
	private Buffer rawBuffer = new Buffer(512); // INITIAL_COLUMN_BUFFER_SIZE * INITIAL_COLUMN_COUNT
	
	// these are all more or less global loop variables to keep from needing to pass them all into various methods during parsing
	private boolean startedColumn = false;
	private boolean startedWithQualifier = false;
	private boolean hasMoreData = true;
	private int columnsCount = 0;
	private long currentRecord = 0;
	private String[] values = new String[16]; // INITIAL_COLUMN_COUNT
	private boolean[] isQualified = new boolean[16]; // INITIAL_COLUMN_COUNT
	private String[] csvHeaders = {};
	private Map<String, Integer> headerIndex = new HashMap<String, Integer>();
	private char lastLetter;
	private char currentLetter;
	private boolean readingComplexEscape;
	private int escape;
	private int escapeLength;
	private char escapeValue;
	
	/** Configuration accessor - getters and setters for CsvReader behaviour options are exposed here. */
	public final Config config = new Config();
	// these are all the values for switches that the user is may set
	private char textQualifier = Letters.QUOTE;
	private boolean trimWhitespace = true;
	private boolean useTextQualifier = true;
	private char cellDelimiter = Letters.COMMA;
	private RowDelimiter rowDelimiter = new RowDelimiter(Letters.CR, Letters.LF);
	private char comment = Letters.POUND;
	private boolean useComments = false;
	private EscapeMode escapeMode = CsvReader.EscapeMode.DOUBLED;
	private SafetyLimiter safetyLimit = new SafetyLimiter();
	private boolean skipEmptyRecords = true;
	private boolean captureRawRecord = false;
	
	// implementation for Iterator<String[]>
	private Boolean iteratorReadStatus = null;
	
	private class Buffer {
		public char[] buffer;
		public int position = 0;
		
		public Buffer(int size) {
			buffer = new char[size];
		}
		
		public void expand(int addLength) {
			char[] temp = new char[buffer.length + addLength];
			System.arraycopy(buffer, 0, temp, 0, position);
			buffer = temp;
		}
		
		public void append(Buffer source, int sourceStart, int sourceEnd) {
			int delta = sourceEnd - sourceStart;
			
			if (buffer.length - position < delta) {
				expand(Math.max(delta, buffer.length));
			}
			
			System.arraycopy(source.buffer, sourceStart, buffer, position, delta);
			position += delta;
		}
		
		private void append(char letter) {
			if (position == buffer.length) {
				expand(buffer.length);
			}
			
			buffer[position] = letter;
			position++;
		}
	}
	
	private class DataBuffer extends Buffer {
		/** How much usable data has been read into the stream, which will not always be as long as Buffer.Length. */
		public int count = 0;
		/** The position of the cursor in the buffer when the current column was started or the last time data was moved out to the column buffer. */
		public int columnStart = 0;
		public int lineStart = 0;
		
		public DataBuffer(int size) {
			super(size);
		}
	}
	
	private class RowDelimiter {
		protected final char delimiterOne;
		protected final char delimiterTwo;
		
		private RowDelimiter(char delimiterOne, char delimiterTwo) {
			this.delimiterOne = delimiterOne;
			this.delimiterTwo = delimiterTwo;
		}
		
		public boolean matches() {
			return currentLetter == delimiterOne || currentLetter == delimiterTwo;
		}
		
		public boolean includeEmptyRecord() {
			return startedColumn || columnsCount > 0 || (!skipEmptyRecords && (currentLetter == delimiterOne || lastLetter != delimiterOne));
		}
		
		public char[] getDelimiter() {
			return new char[]{delimiterOne, delimiterTwo};
		}
	}
	
	private class RowDelimiterSingleChar extends RowDelimiter {	
		public RowDelimiterSingleChar(char delimiterOne) {
			super(delimiterOne, Letters.NULL);
		}
		
		@Override
		public boolean matches() {
			return currentLetter == delimiterOne;
		}
		
		@Override
		public boolean includeEmptyRecord() {
			return startedColumn || columnsCount > 0 || !skipEmptyRecords;
		}
		
		@Override
		public char[] getDelimiter() {
			return new char[]{delimiterOne};
		}
	}
	
	private class SafetyLimiter {
		private static final int MAX_COLUMNS = 100000;
		
		public void test() throws IOException {
			if (columnsCount >= MAX_COLUMNS) {
				close();
				NumberFormat nf = NumberFormat.getIntegerInstance();
				String max = nf.format(MAX_COLUMNS);
				throw new IOException("Maximum column count of " + max + " exceeded in record " + nf.format(currentRecord)
						+ ". Set the SafetyLimit property to false if you're expecting more than " + max + " columns per record to avoid this error.");
			}
		}
	}
	
	private class SafetyLimiterNoOp extends SafetyLimiter {
		@Override
		public void test() throws IOException {
			// does nothing
		}
	}
	
	protected class ComplexEscape {
		private static final int UNICODE = 1;
		private static final int OCTAL = 2;
		private static final int DECIMAL = 3;
		private static final int HEX = 4;
	}
	
	protected class Letters {
		public static final char LF = '\n';
		public static final char CR = '\r';
		public static final char QUOTE = '"';
		public static final char COMMA = ',';
		public static final char SPACE = ' ';
		public static final char TAB = '\t';
		public static final char POUND = '#';
		public static final char BACKSLASH = '\\';
		public static final char NULL = '\0';
		public static final char BACKSPACE = '\b';
		public static final char FORM_FEED = '\f';
		public static final char ESCAPE = '\u001B'; // ASCII/ANSI escape
		public static final char VERTICAL_TAB = '\u000B';
		public static final char ALERT = '\u0007';
	}
	
	public static enum EscapeMode {
		/** Double up the text qualifier to represent an occurrence of the text qualifier. */
		DOUBLED,
		/** Use a backslash character before the text qualifier to represent an occurrence of the text qualifier. */
		BACKSLASH;
	}
	
	/** Creates a {@link com.nunn.yacsv.CsvReader CsvReader} object using a String of data as the source.
	 * @param data The data source.
	 * @return A {@link com.nunn.yacsv.CsvReader CsvReader} object using the String of data as the source. */
	public static CsvReader parse(String data) {
		if (data == null) {
			throw new IllegalArgumentException("Parameter data can not be null.");
		}
		return new CsvReader(new StringReader(data));
	}
	
	/** Constructs a {@link com.nunn.yacsv.CsvReader CsvReader} object using a {@link java.io.Reader Reader} object as the data source.
	 * @param inputReader The data source. */
	public CsvReader(Reader inputReader) {
		if (inputReader == null) {
			throw new IllegalArgumentException("Parameter inputReader can not be null.");
		}
		reader = inputReader;
	}
	
	/** Constructs a {@link com.nunn.yacsv.CsvReader CsvReader} object using an {@link java.io.InputStream InputStream} object as the data source.
	 * @param inputStream The data source.
	 * @param charset The {@link java.nio.charset.Charset Charset} to interpret the data. */
	public CsvReader(InputStream inputStream, Charset charset) {
		this(newReader(inputStream, charset));
	}
	
	/** Constructs a {@link com.nunn.yacsv.CsvReader CsvReader} object using a {@link java.nio.file.Path Path} object as the data source.
	 * @param path The path to the data source.
	 * @param charset The {@link java.nio.charset.Charset Charset} to interpret the data. */
	public CsvReader(Path path, Charset charset) {
		this(newReader(path, charset));
	}
	
	/** Constructs a {@link com.nunn.yacsv.CsvReader CsvReader} object using the named file as the data source.
	 * @param fileName The file name to resolve the data source.
	 * @param charset The {@link java.nio.charset.Charset Charset} to interpret the data. */
	public CsvReader(String fileName, Charset charset) {
		this(newReader(fileName, charset));
	}
	
	private static Reader newReader(InputStream inputStream, Charset charset) {
		if (inputStream == null) {
			throw new IllegalArgumentException("Parameter inputStream can not be null.");
		}
		if (charset == null) {
			throw new IllegalArgumentException("Parameter charset can not be null.");
		}
		return new InputStreamReader(inputStream, charset);
	}
	
	private static Reader newReader(Path path, Charset charset) {
		if (path == null) {
			throw new IllegalArgumentException("Parameter path can not be null.");
		}
		if (charset == null) {
			throw new IllegalArgumentException("Parameter charset can not be null.");
		}
		
		try {
			return Files.newBufferedReader(path, charset);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Could not open the given path: " + path, e);
		}
	}
	
	private static Reader newReader(String fileName, Charset charset) {
		if (fileName == null) {
			throw new IllegalArgumentException("Parameter fileName can not be null.");
		}
		
		try {
			Path path = Paths.get(fileName);
			return newReader(path, charset);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Could not resolve the given file name: " + fileName, e);
		}
	}
	
	public class Config {
		
		private Config(){}
		
		/** Get value of option to additionally make records available as raw (not column-parsed) data. This adds a performance overhead. Default is FALSE.
		 * @return Value of option to additionally make records available as raw data. */
		public boolean getCaptureRawRecord() {
			return captureRawRecord;
		}
		
		/** Set value of option to additionally make records available as raw (not column-parsed) data. This adds a performance overhead. Default is FALSE.
		 * @param capture Set TRUE to enable availability of raw record data. */
		public void setCaptureRawRecord(boolean capture) {
			captureRawRecord = capture;
		}
		
		/** Gets value of option to trim leading and trailing whitespace characters from non-textqualified column data. Default is TRUE.
		 * @return Value of option to trim leading and trailing whitespace characters from non-textqualified column data. */
		public boolean getTrimWhitespace() {
			return trimWhitespace;
		}
		
		/** Sets option to trim leading and trailing whitespace characters from non-textqualified column data. Default is TRUE.
		 * @param trim Set TRUE to trim leading and trailing whitespace characters from non-textqualified column data. */
		public void setTrimWhitespace(boolean trim) {
			trimWhitespace = trim;
		}
		
		/** Gets the character being used as the column delimiter. Default is comma: ,
		 * @return The character being used as the column delimiter. */
		public char getDelimiter() {
			return cellDelimiter;
		}
		
		/** Sets the character to use as the column delimiter. Default is comma: ,
		 * @param delimiter The character to use as the column delimiter. */
		public void setDelimiter(char delimiter) {
			cellDelimiter = delimiter;
		}
		
		/** Gets the record delimiter. For CSV this is typically some form of line ending. Default is [\r\n].
		 * @return Single char or pair of chars (typically for "\r\n" usage) to represent record delimiter. */
		public char[] getRecordDelimiter() {
			return rowDelimiter.getDelimiter();
		}
		
		/** Sets a single character to use as the record delimiter. By default the delimiter is a pair of chars, not a single char.
		 * @param delimiterOne The character to use as the record delimiter. */
		public void setRecordDelimiter(char delimiterOne) {
			rowDelimiter = new RowDelimiterSingleChar(delimiterOne);
		}
		
		/** Sets a pair of characters to use as the record delimiter, e.g. [\r\n] for Windows line breaks.
		 * @param delimiterOne The first character to use as the record delimiter.
		 * @param delimiterTwo The second character to use as the record delimiter. */
		public void setRecordDelimiter(char delimiterOne, char delimiterTwo) {
			rowDelimiter = new RowDelimiter(delimiterOne, delimiterTwo);
		}
		
		/** Gets the character to use as a text qualifier in the data. Default is double quote: "
		 * @return The character to use as a text qualifier in the data. */
		public char getTextQualifier() {
			return textQualifier;
		}
		
		/** Sets the character to use as a text qualifier in the data. Default is double quote: "
		 * @param qualifier The character to use as a text qualifier in the data. */
		public void setTextQualifier(char qualifier) {
			textQualifier = qualifier;
		}
		
		/** Gets the value of option to use a text qualifier while parsing. Default is TRUE.
		 * @return Value of option to use a text qualifier while parsing. */
		public boolean getUseTextQualifier() {
			return useTextQualifier;
		}
		
		/** Sets the option to use a text qualifier while parsing. Default is TRUE.
		 * @param use Set TRUE to enable parsing with a text qualifier. */
		public void setUseTextQualifier(boolean use) {
			useTextQualifier = use;
		}
		
		/** Gets the character being used as a comment signal. Default is pound/hash/sharp: #
		 * @return The character being used as a comment signal. */
		public char getComment() {
			return comment;
		}
		
		/** Sets the character to use as a comment signal. Default is pound/hash/sharp: #
		 * @param commentChar The character to use as a comment signal. */
		public void setComment(char commentChar) {
			comment = commentChar;
		}
		
		/** Gets the value of option to ignore comment data while parsing. Default is FALSE.
		 * @return Value of option to ignore comment data while parsing. */
		public boolean getUseComments() {
			return useComments;
		}
		
		/** Sets the value of option to ignore comment data while parsing. Default is FALSE.
		 * @param use Set TRUE to ignore comment data while parsing. */
		public void setUseComments(boolean use) {
			useComments = use;
		}
		
		/** Gets the method used to escape an occurrence of the text qualifier inside qualified data. Default is {@link com.nunn.yacsv.CsvReader.EscapeMode#DOUBLED DOUBLED}
		 * @return The method used to escape an occurrence of the text qualifier inside qualified data. */
		public EscapeMode getEscapeMode() {
			return escapeMode;
		}
		
		/** Sets the method used to escape an occurrence of the text qualifier inside qualified data. Default is {@link com.nunn.yacsv.CsvReader.EscapeMode#DOUBLED DOUBLED} 
		 * @param mode The method used to escape an occurrence of the text qualifier inside qualified data. */
		public void setEscapeMode(EscapeMode mode) {
			escapeMode = mode;
		}
		
		/** Get option to silently skip empty records. Default: TRUE
		 * @return Value of option to silently skip empty records. */
		public boolean getSkipEmptyRecords() {
			return skipEmptyRecords;
		}
		
		/** Set option to silently skip empty records. Default: TRUE
		 * @param skip Set TRUE to silently skip empty records.*/
		public void setSkipEmptyRecords(boolean skip) {
			skipEmptyRecords = skip;
		}
		
		/** Get value of option to perform safe parsing. Default is TRUE.
		 * This feature is intended to prevent excessive memory use in the case where parsing settings (e.g. Charset) don't match the format of a file.
		 * Disable if the file format is known and tested. When disabled, the max column count and length are greatly increased.
		 * @return The value of the safety switch option. */
		public boolean getSafetySwitch() {
			return safetyLimit.getClass().equals(SafetyLimiter.class);
		}
		
		/** Set the value of option to perform safe parsing. Default is TRUE.
		 * This feature is intended to prevent excessive memory use in the case where parsing settings (e.g. Charset) don't match the format of a file.
		 * Disable if the file format is known and tested. When disabled, the max column count and length are greatly increased.
		 * @param safetySwitch Set TRUE to enable the safe parsing feature. */
		public void setSafetySwitch(boolean safetySwitch) {
			if (safetySwitch) {
				safetyLimit = new SafetyLimiter();
			}
			else {
				safetyLimit = new SafetyLimiterNoOp();
			}
		}
		
	}
	
	/** Gets the count of columns found in this record.
	 * @return The count of columns found in this record. */
	public int getColumnCount() {
		return columnsCount;
	}
	
	/** Gets the count of headers setup by a previous call to {@link com.nunn.yacsv.CsvReader#readHeaders readHeaders()} or {@link com.nunn.yacsv.CsvReader#setHeaders setHeaders()}.
	 * @return The count of headers. */
	public int getHeaderCount() {
		return csvHeaders.length;
	}
	
	/** Returns a copy of the header values as a String array.
	 * @return The header values as a String array.
	 * @exception IOException Thrown if this CSVReader has already been closed. */
	public String[] getHeaders() throws IOException {
		checkClosed();
		
		String[] clone = new String[csvHeaders.length];
		System.arraycopy(csvHeaders, 0, clone, 0, csvHeaders.length);
		return clone;
	}
	
	/** Set arbitrary CSV column headers. Useful when CSV file has no header row.
	 * @param headers The header values. */
	public void setHeaders(String[] headers) {
		if (headers == null) {
			setHeaders(new String[0], 0);
		}
		else {
			setHeaders(headers, headers.length);
		}
	}
	
	private void setHeaders(String[] headers, int length) {
		headerIndex.clear();
		
		csvHeaders = new String[length];
		System.arraycopy(headers, 0, csvHeaders, 0, length);
		
		for (int i = 0; i < length; i++) {
			Integer previous = headerIndex.put(headers[i], Integer.valueOf(i));
			
			if (previous != null) {
				headerIndex.clear();
				csvHeaders = new String[0];
				throw new IllegalArgumentException("Found duplicate headers with name " + headers[i]);
			}
		}
	}
	
	/** Get all parsed column values for the current record.
	 * @return A copy of the current record's column values.
	 * @throws IOException Thrown if this CSVReader has already been closed. */
	public String[] getValues() throws IOException {
		checkClosed();
		
		// values.Length might be greater than columnsCount
		String[] clone = new String[columnsCount];
		System.arraycopy(values, 0, clone, 0, columnsCount);
		return clone;
	}
	
	/** Returns the current column value for a given column index.
	 * @param columnIndex The index of the column.
	 * @return The current column value.
	 * @exception IOException Thrown if this CSVReader has already been closed. */
	public String get(int columnIndex) throws IOException {
		checkClosed();
		return columnIndex > -1 && columnIndex < columnsCount ? values[columnIndex] : "";
	}
	
	/** Returns the current column value for a given column header name.
	 * @param headerName The header name of the column.
	 * @return The current column value.
	 * @exception IOException Thrown if this CSVReader has already been closed. */
	public String get(String headerName) throws IOException {
		checkClosed();
		return get(getIndex(headerName));
	}
	
	/** Gets the index of the current record.
	 * @return The index of the current record. */
	public long getCurrentRecord() {
		return currentRecord - 1;
	}
	
	/** Gets the current record in raw state, ignoring column parsing. The captureRawRecord option must be enabled when reading a record to use this feature.
	 * @return Raw record data.
	 * @throws IOException Thrown if this CSVReader has already been closed. */
	public String getRawRecord() throws IOException {
		checkClosed();
		
		String rawRecord;
		
		if (captureRawRecord) {
			rawRecord = new String(rawBuffer.buffer, 0, rawBuffer.position);
			
			// When hasMoreData == false, all data has already been copied to the raw buffer.
			if (hasMoreData && dataBuffer.position > dataBuffer.lineStart) {
				rawRecord += new String(dataBuffer.buffer, dataBuffer.lineStart, dataBuffer.position - dataBuffer.lineStart - 1);
			}
		}
		else {
			rawRecord = "";
		}
		
		return rawRecord;
	}
	
	/** Reads another record. Must be called before attempting to get any record data.
	 * @return Whether another record was successfully read or not.
	 * @exception IOException Thrown if an error occurs while reading data from the source stream. */
	public boolean readRecord() throws IOException {
		checkClosed();
		
		columnsCount = 0;
		rawBuffer.position = 0;
		
		dataBuffer.lineStart = dataBuffer.position;
		
		boolean hasReadNextLine = false;
		
		// check to see if we've already found the end of data
		if (hasMoreData) {
			// loop over the data stream until the end of data is found or the end of the record is found
			do {
				if (dataBuffer.position == dataBuffer.count) {
					readData();
				}
				else {
					startedWithQualifier = false;
					
					// grab the current letter as a char
					currentLetter = dataBuffer.buffer[dataBuffer.position];
					
					if (useTextQualifier && currentLetter == textQualifier) {
						// this will be a text qualified column, so we need to set startedWithQualifier
						// to make it enter the seperate branch to handle text qualified columns
						startedWithQualifier = true;
						boolean lastLetterWasQualifier = false;
						
						// read qualified
						startedColumn = true;
						dataBuffer.columnStart = dataBuffer.position + 1;
						
						lastLetter = currentLetter;
						
						char escapeChar = escapeMode == EscapeMode.BACKSLASH ? Letters.BACKSLASH : textQualifier;
						
						boolean eatingTrailingJunk = false;
						boolean lastLetterWasEscape = false;
						readingComplexEscape = false;
						escape = ComplexEscape.UNICODE;
						escapeLength = 0;
						escapeValue = Letters.NULL;
						
						dataBuffer.position++;
						
						do {
							if (dataBuffer.position == dataBuffer.count) {
								readData();
							}
							else {
								// grab the current letter as a char
								currentLetter = dataBuffer.buffer[dataBuffer.position];
								
								if (eatingTrailingJunk) {
									dataBuffer.columnStart = dataBuffer.position + 1;
									
									if (currentLetter == cellDelimiter) {
										endColumn();
									}
									else if (rowDelimiter.matches()) {
										endColumn();
										hasReadNextLine = true;
										currentRecord++;
									}
								}
								else if (readingComplexEscape) {
									handleComplexEscape();
								}
								else if (currentLetter == textQualifier) {
									if (lastLetterWasEscape) {
										lastLetterWasEscape = false;
										lastLetterWasQualifier = false;
									}
									else {
										updateCurrentValue();
										
										if (escapeMode == EscapeMode.DOUBLED) {
											lastLetterWasEscape = true;
										}
										
										lastLetterWasQualifier = true;
									}
								}
								else if (escapeMode == EscapeMode.BACKSLASH && lastLetterWasEscape) {
									handleEscapee();
									lastLetterWasEscape = false;
								}
								else if (currentLetter == escapeChar) {
									updateCurrentValue();
									lastLetterWasEscape = true;
								}
								else {
									if (lastLetterWasQualifier) {
										if (currentLetter == cellDelimiter) {
											endColumn();
										}
										else if (rowDelimiter.matches()) {
											endColumn();
											hasReadNextLine = true;
											currentRecord++;
										}
										else {
											dataBuffer.columnStart = dataBuffer.position + 1;
											eatingTrailingJunk = true;
										}
										
										// make sure to clear the flag for next run of the loop
										lastLetterWasQualifier = false;
									}
								}
								
								// keep track of the last letter because we need it for several key decisions
								lastLetter = currentLetter;
								
								if (startedColumn) {
									dataBuffer.position++;
									safetyLimit.test();
								}
							}
						} while (hasMoreData && startedColumn);
					}
					else if (currentLetter == cellDelimiter) {
						// we encountered a column with no data, so just send the end column
						lastLetter = currentLetter;
						endColumn();
					}
					else if (rowDelimiter.matches()) {
						// this will skip blank lines
						if (rowDelimiter.includeEmptyRecord()) {
							endColumn();
							hasReadNextLine = true;
							currentRecord++;
						}
						else {
							dataBuffer.lineStart = dataBuffer.position + 1;
						}
						
						lastLetter = currentLetter;
					}
					else if (useComments && columnsCount == 0 && currentLetter == comment) {
						// encountered a comment character at the beginning of the line so just ignore the rest of the line
						lastLetter = currentLetter;
						skipLine();
					}
					else if (trimWhitespace && Character.isWhitespace(currentLetter)) {
						// do nothing, this will trim leading whitespace for both text qualified columns and non
						startedColumn = true;
						dataBuffer.columnStart = dataBuffer.position + 1;
					}
					else {
						// since the letter wasn't a special letter, this will be the first letter of our current column
						
						startedColumn = true;
						dataBuffer.columnStart = dataBuffer.position;
						boolean lastLetterWasBackslash = false;
						readingComplexEscape = false;
						escape = ComplexEscape.UNICODE;
						escapeLength = 0;
						escapeValue = Letters.NULL;
						
						boolean firstLoop = true;
						
						do {
							if (!firstLoop && dataBuffer.position == dataBuffer.count) {
								readData();
							}
							else {
								if (!firstLoop) {
									// grab the current letter as a char
									currentLetter = dataBuffer.buffer[dataBuffer.position];
								}
								
								if (!useTextQualifier && escapeMode == EscapeMode.BACKSLASH && currentLetter == Letters.BACKSLASH) {
									if (lastLetterWasBackslash) {
										lastLetterWasBackslash = false;
									}
									else {
										updateCurrentValue();
										lastLetterWasBackslash = true;
									}
								}
								else if (readingComplexEscape) {
									handleComplexEscape();
								}
								else if (escapeMode == EscapeMode.BACKSLASH && lastLetterWasBackslash) {
									handleEscapee();
									lastLetterWasBackslash = false;
								}
								else if (currentLetter == cellDelimiter) {
									endColumn();
								}
								else if (rowDelimiter.matches()) {
									endColumn();
									hasReadNextLine = true;
									currentRecord++;
								}
								
								// keep track of the last letter because we need it for several key decisions
								lastLetter = currentLetter;
								firstLoop = false;
								
								if (startedColumn) {
									dataBuffer.position++;
									safetyLimit.test();
								}
							}
						} while (hasMoreData && startedColumn);
					}
					
					if (hasMoreData) {
						dataBuffer.position++;
					}
				}
			} while (hasMoreData && !hasReadNextLine);
			
			// check to see if we hit the end of the file without processing the current record
			if (startedColumn || lastLetter == cellDelimiter) {
				endColumn();
				hasReadNextLine = true;
				currentRecord++;
			}
		}
		
		return hasReadNextLine;
	}
	
	private void handleComplexEscape() {
		escapeLength++;
		
		switch (escape) {
			case ComplexEscape.UNICODE:
				escapeValue *= (char) 16;
				escapeValue += hexToDec(currentLetter);
				
				if (escapeLength == 4) {
					readingComplexEscape = false;
				}
				
				break;
			case ComplexEscape.OCTAL:
				escapeValue *= (char) 8;
				escapeValue += (char) (currentLetter - '0');
				
				if (escapeLength == 3) {
					readingComplexEscape = false;
				}
				
				break;
			case ComplexEscape.DECIMAL:
				escapeValue *= (char) 10;
				escapeValue += (char) (currentLetter - '0');
				
				if (escapeLength == 3) {
					readingComplexEscape = false;
				}
				
				break;
			case ComplexEscape.HEX:
				escapeValue *= (char) 16;
				escapeValue += hexToDec(currentLetter);
				
				if (escapeLength == 2) {
					readingComplexEscape = false;
				}
				
				break;
			default:
				break;
		}
		
		if (!readingComplexEscape) {
			appendEscapedChar(escapeValue);
		}
		else {
			dataBuffer.columnStart = dataBuffer.position + 1;
		}
	}
	
	private void handleEscapee() {
		switch (currentLetter) {
			case 'n':
				appendEscapedChar(Letters.LF);
				break;
			case 'r':
				appendEscapedChar(Letters.CR);
				break;
			case 't':
				appendEscapedChar(Letters.TAB);
				break;
			case 'b':
				appendEscapedChar(Letters.BACKSPACE);
				break;
			case 'f':
				appendEscapedChar(Letters.FORM_FEED);
				break;
			case 'e':
				appendEscapedChar(Letters.ESCAPE);
				break;
			case 'v':
				appendEscapedChar(Letters.VERTICAL_TAB);
				break;
			case 'a':
				appendEscapedChar(Letters.ALERT);
				break;
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
				escape = ComplexEscape.OCTAL;
				readingComplexEscape = true;
				escapeLength = 1;
				escapeValue = (char) (currentLetter - '0');
				dataBuffer.columnStart = dataBuffer.position + 1;
				break;
			case 'u':
			case 'U':
				escape = ComplexEscape.UNICODE;
				readingComplexEscape = true;
				escapeLength = 0;
				escapeValue = (char) 0;
				dataBuffer.columnStart = dataBuffer.position + 1;
				break;
			case 'x':
			case 'X':
				escape = ComplexEscape.HEX;
				readingComplexEscape = true;
				escapeLength = 0;
				escapeValue = (char) 0;
				dataBuffer.columnStart = dataBuffer.position + 1;
				break;
			case 'o':
			case 'O':
				escape = ComplexEscape.OCTAL;
				readingComplexEscape = true;
				escapeLength = 0;
				escapeValue = (char) 0;
				dataBuffer.columnStart = dataBuffer.position + 1;
				break;
			case 'd':
			case 'D':
				escape = ComplexEscape.DECIMAL;
				readingComplexEscape = true;
				escapeLength = 0;
				escapeValue = (char) 0;
				dataBuffer.columnStart = dataBuffer.position + 1;
				break;
			default:
				break;
		}
	}
	
	private void readData() throws IOException {
		updateCurrentValue();
		
		if (captureRawRecord && dataBuffer.count > 0) {
			rawBuffer.append(dataBuffer, dataBuffer.lineStart, dataBuffer.count);
		}
		
		try {
			dataBuffer.count = reader.read(dataBuffer.buffer, 0, dataBuffer.buffer.length);
		}
		catch (IOException ex) {
			close();
			throw ex;
		}
		
		// Reader.read(...) count of -1 indicates end of stream, set our flag to match.
		if (dataBuffer.count == -1) {
			hasMoreData = false;
		}
		
		dataBuffer.position = 0;
		dataBuffer.lineStart = 0;
		dataBuffer.columnStart = 0;
	}
	
	/** Read the first record of data as column headers.
	 * @return Whether the header record was successfully read or not.
	 * @exception IOException Thrown if an error occurs while reading data from the source stream. */
	public boolean readHeaders() throws IOException {
		boolean result = readRecord();
		
		setHeaders(values, columnsCount);
		
		if (result) {
			currentRecord--;
		}
		
		columnsCount = 0;
		
		return result;
	}
	
	/** Returns the column header value for a given column index.
	 * @param columnIndex The index of the header column being requested.
	 * @return The value of the column header at the given column index.
	 * @exception IOException Thrown if this CSVReader has already been closed. */
	public String getHeader(int columnIndex) throws IOException {
		checkClosed();
		return columnIndex > -1 && columnIndex < csvHeaders.length ? csvHeaders[columnIndex] : "";
	}
	
	/** Returns a flag indicating if the column was text qualified in the current record.
	 * @param columnIndex The index of the column to be checked.
	 * @return TRUE when the column was parsed as text qualified data.
	 * @exception IOException Thrown if this CSVReader has already been closed.  */
	public boolean isQualified(int columnIndex) throws IOException {
		checkClosed();
		return columnIndex < columnsCount && columnIndex > -1 ? isQualified[columnIndex] : false;
	}
	
	private void endColumn() throws IOException {
		String currentValue;
		
		if (startedColumn) {
			if (columnBuffer.position == 0) {
				if (dataBuffer.columnStart < dataBuffer.position) {
					int lastLetter = dataBuffer.position - 1;
					
					if (trimWhitespace && !startedWithQualifier) {
						while (lastLetter >= dataBuffer.columnStart && Character.isWhitespace(dataBuffer.buffer[lastLetter])) {
							lastLetter--;
						}
					}
					
					currentValue = new String(dataBuffer.buffer, dataBuffer.columnStart, lastLetter - dataBuffer.columnStart + 1);
				}
				else {
					currentValue = "";
				}
			}
			else {
				updateCurrentValue();
				
				int lastLetter = columnBuffer.position - 1;
				
				if (trimWhitespace && !startedWithQualifier) {
					while (lastLetter >= 0 && Character.isWhitespace(columnBuffer.buffer[lastLetter])) {
						lastLetter--;
					}
				}
				
				currentValue = new String(columnBuffer.buffer, 0, lastLetter + 1);
			}
		}
		else {
			currentValue = "";
		}
		
		columnBuffer.position = 0;
		startedColumn = false;
		
		safetyLimit.test();
		
		// check to see if our current holder array for column chunks is still big enough to handle another column chunk
		if (columnsCount == values.length) {
			int newLength = values.length * 2;
			
			String[] holder = new String[newLength];
			System.arraycopy(values, 0, holder, 0, values.length);
			values = holder;
			
			boolean[] qualifiedHolder = new boolean[newLength];
			System.arraycopy(isQualified, 0, qualifiedHolder, 0, isQualified.length);
			isQualified = qualifiedHolder;
		}
		
		values[columnsCount] = currentValue;
		isQualified[columnsCount] = startedWithQualifier;
		columnsCount++;
	}
	
	private void appendEscapedChar(char letter) {
		columnBuffer.append(letter);
		dataBuffer.columnStart = dataBuffer.position + 1;
	}
	
	private void updateCurrentValue() {
		if (startedColumn && dataBuffer.columnStart < dataBuffer.position) {
			columnBuffer.append(dataBuffer, dataBuffer.columnStart, dataBuffer.position);
		}
		
		dataBuffer.columnStart = dataBuffer.position + 1;
	}
	
	/** Gets the corresponding column index for a given column header name.
	 * @param headerName The header name of the column.
	 * @return The column index for the given column header name. Returns -1 if not found.
	 * @exception IOException Thrown if this CSVReader has already been closed. */
	public int getIndex(String headerName) throws IOException {
		checkClosed();
		
		Integer indexValue = headerIndex.get(headerName);
		
		if (indexValue != null) {
			return indexValue.intValue();
		}
		
		return -1;
	}
	
	/** Skips the next record by accurate parsing method. Does not increment record count returned by {@link com.nunn.yacsv.CsvReader#getCurrentRecord getCurrentRecord()}.
	 * @return Whether a record was skipped.
	 * @exception IOException Thrown if an error occurs while reading data from the source stream. */
	public boolean skipRecord() throws IOException {
		checkClosed();
		
		boolean recordRead = false;
		
		if (hasMoreData) {
			recordRead = readRecord();
			
			if (recordRead) {
				currentRecord--;
			}
		}
		
		return recordRead;
	}
	
	/** Skips the next line of data using the standard end of line characters and does not do any column delimited parsing.
	 * @return Whether a line was successfully skipped or not.
	 * @exception IOException Thrown if this CSVReader has already been closed, or if an error occurs while reading data from the source stream. */
	public boolean skipLine() throws IOException {
		checkClosed();
		
		// clear public column values for current line
		columnsCount = 0;
		
		boolean skippedLine = false;
		
		if (hasMoreData) {
			boolean foundEol = false;
			
			do {
				if (dataBuffer.position == dataBuffer.count) {
					readData();
				}
				else {
					skippedLine = true;
					
					// grab the current letter as a char
					currentLetter = dataBuffer.buffer[dataBuffer.position];
					
					if (currentLetter == Letters.CR || currentLetter == Letters.LF) {
						foundEol = true;
					}
					
					// keep track of the last letter because we need it for several key decisions
					lastLetter = currentLetter;
					
					if (!foundEol) {
						dataBuffer.position++;
					}
				}
			} while (hasMoreData && !foundEol);
			
			columnBuffer.position = 0;
			
			dataBuffer.lineStart = dataBuffer.position + 1;
		}
		
		rawBuffer.position = 0;
		
		return skippedLine;
	}
	
	@Override // implements AutoCloseable
	public void close() {
		close(true);
	}
	
	/** Closes and releases related resources, optionally closing underlying reader.
	 * @param closeReader Close the underlying input reader. */
	public void close(boolean closeReader) {
		if ( ! closed) {
			dataBuffer = null;
			columnBuffer = null;
			rawBuffer = null;
			
			if (reader != null) {
				if (closeReader) {
					try {
						reader.close();
					}
					catch (Exception e) {
						// eat the exception
					}
				}
				reader = null;
			}
			
			closed = true;
		}
	}
	
	private void checkClosed() throws IOException {
		if (closed) {
			throw new IOException("This instance of the CsvReader class has already been closed.");
		}
	}
	
	private char hexToDec(char hex) {
		return (char) (hex >= 'a' ? hex - 'a' + 10 : hex >= 'A' ? hex - 'A' + 10 : hex - '0');
	}

	@Override // implements Iterator<String[]>
	public boolean hasNext() {
		if (iteratorReadStatus == null) {
			try {
				iteratorReadStatus = readRecord();
			}
			catch (Exception e) {
				// eat the exception
				iteratorReadStatus = false;
			}
		}
		return iteratorReadStatus;
	}

	@Override // implements Iterator<String[]>
	public String[] next() {
		if (hasNext()) {
			iteratorReadStatus = null;
			try {
				return getValues();
			}
			catch (IOException e) {
				// eat the exception
			}
		}
		throw new NoSuchElementException("No next record.");
	}
	
}
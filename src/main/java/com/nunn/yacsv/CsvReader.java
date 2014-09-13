/*
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
import java.util.Map;

/** A stream based parser for parsing delimited text data from a file or a stream. */
public class CsvReader implements AutoCloseable {
	
	private Reader			reader			= null;
	private boolean			closed			= false;
	
	// this will be our working buffer to hold data chunks read in from the data file
	private DataBuffer		dataBuffer		= new DataBuffer(1024);		// MAX_BUFFER_SIZE
	private Buffer			columnBuffer	= new Buffer(50);			// INITIAL_COLUMN_BUFFER_SIZE
	private Buffer			rawBuffer		= new Buffer(500);			// INITIAL_COLUMN_BUFFER_SIZE * INITIAL_COLUMN_COUNT
	
	// these are all more or less global loop variables to keep from needing to pass them all into various methods during parsing
	private boolean					startedColumn				= false;
	private boolean					startedWithQualifier		= false;
	private boolean					hasMoreData					= true;
	private int						columnsCount				= 0;
	private long					currentRecord				= 0;
	private String[]				values						= new String[10];			// INITIAL_COLUMN_COUNT
	private boolean[]				isQualified					= new boolean[10];			// INITIAL_COLUMN_COUNT
	private String[]				csvHeaders					= {};
	private Map<String, Integer>	headerIndex					= new HashMap<String, Integer>();
	private char					lastLetter;
	private char					currentLetter;
	
	// these are all the values for switches that the user is may set
	public char				textQualifier				= Letters.QUOTE;
	public boolean			trimWhitespace				= true;
	public boolean			useTextQualifier			= true;
	public char				cellDelimiter				= Letters.COMMA;
	public RowDelimiter		rowDelimiter				= new RowDelimiter(Letters.CR, Letters.LF);
	public char				comment						= Letters.POUND;
	public boolean			useComments					= false;
	public EscapeMode		escapeMode					= CsvReader.EscapeMode.DOUBLED;
	public SafetyLimiter	safetyLimit					= new SafetyLimiter();
	public boolean			skipEmptyRecords			= true;
	public boolean			captureRawRecord			= false;
	
	private class Buffer {
		public char[]	buffer;
		public int		position = 0;
		
		public Buffer(int size) {
			buffer = new char[size];
		}
		
		public void expand(int newLength) {
			char[] temp = new char[newLength];
			System.arraycopy(buffer, 0, temp, 0, position);
			buffer = temp;
		}
	}
	
	private class DataBuffer extends Buffer {
		/** How much usable data has been read into the stream, which will not always be as long as Buffer.Length. */
		public int		count			= 0;
		/** The position of the cursor in the buffer when the current column was started or the last time data was moved out to the column buffer. */
		public int		columnStart		= 0;
		public int		lineStart		= 0;
		
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
	
	private class ComplexEscape {
		private static final int	UNICODE	= 1;
		private static final int	OCTAL	= 2;
		private static final int	DECIMAL	= 3;
		private static final int	HEX		= 4;
	}
	
	private class Letters {
		public static final char	LF				= '\n';
		public static final char	CR				= '\r';
		public static final char	QUOTE			= '"';
		public static final char	COMMA			= ',';
		public static final char	SPACE			= ' ';
		public static final char	TAB				= '\t';
		public static final char	POUND			= '#';
		public static final char	BACKSLASH		= '\\';
		public static final char	NULL			= '\0';
		public static final char	BACKSPACE		= '\b';
		public static final char	FORM_FEED		= '\f';
		public static final char	ESCAPE			= '\u001B'; // ASCII/ANSI escape
		public static final char	VERTICAL_TAB	= '\u000B';
		public static final char	ALERT			= '\u0007';
	}
	
	public static enum EscapeMode {
		/** Double up the text qualifier to represent an occurance of the text qualifier. */
		DOUBLED,
		/** Use a backslash character before the text qualifier to represent an occurance of the text qualifier. */
		BACKSLASH;
	}
	
	/** Constructs a {@link com.csvreader.CsvReader CsvReader} object using a {@link java.io.Reader Reader} object as the data source.
	 * 
	 * @param inputStream The stream to use as the data source. */
	public CsvReader(Reader inputReader) {
		if (inputReader == null) {
			throw new IllegalArgumentException("Parameter inputReader can not be null.");
		}
		reader = inputReader;
	}
	
	/** Constructs a {@link com.csvreader.CsvReader CsvReader} object using an {@link java.io.InputStream InputStream} object as the data source.
	 * 
	 * @param inputStream The stream to use as the data source.
	 * @param charset The {@link java.nio.charset.Charset Charset} to use while parsing the data. */
	public CsvReader(InputStream inputStream, Charset charset) {
		this(newReader(inputStream, charset));
	}
	
	/** Constructs a {@link com.csvreader.CsvReader CsvReader} object using a {@link java.io.file.Path Path} object as the data source.
	 * 
	 * @param path The path to use as the data source.
	 * @param charset The {@link java.nio.charset.Charset Charset} to use while parsing the data. */
	public CsvReader(Path path, Charset charset) {
		this(newReader(path, charset));
	}
	
	/** Constructs a {@link com.csvreader.CsvReader CsvReader} object using the named file as the data source.
	 * 
	 * @param fileName The name of the file to use as the data source.
	 * @param charset The {@link java.nio.charset.Charset Charset} to use while parsing the data. */
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
	
	public boolean getCaptureRawRecord() {
		return captureRawRecord;
	}
	
	public void setCaptureRawRecord(boolean captureRawRecord) {
		this.captureRawRecord = captureRawRecord;
	}
	
	public String getRawRecord() throws IOException {
		return buildRawRecord();
	}
	
	/** Gets whether leading and trailing whitespace characters are being trimmed from non-textqualified column data. Default is true.
	 * 
	 * @return Whether leading and trailing whitespace characters are being trimmed from non-textqualified column data. */
	public boolean getTrimWhitespace() {
		return trimWhitespace;
	}
	
	/** Sets whether leading and trailing whitespace characters should be trimmed from non-textqualified column data or not. Default is true.
	 * 
	 * @param trimWhitespace Whether leading and trailing whitespace characters should be trimmed from non-textqualified column data or not. */
	public void setTrimWhitespace(boolean trimWhitespace) {
		this.trimWhitespace = trimWhitespace;
	}
	
	/** Gets the character being used as the column delimiter. Default is comma, ','.
	 * 
	 * @return The character being used as the column delimiter. */
	public char getDelimiter() {
		return cellDelimiter;
	}
	
	/** Sets the character to use as the column delimiter. Default is comma, ','.
	 * 
	 * @param delimiter The character to use as the column delimiter. */
	public void setDelimiter(char delimiter) {
		cellDelimiter = delimiter;
	}
	
	/** Returns the current single record delimiter, or pair of delimiters (typically for "\r\n" usage). */
	public char[] getRecordDelimiter() {
		return rowDelimiter.getDelimiter();
	}
	
	/** Sets the character to use as the record delimiter.
	 * 
	 * @param recordDelimiter The character to use as the record delimiter. Default is combination of standard end of line characters for Windows, Unix, or Mac. */
	public void setRecordDelimiter(char delimiterOne) {
		rowDelimiter = new RowDelimiterSingleChar(delimiterOne);
	}
	
	/** Sets the characters to use as the record delimiter, e.g \r\n for Windows line breaks.
	 * 
	 * @param recordDelimiter The characters to use as the record delimiter. Default is combination of standard end of line characters for Windows, Unix, or Mac. */
	public void setRecordDelimiter(char delimiterOne, char delimiterTwo) {
		rowDelimiter = new RowDelimiter(delimiterOne, delimiterTwo);
	}
	
	/** Gets the character to use as a text qualifier in the data.
	 * 
	 * @return The character to use as a text qualifier in the data. */
	public char getTextQualifier() {
		return textQualifier;
	}
	
	/** Sets the character to use as a text qualifier in the data.
	 * 
	 * @param textQualifier The character to use as a text qualifier in the data. */
	public void setTextQualifier(char textQualifier) {
		this.textQualifier = textQualifier;
	}
	
	/** Whether text qualifiers will be used while parsing or not.
	 * 
	 * @return Whether text qualifiers will be used while parsing or not. */
	public boolean getUseTextQualifier() {
		return useTextQualifier;
	}
	
	/** Sets whether text qualifiers will be used while parsing or not.
	 * 
	 * @param useTextQualifier */
	public void setUseTextQualifier(boolean useTextQualifier) {
		this.useTextQualifier = useTextQualifier;
	}
	
	/** Gets the character being used as a comment signal.
	 * 
	 * @return The character being used as a comment signal. */
	public char getComment() {
		return comment;
	}
	
	/** Sets the character to use as a comment signal.
	 * 
	 * @param comment The character to use as a comment signal. */
	public void setComment(char comment) {
		this.comment = comment;
	}
	
	/** Gets whether comments are being looked for while parsing or not.
	 * 
	 * @return Whether comments are being looked for while parsing or not. */
	public boolean getUseComments() {
		return useComments;
	}
	
	/** Sets whether comments are being looked for while parsing or not.
	 * 
	 * @param useComments Whether comments are being looked for while parsing or not. */
	public void setUseComments(boolean useComments) {
		this.useComments = useComments;
	}
	
	/** Gets the current way to escape an occurance of the text qualifier inside qualified data.
	 * 
	 * @return The current way to escape an occurance of the text qualifier inside qualified data. */
	public EscapeMode getEscapeMode() {
		return escapeMode;
	}
	
	/** Sets the current way to escape an occurance of the text qualifier inside qualified data.
	 * 
	 * @param escapeMode The way to escape an occurance of the text qualifier inside qualified data.
	 * @exception IllegalArgumentException When an illegal value is specified for escapeMode. */
	public void setEscapeMode(EscapeMode escapeMode) throws IllegalArgumentException {
		this.escapeMode = escapeMode;
	}
	
	public boolean getSkipEmptyRecords() {
		return skipEmptyRecords;
	}
	
	public void setSkipEmptyRecords(boolean skipEmptyRecords) {
		this.skipEmptyRecords = skipEmptyRecords;
	}
	
	/** Safety caution to prevent the parser from using large amounts of memory in the case where parsing settings like file encodings don't end up matching the actual format of a file. This switch can be turned off if the file format is known and tested. With the switch off, the max column lengths and max column count per record supported by the parser will greatly increase. Default is true.
	 * 
	 * @return The current setting of the safety switch. */
	public boolean getSafetySwitch() {
		return safetyLimit.getClass().equals(SafetyLimiter.class);
	}
	
	/** Safety caution to prevent the parser from using large amounts of memory in the case where parsing settings like file encodings don't end up matching the actual format of a file. This switch can be turned off if the file format is known and tested. With the switch off, the max column lengths and max column count per record supported by the parser will greatly increase. Default is true.
	 * 
	 * @param safetySwitch */
	public void setSafetySwitch(boolean safetySwitch) {
		if (safetySwitch) {
			safetyLimit = new SafetyLimiter();
		}
		else {
			safetyLimit = new SafetyLimiterNoOp();
		}
	}
	
	/** Gets the count of columns found in this record.
	 * 
	 * @return The count of columns found in this record. */
	public int getColumnCount() {
		return columnsCount;
	}
	
	/** Gets the index of the current record.
	 * 
	 * @return The index of the current record. */
	public long getCurrentRecord() {
		return currentRecord - 1;
	}
	
	/** Gets the count of headers read in by a previous call to {@link com.csvreader.CsvReader#readHeaders readHeaders()}.
	 * 
	 * @return The count of headers read in by a previous call to {@link com.csvreader.CsvReader#readHeaders readHeaders()}. */
	public int getHeaderCount() {
		return csvHeaders.length;
	}
	
	/** Returns the header values as a string array.
	 * 
	 * @return The header values as a String array.
	 * @exception IOException Thrown if this object has already been closed. */
	public String[] getHeaders() throws IOException {
		checkClosed();
		
		String[] clone = new String[csvHeaders.length];
		System.arraycopy(csvHeaders, 0, clone, 0, csvHeaders.length);
		return clone;
	}
	
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
	
	public String[] getValues() throws IOException {
		checkClosed();
		
		// values.Length might be greater than columnsCount
		String[] clone = new String[columnsCount];
		System.arraycopy(values, 0, clone, 0, columnsCount);
		return clone;
	}
	
	/** Returns the current column value for a given column index.
	 * 
	 * @param columnIndex The index of the column.
	 * @return The current column value.
	 * @exception IOException Thrown if this object has already been closed. */
	public String get(int columnIndex) throws IOException {
		checkClosed();
		return columnIndex > -1 && columnIndex < columnsCount ? values[columnIndex] : "";
	}
	
	/** Returns the current column value for a given column header name.
	 * 
	 * @param headerName The header name of the column.
	 * @return The current column value.
	 * @exception IOException Thrown if this object has already been closed. */
	public String get(String headerName) throws IOException {
		checkClosed();
		return get(getIndex(headerName));
	}
	
	/** Creates a {@link com.csvreader.CsvReader CsvReader} object using a string of data as the source.&nbsp;Uses ISO-8859-1 as the {@link java.nio.charset.Charset Charset}.
	 * 
	 * @param data The String of data to use as the source.
	 * @return A {@link com.csvreader.CsvReader CsvReader} object using the String of data as the source. */
	public static CsvReader parse(String data) {
		if (data == null) {
			throw new IllegalArgumentException("Parameter data can not be null.");
		}
		return new CsvReader(new StringReader(data));
	}
	
	/** Reads another record.
	 * 
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
						boolean readingComplexEscape = false;
						int escape = ComplexEscape.UNICODE;
						int escapeLength = 0;
						char escapeValue = Letters.NULL;
						
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
									}
									
									if (!readingComplexEscape) {
										appendLetter(escapeValue);
									}
									else {
										dataBuffer.columnStart = dataBuffer.position + 1;
									}
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
									switch (currentLetter) {
										case 'n':
											appendLetter(Letters.LF);
											break;
										case 'r':
											appendLetter(Letters.CR);
											break;
										case 't':
											appendLetter(Letters.TAB);
											break;
										case 'b':
											appendLetter(Letters.BACKSPACE);
											break;
										case 'f':
											appendLetter(Letters.FORM_FEED);
											break;
										case 'e':
											appendLetter(Letters.ESCAPE);
											break;
										case 'v':
											appendLetter(Letters.VERTICAL_TAB);
											break;
										case 'a':
											appendLetter(Letters.ALERT);
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
										case 'x':
										case 'o':
										case 'd':
										case 'U':
										case 'X':
										case 'O':
										case 'D':
											switch (currentLetter) {
												case 'u':
												case 'U':
													escape = ComplexEscape.UNICODE;
													break;
												case 'x':
												case 'X':
													escape = ComplexEscape.HEX;
													break;
												case 'o':
												case 'O':
													escape = ComplexEscape.OCTAL;
													break;
												case 'd':
												case 'D':
													escape = ComplexEscape.DECIMAL;
													break;
											}
											
											readingComplexEscape = true;
											escapeLength = 0;
											escapeValue = (char) 0;
											dataBuffer.columnStart = dataBuffer.position + 1;
											
											break;
										default:
											break;
									}
									
									lastLetterWasEscape = false;
									// can only happen for ESCAPE_MODE_BACKSLASH
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
					else if (trimWhitespace && (currentLetter == Letters.SPACE || currentLetter == Letters.TAB)) {
						// do nothing, this will trim leading whitespace for both text qualified columns and non
						startedColumn = true;
						dataBuffer.columnStart = dataBuffer.position + 1;
					}
					else {
						// since the letter wasn't a special letter, this will be the first letter of our current column
						
						startedColumn = true;
						dataBuffer.columnStart = dataBuffer.position;
						boolean lastLetterWasBackslash = false;
						boolean readingComplexEscape = false;
						int escape = ComplexEscape.UNICODE;
						int escapeLength = 0;
						char escapeValue = Letters.NULL;
						
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
									}
									
									if (!readingComplexEscape) {
										appendLetter(escapeValue);
									}
									else {
										dataBuffer.columnStart = dataBuffer.position + 1;
									}
								}
								else if (escapeMode == EscapeMode.BACKSLASH && lastLetterWasBackslash) {
									switch (currentLetter) {
										case 'n':
											appendLetter(Letters.LF);
											break;
										case 'r':
											appendLetter(Letters.CR);
											break;
										case 't':
											appendLetter(Letters.TAB);
											break;
										case 'b':
											appendLetter(Letters.BACKSPACE);
											break;
										case 'f':
											appendLetter(Letters.FORM_FEED);
											break;
										case 'e':
											appendLetter(Letters.ESCAPE);
											break;
										case 'v':
											appendLetter(Letters.VERTICAL_TAB);
											break;
										case 'a':
											appendLetter(Letters.ALERT);
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
										case 'x':
										case 'o':
										case 'd':
										case 'U':
										case 'X':
										case 'O':
										case 'D':
											switch (currentLetter) {
												case 'u':
												case 'U':
													escape = ComplexEscape.UNICODE;
													break;
												case 'x':
												case 'X':
													escape = ComplexEscape.HEX;
													break;
												case 'o':
												case 'O':
													escape = ComplexEscape.OCTAL;
													break;
												case 'd':
												case 'D':
													escape = ComplexEscape.DECIMAL;
													break;
											}
											
											readingComplexEscape = true;
											escapeLength = 0;
											escapeValue = (char) 0;
											dataBuffer.columnStart = dataBuffer.position + 1;
											
											break;
										default:
											break;
									}
									
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
	
	private String buildRawRecord() throws IOException {
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
	
	/** @exception IOException Thrown if an error occurs while reading data from the source stream. */
	private void readData() throws IOException {
		updateCurrentValue();
		
		if (captureRawRecord && dataBuffer.count > 0) {
			bufferToBuffer(dataBuffer, dataBuffer.lineStart, dataBuffer.count, rawBuffer);
		}
		
		try {
			dataBuffer.count = reader.read(dataBuffer.buffer, 0, dataBuffer.buffer.length);
		}
		catch (IOException ex) {
			close();
			throw ex;
		}
		
		// if no more data could be found, set flag stating that the end of the data was found
		if (dataBuffer.count == -1) {
			hasMoreData = false;
		}
		
		dataBuffer.position = 0;
		dataBuffer.lineStart = 0;
		dataBuffer.columnStart = 0;
	}
	
	/** Read the first record of data as column headers.
	 * 
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
	 * 
	 * @param columnIndex The index of the header column being requested.
	 * @return The value of the column header at the given column index.
	 * @exception IOException Thrown if this object has already been closed. */
	public String getHeader(int columnIndex) throws IOException {
		checkClosed();
		return columnIndex > -1 && columnIndex < csvHeaders.length ? csvHeaders[columnIndex] : "";
	}
	
	public boolean isQualified(int columnIndex) throws IOException {
		checkClosed();
		return columnIndex < columnsCount && columnIndex > -1 ? isQualified[columnIndex] : false;
	}
	
	/** @exception IOException Thrown if a very rare extreme exception occurs during parsing, normally resulting from improper data format. */
	private void endColumn() throws IOException {
		String currentValue;
		
		if (startedColumn) {
			if (columnBuffer.position == 0) {
				if (dataBuffer.columnStart < dataBuffer.position) {
					int lastLetter = dataBuffer.position - 1;
					
					if (trimWhitespace && !startedWithQualifier) {
						while (lastLetter >= dataBuffer.columnStart && (dataBuffer.buffer[lastLetter] == Letters.SPACE || dataBuffer.buffer[lastLetter] == Letters.TAB)) {
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
					while (lastLetter >= 0 && (columnBuffer.buffer[lastLetter] == Letters.SPACE || columnBuffer.buffer[lastLetter] == Letters.TAB)) {
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
	
	private void appendLetter(char letter) {
		if (columnBuffer.position == columnBuffer.buffer.length) {
			columnBuffer.expand(columnBuffer.buffer.length * 2);
		}
		
		columnBuffer.buffer[columnBuffer.position] = letter;
		columnBuffer.position++;
		dataBuffer.columnStart = dataBuffer.position + 1;
	}
	
	private void updateCurrentValue() {
		if (startedColumn && dataBuffer.columnStart < dataBuffer.position) {
			bufferToBuffer(dataBuffer, dataBuffer.columnStart, dataBuffer.position, columnBuffer);
		}
		
		dataBuffer.columnStart = dataBuffer.position + 1;
	}
	
	private void bufferToBuffer(Buffer source, int sourceStart, int sourceEnd, Buffer target) {
		int nextReadLength = sourceEnd - sourceStart;
		
		if (target.buffer.length - target.position < nextReadLength) {
			target.expand(target.buffer.length + Math.max(nextReadLength, target.buffer.length));
		}
		
		System.arraycopy(source.buffer, sourceStart, target.buffer, target.position, nextReadLength);
		
		target.position += nextReadLength;
	}
	
	/** Gets the corresponding column index for a given column header name.
	 * 
	 * @param headerName The header name of the column.
	 * @return The column index for the given column header name.&nbsp;Returns -1 if not found.
	 * @exception IOException Thrown if this object has already been closed. */
	public int getIndex(String headerName) throws IOException {
		checkClosed();
		
		Integer indexValue = headerIndex.get(headerName);
		
		if (indexValue != null) {
			return indexValue.intValue();
		}
		
		return -1;
	}
	
	/** Skips the next record of data by parsing each column.&nbsp;Does not increment {@link com.csvreader.CsvReader#getCurrentRecord getCurrentRecord()}.
	 * 
	 * @return Whether another record was successfully skipped or not.
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
	 * 
	 * @return Whether a line was successfully skipped or not.
	 * @exception IOException Thrown if an error occurs while reading data from the source stream. */
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
	
	/** Closes and releases all related resources. */
	@Override
	public void close() throws Exception {
		close(true);
	}
	
	/** Closes and releases all related resources. */
	public void close(boolean closeInputStream) {
		if ( ! closed) {
			dataBuffer = null;
			columnBuffer = null;
			rawBuffer = null;
			
			if (reader != null) {
				if (closeInputStream) {
					try {
						reader.close();
					}
					catch (Exception e) {
						// just eat the exception
					}
				}
				reader = null;
			}
			
			closed = true;
		}
	}
	
	/** @exception IOException Thrown if this object has already been closed. */
	private void checkClosed() throws IOException {
		if (closed) {
			throw new IOException("This instance of the CsvReader class has already been closed.");
		}
	}
	
	private static char hexToDec(char hex) {
		return (char) (hex >= 'a' ? hex - 'a' + 10 : hex >= 'A' ? hex - 'A' + 10 : hex - '0');
	}
	
}
/* Yet Another CSV Writer. Programmed by Rob Nunn.
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

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.nunn.yacsv.CsvReader.EscapeMode;
import com.nunn.yacsv.CsvReader.Letters;

/** A stream based writer for writing delimited text data to a file or a stream. */
public class CsvWriter implements AutoCloseable {
	
	private Writer writer = null;
	private boolean firstColumn = true;
	private boolean closed = false;
	
	/** Configuration accessor - getters and setters for CsvReader behaviour options are exposed here. */
	public final Config config = new Config();
	
	public char textQualifier = Letters.QUOTE;
	public boolean useTextQualifier = true;
	public char delimiter = Letters.COMMA;
	public String recordDelimiter = "\r\n";
	public char commentChar = Letters.POUND;
	public EscapeMode escapeMode = EscapeMode.DOUBLED;
	public boolean forceQualifier = false;
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using a file as the data destination.
	 * @param fileName The path to the file to output the data.
	 * @param delimiter The character to use as the column delimiter.
	 * @param charset The {@link java.nio.charset.Charset Charset} to use while writing the data. 
	 * @throws FileNotFoundException */
	public CsvWriter(String fileName, char delimiter, Charset charset) throws FileNotFoundException {
		if (fileName == null) {
			throw new IllegalArgumentException("Parameter fileName can not be null.");
		}
		if (charset == null) {
			throw new IllegalArgumentException("Parameter charset can not be null.");
		}
		this.writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), charset));
		this.delimiter = delimiter;
	}
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using a file as the data destination.
	 * Uses a comma as the column delimiter and UTF-8 as the {@link java.nio.charset.Charset Charset}.
	 * @param fileName The path to the file to output the data. 
	 * @throws FileNotFoundException */
	public CsvWriter(String fileName) throws FileNotFoundException {
		this(fileName, Letters.COMMA, StandardCharsets.UTF_8);
	}
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using a Writer to write data to. Uses a comma as the column delimiter.
	 * @param writer The stream to write the column delimited data to. */
	public CsvWriter(Writer outputStream) {
		this(outputStream, Letters.COMMA);
	}
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using a Writer to write data to.
	 * @param writer The stream to write the column delimited data to.
	 * @param delimiter The character to use as the column delimiter. */
	public CsvWriter(Writer outputStream, char delimiter) {
		if (outputStream == null) {
			throw new IllegalArgumentException("Parameter writer can not be null.");
		}
		this.writer = outputStream;
		this.delimiter = delimiter;
	}
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using an OutputStream to write data to.
	 * @param writer The stream to write the column delimited data to.
	 * @param delimiter The character to use as the column delimiter.
	 * @param charset The {@link java.nio.charset.Charset Charset} to use while writing the data. */
	public CsvWriter(OutputStream outputStream, char delimiter, Charset charset) {
		this(new OutputStreamWriter(outputStream, charset), delimiter);
	}
	
	public class Config {
		
		private Config(){}
		
		/** Gets the character being used as the column delimiter.
		 * @return The character being used as the column delimiter. */
		public char getDelimiter() {
			return delimiter;
		}
		
		/** Sets the character to use as the column delimiter.
		 * @param newDelimiter The character to use as the column delimiter. */
		public void setDelimiter(char newDelimiter) {
			delimiter = newDelimiter;
		}
		
		public String getRecordDelimiter() {
			return recordDelimiter;
		}
		
		/** Sets the character to use as the record delimiter.
		 * @param newRecordDelimiter The character to use as the record delimiter.
		 * Default is Windows CRLF type. */
		public void setRecordDelimiter(String newRecordDelimiter) {
			recordDelimiter = newRecordDelimiter;
		}
		
		/** Gets the character to use as a text qualifier in the data.
		 * @return The character to use as a text qualifier in the data. */
		public char getTextQualifier() {
			return textQualifier;
		}
		
		/** Sets the character to use as a text qualifier in the data.
		 * @param newTextQualifier The character to use as a text qualifier in the data. */
		public void setTextQualifier(char newTextQualifier) {
			textQualifier = newTextQualifier;
		}
		
		/** Whether text qualifiers will be used while writing data or not.
		 * @return Whether text qualifiers will be used while writing data or not. */
		public boolean getUseTextQualifier() {
			return useTextQualifier;
		}
		
		/** Sets whether text qualifiers will be used while writing data or not.
		 * @param newUseTextQualifier Whether to use a text qualifier while writing data or not. */
		public void setUseTextQualifier(boolean newUseTextQualifier) {
			useTextQualifier = newUseTextQualifier;
		}
		
		public EscapeMode getEscapeMode() {
			return escapeMode;
		}
		
		public void setEscapeMode(EscapeMode newEscapeMode) {
			escapeMode = newEscapeMode;
		}
		
		public void setComment(char newCommentChar) {
			commentChar = newCommentChar;
		}
		
		public char getComment() {
			return commentChar;
		}
		
		/** Whether fields will be surrounded by the text qualifier even if the qualifier is not necessarily needed to escape this field.
		 * @return Whether fields will be forced to be qualified or not. */
		public boolean getForceQualifier() {
			return forceQualifier;
		}
		
		/** Use this to force all fields to be surrounded by the text qualifier even if the qualifier is not necessarily needed to escape this field. Default is false.
		 * @param newForceQualifier Whether to force the fields to be qualified or not. */
		public void setForceQualifier(boolean newForceQualifier) {
			forceQualifier = newForceQualifier;
		}
		
	}
	
	public void writeTrimmed(String content) throws IOException {
		checkClosed();
		
		if (content == null) {
			content = "";
		}
		
		if (!firstColumn) {
			writer.write(delimiter);
		}
		
		boolean textQualify = forceQualifier;
		
		if ( ! content.isEmpty()) {
			content = content.trim();
		}
		
		if ( ! textQualify && useTextQualifier
				&& (content.indexOf(textQualifier) > -1
						|| content.indexOf(delimiter) > -1
						|| content.indexOf(recordDelimiter) > -1
						|| (firstColumn && (content.isEmpty() || content.charAt(0) == commentChar)))) {
		// check for empty first column, which if on its own line must
		// be qualified or the line will be skipped
			textQualify = true;
		}
		
		doWrite(content, textQualify);
	}
	
	/** Writes another column of data to this record. Does not preserve leading and trailing whitespace in this column of data.
	 * @param content The data for the new column.
	 * @exception IOException Thrown if an error occurs while writing data to the destination stream. */
	public void write(String content) throws IOException {
		checkClosed();
		
		if (content == null) {
			content = "";
		}
		
		if (!firstColumn) {
			writer.write(delimiter);
		}
		
		boolean textQualify = forceQualifier;
		
		if ( ! textQualify && useTextQualifier
				&& (content.indexOf(textQualifier) > -1
						|| content.indexOf(delimiter) > -1
						|| content.indexOf(recordDelimiter) > -1
						|| (firstColumn && (content.isEmpty() || content.charAt(0) == commentChar)))) {
		// check for empty first column, which if on its own line must
		// be qualified or the line will be skipped
			textQualify = true;
		}
		
		if (useTextQualifier && !textQualify && ! content.isEmpty()) {
			char firstLetter = content.charAt(0);
			
			if (firstLetter == Letters.SPACE || firstLetter == Letters.TAB) {
				textQualify = true;
			}
			
			if (!textQualify && content.length() > 1) {
				char lastLetter = content.charAt(content.length() - 1);
				
				if (lastLetter == Letters.SPACE || lastLetter == Letters.TAB) {
					textQualify = true;
				}
			}
		}
		
		doWrite(content, textQualify);
	}
	
	private void doWrite(String content, boolean textQualify) throws IOException {
		if (textQualify) {
			writer.write(textQualifier);
			
			if (escapeMode == EscapeMode.BACKSLASH) {
				content = content.replace("" + Letters.BACKSLASH, "" + Letters.BACKSLASH + Letters.BACKSLASH);
				content = content.replace("" + textQualifier, "" + Letters.BACKSLASH + textQualifier);
			}
			else {
				content = content.replace("" + textQualifier, "" + textQualifier + textQualifier);
			}
		}
		else if (escapeMode == EscapeMode.BACKSLASH) {
			content = content.replace("" + Letters.BACKSLASH, "" + Letters.BACKSLASH + Letters.BACKSLASH);
			content = content.replace("" + delimiter, "" + Letters.BACKSLASH + delimiter);
			
			// may have to do every character in rDelim?
			for (char rd : recordDelimiter.toCharArray()) {
				content = content.replace("" + rd, "" + Letters.BACKSLASH + rd);
			}
			
			
			if (firstColumn && ! content.isEmpty() && content.charAt(0) == commentChar) {
				if (content.length() > 1) {
					content = "" + Letters.BACKSLASH + commentChar + content.substring(1);
				}
				else {
					content = "" + Letters.BACKSLASH + commentChar;
				}
			}
		}
		
		writer.write(content);
		
		if (textQualify) {
			writer.write(textQualifier);
		}
		
		firstColumn = false;
	}
	
	public void writeComment(String commentText) throws IOException {
		checkClosed();
		writer.write(commentChar);
		writer.write(commentText);
		writer.write(recordDelimiter);
		firstColumn = true;
	}
	
	/** Writes a new record using the passed in array of values.
	 * @param values Values to be written.
	 * @throws IOException Thrown if an error occurs while writing data to the destination stream. */
	public void writeRecordTrimmed(String[] values) throws IOException {
		if (values != null && values.length > 0) {
			for (int i = 0; i < values.length; i++) {
				writeTrimmed(values[i]);
			}
			endRecord();
		}
	}
	
	/** Writes a new record using the passed in array of values.
	 * @param values Values to be written.
	 * @throws IOException Thrown if an error occurs while writing data to the destination stream. */
	public void writeRecord(String[] values) throws IOException {
		if (values != null && values.length > 0) {
			for (int i = 0; i < values.length; i++) {
				write(values[i]);
			}
			endRecord();
		}
	}
	
	/** Ends the current record by sending the record delimiter.
	 * @exception IOException Thrown if an error occurs while writing data to the destination stream. */
	public void endRecord() throws IOException {
		checkClosed();
		writer.write(recordDelimiter);
		firstColumn = true;
	}
	
	/** Clears all buffers for the current writer and causes any buffered data to be written to the underlying device.
	 * @exception IOException Thrown if an error occurs while writing data to the destination stream. */
	public void flush() throws IOException {
		writer.flush();
	}
	
	/** Closes and releases all related resources. */
	@Override // implements AutoCloseable
	public void close() {
		close(true);
	}
	
	public void close(boolean closeWriter) {
		if ( ! closed && closeWriter && writer != null) {
			try {
				writer.close();
			}
			catch (Exception e) {
				// just eat the exception
			}
		}
		writer = null;
		closed = true;
	}
	
	private void checkClosed() throws IOException {
		if (closed) {
			throw new IOException("This instance of the CsvWriter class has already been closed.");
		}
	}
	
}
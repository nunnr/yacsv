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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import com.nunn.yacsv.CsvReader.EscapeMode;
import com.nunn.yacsv.CsvReader.Letters;

/** A stream based writer for writing delimited text data to a file or a stream. */
public class CsvWriter implements AutoCloseable {
	
	private Writer			outputStream				= null;
	private String			fileName					= null;
	private boolean			firstColumn					= true;
	private boolean			useCustomRecordDelimiter	= false;
	private Charset			charset						= null;
	private boolean			initialized					= false;
	private boolean			closed						= false;
	private String			systemRecordDelimiter		= "\r\n";
	
	public char		TextQualifier = Letters.QUOTE;
	public boolean	UseTextQualifier = true;
	public char		Delimiter = Letters.COMMA;
	public char		RecordDelimiter = Letters.NULL;
	public char		Comment = Letters.POUND;
	public EscapeMode		escapeMode = EscapeMode.DOUBLED;
	public boolean	ForceQualifier = false;
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using a file as the data destination.
	 * 
	 * @param fileName
	 *            The path to the file to output the data.
	 * @param delimiter
	 *            The character to use as the column delimiter.
	 * @param charset
	 *            The {@link java.nio.charset.Charset Charset} to use while writing the data. */
	public CsvWriter(String fileName, char delimiter, Charset charset) {
		if (fileName == null) {
			throw new IllegalArgumentException("Parameter fileName can not be null.");
		}
		
		if (charset == null) {
			throw new IllegalArgumentException("Parameter charset can not be null.");
		}
		
		this.fileName = fileName;
		this.Delimiter = delimiter;
		this.charset = charset;
	}
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using a file as the data destination.&nbsp;Uses a comma as the column delimiter and ISO-8859-1 as the {@link java.nio.charset.Charset Charset}.
	 * 
	 * @param fileName
	 *            The path to the file to output the data. */
	public CsvWriter(String fileName) {
		this(fileName, Letters.COMMA, Charset.forName("ISO-8859-1"));
	}
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using a Writer to write data to.&nbsp;Uses a comma as the column delimiter.
	 * 
	 * @param outputStream
	 *            The stream to write the column delimited data to. */
	public CsvWriter(Writer outputStream) {
		this(outputStream, Letters.COMMA);
	}
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using a Writer to write data to.
	 * 
	 * @param outputStream
	 *            The stream to write the column delimited data to.
	 * @param delimiter
	 *            The character to use as the column delimiter. */
	public CsvWriter(Writer outputStream, char delimiter) {
		if (outputStream == null) {
			throw new IllegalArgumentException("Parameter outputStream can not be null.");
		}
		
		this.outputStream = outputStream;
		this.Delimiter = delimiter;
		initialized = true;
	}
	
	/** Creates a {@link com.csvreader.CsvWriter CsvWriter} object using an OutputStream to write data to.
	 * 
	 * @param outputStream
	 *            The stream to write the column delimited data to.
	 * @param delimiter
	 *            The character to use as the column delimiter.
	 * @param charset
	 *            The {@link java.nio.charset.Charset Charset} to use while writing the data. */
	public CsvWriter(OutputStream outputStream, char delimiter, Charset charset) {
		this(new OutputStreamWriter(outputStream, charset), delimiter);
	}
	
	/** Gets the character being used as the column delimiter.
	 * 
	 * @return The character being used as the column delimiter. */
	public char getDelimiter() {
		return Delimiter;
	}
	
	/** Sets the character to use as the column delimiter.
	 * 
	 * @param delimiter
	 *            The character to use as the column delimiter. */
	public void setDelimiter(char delimiter) {
		this.Delimiter = delimiter;
	}
	
	public char getRecordDelimiter() {
		return RecordDelimiter;
	}
	
	/** Sets the character to use as the record delimiter.
	 * 
	 * @param recordDelimiter
	 *            The character to use as the record delimiter. Default is combination of standard end of line characters for Windows, Unix, or Mac. */
	public void setRecordDelimiter(char recordDelimiter) {
		useCustomRecordDelimiter = true;
		this.RecordDelimiter = recordDelimiter;
	}
	
	/** Gets the character to use as a text qualifier in the data.
	 * 
	 * @return The character to use as a text qualifier in the data. */
	public char getTextQualifier() {
		return TextQualifier;
	}
	
	/** Sets the character to use as a text qualifier in the data.
	 * 
	 * @param textQualifier
	 *            The character to use as a text qualifier in the data. */
	public void setTextQualifier(char textQualifier) {
		this.TextQualifier = textQualifier;
	}
	
	/** Whether text qualifiers will be used while writing data or not.
	 * 
	 * @return Whether text qualifiers will be used while writing data or not. */
	public boolean getUseTextQualifier() {
		return UseTextQualifier;
	}
	
	/** Sets whether text qualifiers will be used while writing data or not.
	 * 
	 * @param useTextQualifier
	 *            Whether to use a text qualifier while writing data or not. */
	public void setUseTextQualifier(boolean useTextQualifier) {
		this.UseTextQualifier = useTextQualifier;
	}
	
	public EscapeMode getEscapeMode() {
		return escapeMode;
	}
	
	public void setEscapeMode(EscapeMode escapeMode) {
		this.escapeMode = escapeMode;
	}
	
	public void setComment(char comment) {
		this.Comment = comment;
	}
	
	public char getComment() {
		return Comment;
	}
	
	/** Whether fields will be surrounded by the text qualifier even if the qualifier is not necessarily needed to escape this field.
	 * 
	 * @return Whether fields will be forced to be qualified or not. */
	public boolean getForceQualifier() {
		return ForceQualifier;
	}
	
	/** Use this to force all fields to be surrounded by the text qualifier even if the qualifier is not necessarily needed to escape this field. Default is false.
	 * 
	 * @param forceQualifier
	 *            Whether to force the fields to be qualified or not. */
	public void setForceQualifier(boolean forceQualifier) {
		this.ForceQualifier = forceQualifier;
	}
	
	/** Writes another column of data to this record.
	 * 
	 * @param content
	 *            The data for the new column.
	 * @param preserveSpaces
	 *            Whether to preserve leading and trailing whitespace in this column of data.
	 * @exception IOException
	 *                Thrown if an error occurs while writing data to the destination stream. */
	public void write(String content, boolean preserveSpaces) throws IOException {
		checkClosed();
		
		checkInit();
		
		if (content == null) {
			content = "";
		}
		
		if (!firstColumn) {
			outputStream.write(Delimiter);
		}
		
		boolean textQualify = ForceQualifier;
		
		if (!preserveSpaces && content.length() > 0) {
			content = content.trim();
		}
		
		if (!textQualify && UseTextQualifier && (content.indexOf(TextQualifier) > -1 || content.indexOf(Delimiter) > -1 || (!useCustomRecordDelimiter && (content.indexOf(Letters.LF) > -1 || content.indexOf(Letters.CR) > -1)) || (useCustomRecordDelimiter && content.indexOf(RecordDelimiter) > -1) || (firstColumn && content.length() > 0 && content.charAt(0) == Comment) ||
		// check for empty first column, which if on its own line must
		// be qualified or the line will be skipped
				(firstColumn && content.length() == 0))) {
			textQualify = true;
		}
		
		if (UseTextQualifier && !textQualify && content.length() > 0 && preserveSpaces) {
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
		
		if (textQualify) {
			outputStream.write(TextQualifier);
			
			if (escapeMode == EscapeMode.BACKSLASH) {
				content = content.replace("" + Letters.BACKSLASH, "" + Letters.BACKSLASH + Letters.BACKSLASH);
				content = content.replace("" + TextQualifier, "" + Letters.BACKSLASH + TextQualifier);
			}
			else {
				content = content.replace("" + TextQualifier, "" + TextQualifier + TextQualifier);
			}
		}
		else if (escapeMode == EscapeMode.BACKSLASH) {
			content = content.replace("" + Letters.BACKSLASH, "" + Letters.BACKSLASH + Letters.BACKSLASH);
			content = content.replace("" + Delimiter, "" + Letters.BACKSLASH + Delimiter);
			
			if (useCustomRecordDelimiter) {
				content = content.replace("" + RecordDelimiter, "" + Letters.BACKSLASH + RecordDelimiter);
			}
			else {
				content = content.replace("" + Letters.CR, "" + Letters.BACKSLASH + Letters.CR);
				content = content.replace("" + Letters.LF, "" + Letters.BACKSLASH + Letters.LF);
			}
			
			if (firstColumn && content.length() > 0 && content.charAt(0) == Comment) {
				if (content.length() > 1) {
					content = "" + Letters.BACKSLASH + Comment + content.substring(1);
				}
				else {
					content = "" + Letters.BACKSLASH + Comment;
				}
			}
		}
		
		outputStream.write(content);
		
		if (textQualify) {
			outputStream.write(TextQualifier);
		}
		
		firstColumn = false;
	}
	
	/** Writes another column of data to this record.&nbsp;Does not preserve leading and trailing whitespace in this column of data.
	 * 
	 * @param content
	 *            The data for the new column.
	 * @exception IOException
	 *                Thrown if an error occurs while writing data to the destination stream. */
	public void write(String content) throws IOException {
		write(content, false);
	}
	
	public void writeComment(String commentText) throws IOException {
		checkClosed();
		
		checkInit();
		
		outputStream.write(Comment);
		
		outputStream.write(commentText);
		
		if (useCustomRecordDelimiter) {
			outputStream.write(RecordDelimiter);
		}
		else {
			outputStream.write(systemRecordDelimiter);
		}
		
		firstColumn = true;
	}
	
	/** Writes a new record using the passed in array of values.
	 * 
	 * @param values
	 *            Values to be written.
	 * @param preserveSpaces
	 *            Whether to preserver leading and trailing spaces in columns while writing out to the record or not.
	 * @throws IOException
	 *             Thrown if an error occurs while writing data to the destination stream. */
	public void writeRecord(String[] values, boolean preserveSpaces) throws IOException {
		if (values != null && values.length > 0) {
			for (int i = 0; i < values.length; i++) {
				write(values[i], preserveSpaces);
			}
			
			endRecord();
		}
	}
	
	/** Writes a new record using the passed in array of values.
	 * 
	 * @param values
	 *            Values to be written.
	 * @throws IOException
	 *             Thrown if an error occurs while writing data to the destination stream. */
	public void writeRecord(String[] values) throws IOException {
		writeRecord(values, false);
	}
	
	/** Ends the current record by sending the record delimiter.
	 * 
	 * @exception IOException
	 *                Thrown if an error occurs while writing data to the destination stream. */
	public void endRecord() throws IOException {
		checkClosed();
		
		checkInit();
		
		if (useCustomRecordDelimiter) {
			outputStream.write(RecordDelimiter);
		}
		else {
			outputStream.write(systemRecordDelimiter);
		}
		
		firstColumn = true;
	}
	
	private void checkInit() throws IOException {
		if (!initialized) {
			if (fileName != null) {
				outputStream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), charset));
			}
			
			initialized = true;
		}
	}
	
	/** Clears all buffers for the current writer and causes any buffered data to be written to the underlying device.
	 * 
	 * @exception IOException
	 *                Thrown if an error occurs while writing data to the destination stream. */
	public void flush() throws IOException {
		outputStream.flush();
	}
	
	/** Closes and releases all related resources. */
	public void close() {
		if (!closed) {
			close(true);
			
			closed = true;
		}
	}
	
	private void close(boolean closing) {
		if (!closed) {
			if (closing) {
				charset = null;
			}
			
			try {
				if (initialized) {
					outputStream.close();
				}
			}
			catch (Exception e) {
				// just eat the exception
			}
			
			outputStream = null;
			
			closed = true;
		}
	}
	
	private void checkClosed() throws IOException {
		if (closed) {
			throw new IOException("This instance of the CsvWriter class has already been closed.");
		}
	}
}
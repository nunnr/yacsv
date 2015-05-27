package com.nunn.yacsv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class Fuzzer {
	
	public void writeDataToFile(Path path, int columns, int rows) throws IOException {
		try (BufferedWriter w = Files.newBufferedWriter(path);) {
			for (int i = 0; i < rows; i++) {
				for (int j = 0; j < columns; j++) {
					w.write(getRandomChars());
				}
			}
		}
	}
	
	private final int MAX_CHAR = 250; // NOT Character.MAX_VALUE
	private final int MAX_LENGTH = 20;
	private final Random RANDOM = new Random();
	
	private char[] getRandomChars() {
		int length = RANDOM.nextInt(MAX_LENGTH);
		char[] buffer = new char[length];
		boolean quoteIt = false;
		
		for (int i = 0; i < length; i++) {
			buffer[i] = (char)(RANDOM.nextInt(MAX_CHAR - 1) + 1); // avoid null
			if (buffer[i] == '"') { // double quote quotes
				if (i < length - 1) {
					i++;
					buffer[i] = '"';
				}
				else {
					buffer[i] = 'a'; // easy option
				}
			}
			else if (buffer[i] == '\r' || buffer[i] == '\n') {
				quoteIt = true;
			}
		}
		
		if (quoteIt) {
			char[] temp = new char[length + 2];
			temp[0] = '"';
			temp[length + 1] = '"';
			
			System.arraycopy(buffer, 0, temp, 1, length);
			
			buffer = temp;
		}
		
		return buffer;
	}
	
}

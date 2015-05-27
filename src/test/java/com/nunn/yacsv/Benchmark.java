package com.nunn.yacsv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Benchmark {
	
	public static void main(String[] args) {
		List<Path> files = new ArrayList<>();
		
		Path temp = null;
		
		try {
			temp = Files.createTempDirectory("yacsv-bench");
			
			Fuzzer fuzzer = new Fuzzer();
			
			for (int i = 5; i < 206; i += 20) {
				Path file = Files.createTempFile(temp, "data-" + i, ".csv");
				fuzzer.writeDataToFile(file, i, 10000);
				files.add(file);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		if ( ! files.isEmpty()) {
			for (Path file : files) {
				try (CsvReader csv = new CsvReader(file, StandardCharsets.UTF_8);) {
					csv.config.setSafetySwitch(false);
					
					long total = 0;
					
					long start = System.nanoTime();
					
					while (csv.readRecord()) {
						total++;
					}
					
					long end = System.nanoTime();
					
					System.out.println((end - start) / 1000000 + " msec for " + total + " lines in " + file);
					
					Files.delete(file);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			try {
				Files.delete(temp);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}

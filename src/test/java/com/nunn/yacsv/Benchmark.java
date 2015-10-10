package com.nunn.yacsv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Benchmark {
	
	public static void main(String[] args) {
		System.out.println("Benchmark begun...");
		
		List<Path> files = new ArrayList<>();
		
		Path temp = null;
		
		try {
			temp = Files.createTempDirectory("yacsv-bench");
			
			Fuzzer fuzzer = new Fuzzer();
			
			for (int i = 10000; i < 1000001; i *= 10) {
				Path file = Files.createTempFile(temp, "data-" + i, ".csv");
				fuzzer.writeDataToFile(file, 25, i);
				files.add(file);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("Files ready...");
		
		try {
			Thread.sleep(2000);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if ( ! files.isEmpty()) {
			long[] lines = new long[files.size()];
			long[][] times = new long[files.size()][20];
			int fileIdx = 0;
			
			for (Path file : files) {
				long count = 0;
				
				for (int i = 0; i < 10; i++) {
					try (CsvReader csv = new CsvReader(file, StandardCharsets.UTF_8);) {
						csv.config.setSafetySwitch(false);
						
						count = 0;
						
						long start = System.nanoTime();
						
						while (csv.readRecord()) {
							count++;
						}
						
						times[fileIdx][i] = System.nanoTime() - start;
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				lines[fileIdx] = count;
				
				fileIdx++;
			}
			
			fileIdx = 0;
			
			for (Path file : files) {
				double averageTime = Arrays.stream(times[fileIdx]).average().orElse(-1) / 1000000;
				
				System.out.printf("%5.0f msec for %10d lines in %s%n", averageTime, lines[fileIdx], file);
				
				try {
					Files.delete(file);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				
				fileIdx++;
			}
		}
		
		if (temp != null) {
			try {
				Files.delete(temp);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		System.out.println("Benchmark complete.");
	}
	
}

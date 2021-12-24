package com.ddf.strucexporter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Util {
	public static byte[] readAll(File file) {
		try {
			FileInputStream fis = new FileInputStream(file);
			byte[] result = new byte[fis.available()];
			fis.read(result);
			fis.close();
			return result;
		} catch (IOException e) {
			return new byte[0];
		}
	}
	
	private static final byte[] STRUCTURE_TEMPLE_PREFIX = "structuretemplate_".getBytes(StandardCharsets.UTF_8);
	public static boolean isStructureTemplateKey(byte[] bytes) {
		if (bytes.length < STRUCTURE_TEMPLE_PREFIX.length) {
			return false;
		}
		for (int i = 0; i < STRUCTURE_TEMPLE_PREFIX.length; i++) {
			if (bytes[i] != STRUCTURE_TEMPLE_PREFIX[i]) {
				return false;
			}
		}
		return true;
	}
}

package com.ddf.strucexporter;
import java.io.File;
import java.io.Serializable;
import java.util.Objects;

public class World implements Serializable, Comparable<World> {
	private File worldDir;
	private File leveldatFile;
	private String worldName;
	private File dbDir;
	
	public World(File worldDir, File leveldatFile, String worldName, File dbDir) {
		this.worldDir = worldDir;
		this.leveldatFile = leveldatFile;
		this.worldName = worldName;
		this.dbDir = dbDir;
	}

	public void setWorldDir(File worldDir) {
		this.worldDir = worldDir;
	}

	public File getWorldDir() {
		return worldDir;
	}

	public void setLeveldatFile(File leveldatFile) {
		this.leveldatFile = leveldatFile;
	}

	public File getLeveldatFile() {
		return leveldatFile;
	}

	public void setWorldName(String worldName) {
		this.worldName = worldName;
	}

	public String getWorldName() {
		return worldName;
	}

	public void setDbDir(File dbDir) {
		this.dbDir = dbDir;
	}

	public File getDbDir() {
		return dbDir;
	}
	
	@Override
	public int compareTo(World world) {
		return getWorldName().compareTo(world.getWorldName());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		World world = (World) o;
		return Objects.equals(worldDir, world.worldDir);
	}

	@Override
	public int hashCode() {
		return Objects.hash(worldDir);
	}
}

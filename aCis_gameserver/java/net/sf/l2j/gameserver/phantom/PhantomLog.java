package net.sf.l2j.gameserver.phantom;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class PhantomLog
{
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static Path _path = Path.of("./log/phantoms.log");
	private static boolean _enabled = true;
	
	private PhantomLog()
	{
	}
	
	public static synchronized void init()
	{
		_enabled = PhantomConfig.phantomLogEnabled();
		_path = Path.of(PhantomConfig.phantomLogFile());
		if (!_enabled)
			return;
		
		try
		{
			final Path parent = _path.getParent();
			if (parent != null)
				Files.createDirectories(parent);
			
			if (Files.exists(_path) && PhantomConfig.phantomLogBackupOnStartup())
			{
				final String backup = _path.getFileName().toString() + ".bak-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
				Files.copy(_path, _path.resolveSibling(backup), StandardCopyOption.REPLACE_EXISTING);
			}
			
			Files.writeString(_path, line("INFO", "Phantom log started"), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch (IOException e)
		{
			_enabled = false;
		}
	}
	
	public static void info(String message)
	{
		write("INFO", message);
	}
	
	public static void warn(String message)
	{
		write("WARN", message);
	}
	
	public static void error(String message, Throwable t)
	{
		write("ERROR", message + " | " + t.getClass().getSimpleName() + ": " + t.getMessage());
	}
	
	private static synchronized void write(String level, String message)
	{
		if (!_enabled)
			return;
		
		try
		{
			Files.writeString(_path, line(level, message), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException e)
		{
			_enabled = false;
		}
	}
	
	private static String line(String level, String message)
	{
		return "[" + STAMP.format(LocalDateTime.now()) + "][" + level + "] " + message + System.lineSeparator();
	}
}

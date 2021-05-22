package io.github.astrarre.merger;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.google.common.collect.ImmutableMap;
import io.github.astrarre.api.PlatformId;
import io.github.astrarre.api.classes.RawPlatformClass;
import io.github.astrarre.api.classpath.PathsContext;
import io.github.astrarre.api.impl.ClassTypeSolver;
import io.github.astrarre.merger.impl.AccessMerger;
import io.github.astrarre.merger.impl.ClassMerger;
import io.github.astrarre.merger.impl.HeaderMerger;
import io.github.astrarre.merger.impl.InnerClassAttributeMerger;
import io.github.astrarre.merger.impl.InterfaceMerger;
import io.github.astrarre.merger.impl.SignatureMerger;
import io.github.astrarre.merger.impl.SuperclassMerger;
import io.github.astrarre.merger.impl.field.FieldMerger;
import io.github.astrarre.merger.impl.method.MethodMerger;
import joptsimple.internal.Strings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class Mergers {

	/**
	 * if the first entry of a zip file is a file with the name of this field, it is configured
	 */
	public static final String MERGER_META_FILE = "merger_metadata.properties";
	// start merger meta properties
	public static final String RESOURCES = "resources";
	public static final String PLATFORMS = "platforms";
	// end

	public static final Map<String, ?> CREATE_ZIP = ImmutableMap.of("create", "true");
	public static final ThreadLocal<byte[]> BUFFER = ThreadLocal.withInitial(() -> new byte[8192]);
	/**
	 * if a file in the root directory of a jar has the following name, the entire jar contains nothing but class files
	 */
	public static final String RESOURCES_MARKER_FILE = "resourceJar.marker";

	public static List<Merger> defaults(Map<String, ?> config) {
		List<Merger> mergers = new ArrayList<>(); // order matters sometimes
		mergers.add(new AccessMerger(config));
		mergers.add(new ClassMerger(config));
		mergers.add(new HeaderMerger(config));
		mergers.add(new InnerClassAttributeMerger(config));
		mergers.add(new InterfaceMerger(config));
		mergers.add(new SuperclassMerger(config));
		mergers.add(new SignatureMerger(config));
		mergers.add(new MethodMerger(config));
		mergers.add(new FieldMerger(config));
		return mergers;
	}

	public static void merge(Map<String, List<String>> compact, List<Merger> mergers, Path dest, Map<List<String>, Iterable<File>> toMerge, Function<List<String>, Path> resources, boolean shouldLeaveMarker)
			throws IOException, URISyntaxException {
		Map<List<String>, PathsContext> contexts = new HashMap<>();
		Set<String> fileNames = new HashSet<>();

		List<Closeable> toClose = new ArrayList<>();
		for (Map.Entry<List<String>, Iterable<File>> entry : toMerge.entrySet()) {
			JavaParser parser = new JavaParser();
			List<Path> paths = new ArrayList<>();
			for (File file : entry.getValue()) {
				FileSystem system = FileSystems.newFileSystem(file.toPath(), null);
				for (Path directory : system.getRootDirectories()) {
					paths.add(directory);
				}
				toClose.add(system);
			}

			CombinedTypeSolver solver = new CombinedTypeSolver();
			solver.add(new ClassLoaderTypeSolver(ClassLoader.getSystemClassLoader()));
			solver.add(new ClassTypeSolver(paths, fileNames));
			parser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(solver));
			contexts.put(entry.getKey(), new PathsContext(parser, paths));
		}

		Map<List<String>, ZipOutputStream> resourceOutput = new HashMap<>();
		ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(dest)));
		if(shouldLeaveMarker) {
			output.putNextEntry(new ZipEntry(MERGER_META_FILE));
			output.closeEntry();
		}

		toClose.add(output);
		for (String name : fileNames) {
			if(RESOURCES_MARKER_FILE.equals(name) || MERGER_META_FILE.equals(name)) continue;
			List<RawPlatformClass> classes = new ArrayList<>();
			for (Map.Entry<List<String>, PathsContext> entry : contexts.entrySet()) {
				List<String> strings = entry.getKey();
				PathsContext context = entry.getValue();
				byte[] data = context.getResourceAsByteArray(name);
				if(data != null && name.endsWith(".class")) {
					ClassReader reader = new ClassReader(data);
					ClassNode node = new ClassNode();
					reader.accept(node, 0);
					classes.add(new RawPlatformClass(new PlatformId(strings), node, context));
				} else if(data != null) {
					ZipOutputStream dump = resourceOutput.computeIfAbsent(strings, strings1 -> {
						try {
							Path pth = resources.apply(strings1);
							Files.createDirectories(pth.getParent());
							ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(pth)));
							zos.putNextEntry(new ZipEntry(MERGER_META_FILE));
							Properties properties = new Properties();
							properties.put("platforms", Strings.join(strings, ","));
							properties.put("resources", "true");
							properties.store(zos, "This is a marker file for optimization purposes");
							return zos;
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					});
					dump.putNextEntry(new ZipEntry(name));
					dump.write(data);
					dump.closeEntry();
				}
			}

			if(!classes.isEmpty()) {
				ClassNode merged = new ClassNode();
				for (Merger merger : mergers) {
					merger.merge(classes, merged, compact);
				}
				ClassWriter writer = new ClassWriter(0);
				merged.accept(writer);
				output.putNextEntry(new ZipEntry(name));
				output.write(writer.toByteArray());
				output.closeEntry();
			}
		}

		for (ZipOutputStream value : resourceOutput.values()) {
			value.close();
		}
		for (Closeable closeable : toClose) {
			closeable.close();
		}
	}

	public static void copy(InputStream from, OutputStream to) throws IOException {
		int read;
		byte[] buf = BUFFER.get();
		while ((read = from.read(buf)) != -1) {
			to.write(buf, 0, read);
		}
	}

	static class FileEntry {
		final String name;
		final byte[] data;

		FileEntry(String name, byte[] data) {
			this.name = name;
			this.data = data;
		}
	}

}

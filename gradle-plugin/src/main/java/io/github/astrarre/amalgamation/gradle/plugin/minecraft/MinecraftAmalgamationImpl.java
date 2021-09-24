package io.github.astrarre.amalgamation.gradle.plugin.minecraft;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.gson.reflect.TypeToken;
import io.github.astrarre.amalgamation.gradle.dependencies.CASMergedDependency;
import io.github.astrarre.amalgamation.gradle.dependencies.LibrariesDependency;
import io.github.astrarre.amalgamation.gradle.dependencies.MinecraftDependency;
import io.github.astrarre.amalgamation.gradle.dependencies.MojMergedDependency;
import io.github.astrarre.amalgamation.gradle.dependencies.RemappingDependency;
import io.github.astrarre.amalgamation.gradle.files.CachedFile;
import io.github.astrarre.amalgamation.gradle.files.MojmapFile;
import io.github.astrarre.amalgamation.gradle.files.NativesFile;
import io.github.astrarre.amalgamation.gradle.files.assets.AssetProvider;
import io.github.astrarre.amalgamation.gradle.files.assets.Assets;
import io.github.astrarre.amalgamation.gradle.plugin.base.BaseAmalgamationImpl;
import io.github.astrarre.amalgamation.gradle.utils.AmalgIO;
import io.github.astrarre.amalgamation.gradle.utils.Clock;
import io.github.astrarre.amalgamation.gradle.utils.LauncherMeta;
import io.github.astrarre.amalgamation.gradle.utils.casmerge.CASMerger;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

public class MinecraftAmalgamationImpl extends BaseAmalgamationImpl implements MinecraftAmalgamation {
	public static final Type SET = new TypeToken<Set<String>>() {}.getType();
	public String librariesDirectory = LauncherMeta.activeMinecraftDirectory() + "/libraries";

	public MinecraftAmalgamationImpl(Project project) {
		super(project);
		File file = new File(this.librariesDirectory);
		if(!(file.isDirectory() && file.exists())) {
			this.librariesDirectory = AmalgIO.globalCache(project.getGradle()).resolve("libraries").toAbsolutePath().toString();
		}
	}

	@Override
	public MinecraftDependency client(String version) {
		return new MinecraftDependency(this.project, version, true, false, false);
	}

	@Override
	public MinecraftDependency server(String version, boolean doStrip) {
		return new MinecraftDependency(this.project, version, false, false, doStrip);
	}

	@Override
	public Dependency merged(String version, Action<CASMergedDependency.Config> configurate) {
		CASMergedDependency.Config config = new CASMergedDependency.Config();
		config.version = version;
		configurate.execute(config);

		Dependency client = config.client, server = config.server;
		return new CASMergedDependency(
				this.project,
				version,
				config.handler,
				config.classReaderSettings,
				config.checkForServerOnly,
				() -> AmalgIO.resolve(project, client),
				() -> AmalgIO.resolve(project, server));
	}

	@Override
	public Dependency mojmerged(String version, CASMerger.Handler handler) {
		CachedFile<?> file = new MojmapFile(this.project, version, true);
		return new MojMergedDependency(this.project, version, handler, this.client(version), file);
	}

	@Override
	public LibrariesDependency libraries(String version, Action<LibrariesDependency> configure) {
		LibrariesDependency dependency = new LibrariesDependency(this.project, version);
		configure.execute(dependency);
		return dependency;
	}

	@Override
	public Assets assets(String version) {
		try(Clock clock = new Clock("Cache validation / download for assets took %sms", this.logger)) {
			return AssetProvider.getAssetsDir(this, version);
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String natives(String version) {
		LauncherMeta meta = MinecraftAmalgamationGradlePlugin.getLauncherMeta(this.project);
		CachedFile<Set<String>> natives = new NativesFile(this, version, meta);
		return natives.getPath().toAbsolutePath().toString();
	}

	@Override
	public Dependency map(Action<RemappingDependency> mappings) {
		RemappingDependency dependency = new RemappingDependency(this.project);
		mappings.execute(dependency);
		return dependency;
	}

	@Override
	public void setLibrariesCache(String directory) {
		this.librariesDirectory = directory;
	}

	@Override
	public String librariesCache() {
		return this.librariesDirectory;
	}
}

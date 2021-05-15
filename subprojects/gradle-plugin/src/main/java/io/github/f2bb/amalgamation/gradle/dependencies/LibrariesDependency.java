package io.github.f2bb.amalgamation.gradle.dependencies;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import com.google.common.collect.Iterables;
import io.github.f2bb.amalgamation.gradle.extensions.LauncherMeta;
import io.github.f2bb.amalgamation.gradle.plugin.base.BaseAmalgamationImpl;
import io.github.f2bb.amalgamation.gradle.plugin.minecraft.MinecraftAmalgamationGradlePlugin;
import io.github.f2bb.amalgamation.gradle.util.CachedFile;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

public class LibrariesDependency extends AbstractSelfResolvingDependency {
	public static final String FALLBACK = "AMALGAMATION_GLOBAL";
	/**
	 * defaults to your .minecraft installation, if not found, uses amalgamation cache
	 */
	public String librariesDirectory;

	public LibrariesDependency(Project project, String version) {
		super(project, "net.minecraft", "minecraft-libraries", version);
		// todo multimc support
		// C:\Users\%user%\Desktop\archive\Games\MultiMC\libraries
		switch (BaseAmalgamationImpl.OPERATING_SYSTEM) {
		case "windows":
			this.librariesDirectory = System.getenv("appdata") + "/.minecraft/libraries";
			break;
		case "linux":
			this.librariesDirectory = System.getProperty("user.home") + "/.minecraft/libraries";
			break;
		case "osx":
			this.librariesDirectory = System.getProperty("user.home") + "/Library/Application Support/minecraft/libraries";
			break;
		}

		File file = new File(this.librariesDirectory);
		if (!(file.isDirectory() && file.exists())) {
			this.librariesDirectory = FALLBACK;
		}
	}

	@Override
	protected Iterable<Path> resolvePaths() {
		LauncherMeta meta = MinecraftAmalgamationGradlePlugin.getLauncherMeta(this.project);
		return Iterables.concat(Iterables.transform(
				Objects.requireNonNull(meta.getVersion(this.version), "Invalid version: " + this.version)
				       .getLibraries(),
				input -> Iterables.transform(input.evaluateAllDependencies(), dependency -> {
					Path jar;
					if (FALLBACK.equals(this.librariesDirectory)) {
						jar = CachedFile.globalCache(this.project.getGradle()).resolve(dependency.path);
					} else {
						jar = Paths.get(this.librariesDirectory).resolve(dependency.path);
					}
					return CachedFile.forUrl(dependency, jar, this.project.getLogger()).getPath();
				})));
	}

	@Override
	public Dependency copy() {
		return new LibrariesDependency(this.project, this.version);
	}
}

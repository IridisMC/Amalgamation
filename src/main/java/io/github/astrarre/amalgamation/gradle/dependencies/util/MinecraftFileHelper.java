package io.github.astrarre.amalgamation.gradle.dependencies.util;

import java.nio.file.Path;
import java.util.Set;

import io.github.astrarre.amalgamation.gradle.dependencies.HashedURLDependency;
import io.github.astrarre.amalgamation.gradle.dependencies.ShadowedLibraryStrippedDependency;
import io.github.astrarre.amalgamation.gradle.dependencies.SplitDependency;
import io.github.astrarre.amalgamation.gradle.plugin.minecraft.MinecraftAmalgamationGradlePlugin;
import io.github.astrarre.amalgamation.gradle.utils.AmalgIO;
import io.github.astrarre.amalgamation.gradle.utils.LauncherMeta;
import net.devtech.filepipeline.api.VirtualDirectory;
import net.devtech.filepipeline.api.VirtualFile;
import net.devtech.filepipeline.api.VirtualPath;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

public class MinecraftFileHelper {
	public static Set<Object> getDependency(Project project, String version, boolean isClient, boolean doStrip, boolean doSplit) {
		LauncherMeta meta = MinecraftAmalgamationGradlePlugin.getLauncherMeta(project);
		LauncherMeta.Version vers = meta.getVersion(version);
		LauncherMeta.HashedURL url;
		String area;
		if(isClient) {
			url = vers.getClientJar();
			if(doStrip) {
				area = "client";
			} else {
				area = "client-unstripped";
			}
		} else {
			url = vers.getServerJar();
			if(doStrip) {
				area = "server";
			} else {
				area = "server-unstripped";
			}
		}

		VirtualDirectory globalCache = AmalgIO.globalCache(project);
		VirtualFile jar = globalCache.getDir(version).getFile(area + ".jar");
		VirtualFile unstripped = globalCache.getDir(version).getFile(area + ".jar");
		HashedURLDependency dependency = new HashedURLDependency(project, url);
		dependency.group = "net.minecraft";
		dependency.name = isClient ? "client" : "server";
		dependency.version = version;

		if(doStrip || doSplit) {
			dependency.shouldOutput = false;
		}

		dependency.output = unstripped;
		Set<Object> f;
		if(doSplit) {
			SplitDependency split = new SplitDependency(project, dependency);
			split.outputDir = globalCache.getDir(version).getDir("split");
			f = split;
		} else {
			f = dependency;
		}

		if(doStrip) {
			dependency.shouldOutput = false;
			ShadowedLibraryStrippedDependency dep = new ShadowedLibraryStrippedDependency(project, jar);
			dep.toStrip = f;
			dep.version = version;
			return dep;
		} else {
			return f;
		}
	}
}

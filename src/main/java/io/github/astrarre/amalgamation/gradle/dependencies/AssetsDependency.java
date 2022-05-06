package io.github.astrarre.amalgamation.gradle.dependencies;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.google.common.hash.Hasher;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.astrarre.amalgamation.gradle.plugin.minecraft.MinecraftAmalgamationGradlePlugin;
import io.github.astrarre.amalgamation.gradle.plugin.minecraft.MinecraftAmalgamationImpl;
import io.github.astrarre.amalgamation.gradle.utils.AmalgIO;
import io.github.astrarre.amalgamation.gradle.utils.LauncherMeta;
import io.github.astrarre.amalgamation.gradle.utils.OS;
import net.devtech.filepipeline.api.VirtualDirectory;
import net.devtech.filepipeline.api.VirtualFile;
import net.devtech.filepipeline.api.VirtualPath;
import net.devtech.filepipeline.impl.util.FPInternal;
import org.gradle.api.Project;

public class AssetsDependency extends CachedDependency {
	public final HashedURLDependency assetIndexDependency; // todo getOutdated because there is no hash for the download from minecraft
	private final VirtualDirectory assetsDir;
	public final String assetsDirPath;
	private final String assetIndex;
	private final String version;

	public AssetsDependency(MinecraftAmalgamationImpl amalg, String version) {
		super(amalg.project);
		Project project = amalg.project;
		this.version = version;
		String assetsDirPath = LauncherMeta.minecraftDirectory(OS.ACTIVE) + "/assets";
		this.assetsDirPath = assetsDirPath;
		VirtualDirectory assetsDir = AmalgIO.getDir(Path.of(assetsDirPath)).asDir();
		if(assetsDir.exists()) {
			this.logger.lifecycle("Found .minecraft assets folder");
		} else {
			this.logger.lifecycle("No .minecraft assets folder, using global cache!");
			assetsDir = AmalgIO.globalCache(project).getDir("assetsDir");
		}
		this.assetsDir = assetsDir.asDir();

		LauncherMeta.Version vers = MinecraftAmalgamationGradlePlugin.getLauncherMeta(project).getVersion(version);
		this.assetIndex = vers.getAssetIndexVersion();
		this.assetIndexDependency = new HashedURLDependency(project, vers.getAssetIndexUrl());
		VirtualFile assetIndexFile = assetsDir.getDir("indexes").getFile(this.assetIndex + ".json");
		this.assetIndexDependency.output = assetIndexFile;
		if(assetIndexFile.exists()) {
			try {
				this.assetIndexDependency.writeHash();
			} catch(IOException e) {
				throw FPInternal.rethrow(e);
			}
		}
	}

	public String getAssetIndex() {
		return this.assetIndex;
	}

	public String getAssetsDir() {
		this.getArtifacts();
		return this.assetsDirPath;
	}

	@Override
	public void hashInputs(Hasher hasher) throws IOException {
		hasher.putString(this.assetIndexDependency.hash, StandardCharsets.UTF_8);
	}

	@Override
	protected VirtualPath evaluatePath(byte[] hash) {
		return this.assetsDir.resolve("objects");
	}

	@Override
	protected Set<Artifact> resolve0(VirtualPath resolvedPath, boolean isOutdated) {
		this.logger.lifecycle("downloading assets . . .");
		// if an index file exists, then we know we've downloaded the assets for that version
		VirtualDirectory directory = resolvedPath.asDir();
		Artifact.File file = new Artifact.File(this.project,
				"net.minecraft",
				"assets",
				this.version,
				directory,
				this.getCurrentHash(),
				Artifact.Type.RESOURCES);

		if(!isOutdated) {
			return Set.of(file);
		}

		try(Reader reader = this.assetIndexDependency.getOutdatedReader()) {
			JsonObject assetsJson = LauncherMeta.GSON.fromJson(reader, JsonObject.class);
			JsonObject objects = assetsJson.getAsJsonObject("objects");
			List<Future<?>> futures = new ArrayList<>(objects.entrySet().size());
			Set<String> visitedHashes = new HashSet<>();
			for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
				JsonObject assetJson = entry.getValue().getAsJsonObject();
				String hash = assetJson.getAsJsonPrimitive("hash").getAsString();
				if(visitedHashes.add(hash)) {
					Future<?> future = AmalgIO.SERVICE.submit(() -> {
						String minHash = hash.substring(0, 2);
						LauncherMeta.HashedURL url = new LauncherMeta.HashedURL(hash,
								"https://resources.download.minecraft.net/" + minHash + "/" + hash,
								entry.getKey());
						// todo maybe not compress for PNG?
						HashedURLDependency dependency = new HashedURLDependency(this.project, url);
						dependency.silent = true;
						dependency.output = directory.getDir(minHash).getFile(hash);
						if(!dependency.output.exists()) {
							dependency.resolve();
						}
					});
					futures.add(future);
				}
			}
			for (Future<?> future : futures) {
				future.get();
			}
		} catch (IOException | InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}

		return Set.of(file);
	}
}

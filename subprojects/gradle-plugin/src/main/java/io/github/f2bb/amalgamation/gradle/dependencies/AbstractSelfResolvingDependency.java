package io.github.f2bb.amalgamation.gradle.dependencies;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.AbstractDependency;
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSelfResolvingDependency extends AbstractDependency implements FileCollectionDependency, SelfResolvingDependencyInternal {
	protected final Project project;
	protected final String group, name, version;
	protected Set<File> resolved;

	public AbstractSelfResolvingDependency(Project project, String group, String name, String version) {
		this.project = project;
		this.group = group;
		this.name = name;
		this.version = version;
	}

	@Override
	public boolean contentEquals(@NotNull Dependency dependency) {
		return this.equals(dependency);
	}

	@Override
	public TaskDependency getBuildDependencies() {
		return new AbstractTaskDependency() {
			@Override
			public String toString() {
				return "Dependencies of " + AbstractSelfResolvingDependency.this.toString();
			}

			@Override
			public void visitDependencies(TaskDependencyResolveContext context) {
				context.add(AbstractSelfResolvingDependency.this);
			}
		};
	}

	@Override
	public Set<File> resolve() {
		return this.resolve(true);
	}

	protected Set<File> path() {
		if (this.resolved == null) {
			Set<File> files = new HashSet<>();
			for (Path path : this.resolvePaths()) {
				files.add(path.toFile());
			}
			this.resolved = files;
		}
		return this.resolved;
	}

	protected abstract Iterable<Path> resolvePaths();

	@Override
	public Set<File> resolve(boolean b) {
		return this.path();
	}

	@Nullable
	@Override
	public String getGroup() {
		return this.group;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Nullable
	@Override
	public String getVersion() {
		return this.version;
	}

	@Override
	public FileCollection getFiles() {
		return this.project.files(this.path());
	}

	@Nullable
	@Override
	public ComponentIdentifier getTargetComponentId() {
		return this::toString;
	}

	@NotNull
	@Override
	public String toString() {
		return this.getGroup() + ':' + this.getName() + ':' + this.getVersion();
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof AbstractSingleFileSelfResolvingDependency)) {
			return false;
		}

		AbstractSingleFileSelfResolvingDependency that = (AbstractSingleFileSelfResolvingDependency) object;

		if (!Objects.equals(this.project, that.project)) {
			return false;
		}
		if (!Objects.equals(this.group, that.group)) {
			return false;
		}
		if (!Objects.equals(this.name, that.name)) {
			return false;
		}
		if (!Objects.equals(this.version, that.version)) {
			return false;
		}
		return Objects.equals(this.resolved, that.resolved);
	}

	@Override
	public int hashCode() {
		int result = this.project != null ? this.project.hashCode() : 0;
		result = 31 * result + (this.group != null ? this.group.hashCode() : 0);
		result = 31 * result + (this.name != null ? this.name.hashCode() : 0);
		result = 31 * result + (this.version != null ? this.version.hashCode() : 0);
		result = 31 * result + (this.resolved != null ? this.resolved.hashCode() : 0);
		return result;
	}

	public Set<File> resolve(Iterable<Dependency> dependencies) {
		Configuration configuration = this.project.getConfigurations().detachedConfiguration();
		for (Dependency dependency : dependencies) {
			configuration.getDependencies().add(dependency);
		}
		return configuration.resolve();
	}

	protected static void hash(Hasher hasher, Iterable<File> files) {
		for (File file : files) {
			hasher.putUnencodedChars(file.getAbsolutePath());
			hasher.putLong(file.lastModified());
		}
	}

	public static String hash(Iterable<File> files) {
		Hasher hasher = Hashing.sha256().newHasher();
		hash(hasher, files);
		return hasher.hash().toString();
	}
}

package io.github.astrarre.amalgamation.gradle.plugin.base;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import groovy.lang.Closure;
import io.github.astrarre.amalgamation.gradle.dependencies.URLDependency;
import io.github.astrarre.amalgamation.gradle.ide.eclipse.ConfigureEclipse;
import io.github.astrarre.amalgamation.gradle.ide.eclipse.EclipseExtension;
import io.github.astrarre.amalgamation.gradle.ide.idea.ConfigIdeaExt;
import io.github.astrarre.amalgamation.gradle.ide.idea.IdeaExtension;
import io.github.astrarre.amalgamation.gradle.utils.AmalgIO;
import io.github.astrarre.amalgamation.gradle.utils.Lazy;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.NotNull;

public class BaseAmalgamationImpl implements BaseAmalgamation {
	public final Project project;
	public final Logger logger;

	public BaseAmalgamationImpl(Project project) {
		this.project = project;
		this.logger = project.getLogger();
	}

	@Override
	public <T> Provider<T> provideLazy(Supplier<T> action) {
		return this.project.provider(Lazy.of(action));
	}

	@Override
	public List<File> resolve(Iterable<Dependency> dependency) {
		return AmalgIO.resolve(this.project, dependency);
	}

	@Override
	public List<File> resolveWithSources(Iterable<Dependency> dependency) {
		List<File> files = AmalgIO.resolveSources(this.project, dependency)
				                   .stream()
				                   .map(Path::toFile)
				                   .toList();
		List<File> resolve = AmalgIO.resolve(this.project, dependency);
		resolve.addAll(files);
		return resolve;
	}

	private Provider<FileCollection> sources0(Dependency dependency) {
		return this.provideLazy(() -> this.project.files(
				(Object[]) AmalgIO.resolveSources(this.project, List.of(dependency)).stream().map(Path::toFile).toArray(File[]::new)
		));
	}

	@Override
	public Provider<FileCollection> sources(Object object) {
		return this.sources0(this.project.getDependencies().create(object));
	}

	@Override
	public Provider<FileCollection> sources(Object object, Closure<ModuleDependency> config) {
		return this.sources0(this.project.getDependencies().create(object, config));
	}

	@Override
	public Dependency url(String url) {
		return new URLDependency(this.project, url);
	}

	@Override
	public IdeaExtension idea() throws IllegalStateException {
		return this.getExtension("idea-ext", "org.jetbrains.gradle.plugin.idea-ext", "version '1.1'", () -> ConfigIdeaExt.extension);
	}

	@Override
	public EclipseExtension eclipse() throws IllegalStateException {
		return this.getExtension("eclipse", "eclipse", "", () -> ConfigureEclipse.extension);
	}

	@NotNull
	private <T> T getExtension(String depName, String dep, String version, Supplier<T> extension) {
		T ext = extension.get();
		if(ext == null) {
			throw new IllegalStateException(String.format(
					"%s plugin not found! \n\tplugins {\n\t\tid '%s' %s\n\t}",
					depName,
					dep,
					version
			));
		}
		return ext;
	}
}

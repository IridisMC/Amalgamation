package io.github.astrarre.merger.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.astrarre.api.classes.RawPlatformClass;

import io.github.astrarre.merger.Merger;
import io.github.astrarre.api.PlatformId;
import io.github.astrarre.api.Platformed;
import org.objectweb.asm.tree.ClassNode;

public class ClassMerger extends Merger {
	public ClassMerger(Map<String, ?> properties) {
		super(properties);
	}

	@Override
	public void merge(List<RawPlatformClass> inputs, ClassNode target, Map<String, List<String>> platformCombinations) {
		Set<PlatformId> computed = new HashSet<>();
		for (RawPlatformClass input : inputs) {
			for (Platformed<ClassNode> platformed : input.split(n -> n.invisibleAnnotations)) {
				computed.add(platformed.id);
			}
		}

		for (PlatformId ids : computed) { // todo reduce
			if(target.invisibleAnnotations == null) target.invisibleAnnotations = new ArrayList<>();
			target.invisibleAnnotations.add(ids.createAnnotation());
		}
	}
}

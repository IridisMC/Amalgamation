/*
 * Amalgamation
 * Copyright (C) 2021 Astrarre
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package net.devtech.testbytecodemerge.mergers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.devtech.testbytecodemerge.ClassInfo;
import io.github.f2bb.amalgamation.Parent;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class SuperclassMerger implements Merger {
	@Override
	public void merge(ClassNode node, List<ClassInfo> infos) {
		Map<String, List<ClassInfo>> supers = new HashMap<>();
		for (ClassInfo info : infos) {
			supers.computeIfAbsent(info.node.superName, s -> new ArrayList<>()).add(info);
		}

		// most common super class, this gets priority and is what is shown in the source
		String mostCommon = null;
		int count = 0;
		for (String s : supers.keySet()) {
			int size = supers.get(s).size();
			if (size > count) {
				mostCommon = s;
				count = size;
			}
		}

		if (mostCommon == null) {
			throw new IllegalStateException("no classes!");
		}

		node.superName = mostCommon;

		supers.remove(mostCommon);
		if (!supers.isEmpty()) {
			supers.forEach((s, i) -> {
				AnnotationVisitor n = node.visitAnnotation(Type.getDescriptor(Parent.class), true);
				AnnotationVisitor visitor = n.visitArray("platform");
				for (ClassInfo info : i) {
					visitor.visit("platform", info.createPlatformAnnotation());
				}

				n.visit("parent", Type.getObjectType(s));
			});
		}
	}
}

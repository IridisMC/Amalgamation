/*
 * Amalgamation
 * Copyright (C) 2020 IridisMC
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

package io.github.f2bb.amalgamation.javac;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.*;

public class AmalgamationJavacPlugin implements Plugin, TaskListener {

    @Override
    public String getName() {
        return "Amalgamation";
    }

    @Override
    public void init(JavacTask javacTask, String... args) {
        javacTask.addTaskListener(this);
    }

    @Override
    public void started(TaskEvent taskEvent) {
        if (taskEvent.getKind() == TaskEvent.Kind.ENTER) {
            CompilationUnitTree compilationUnit = taskEvent.getCompilationUnit();
            new AmalgamationTreeVisitor(new AmalgamationPlatformChecker()).scan(new TreePath(compilationUnit), null);
        }
    }

    @Override
    public void finished(TaskEvent taskEvent) {
    }
}
